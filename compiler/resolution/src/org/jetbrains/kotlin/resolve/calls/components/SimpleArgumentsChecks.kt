/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.components

import org.jetbrains.kotlin.descriptors.ClassifierDescriptorWithTypeParameters
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.addSubtypeConstraintIfCompatible
import org.jetbrains.kotlin.resolve.calls.inference.model.ArgumentConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintPosition
import org.jetbrains.kotlin.resolve.calls.inference.model.ReceiverConstraintPosition
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.checker.captureFromExpression
import org.jetbrains.kotlin.types.checker.hasSupertypeWithGivenTypeConstructor
import org.jetbrains.kotlin.types.lowerIfFlexible
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.kotlin.types.upperIfFlexible


fun checkSimpleArgument(
    csBuilder: ConstraintSystemBuilder,
    argument: SimpleKotlinCallArgument,
    expectedType: UnwrappedType?,
    diagnosticsHolder: KotlinDiagnosticsHolder,
    isReceiver: Boolean,
    parameterName: Name?
): ResolvedAtom = when (argument) {
    is ExpressionKotlinCallArgument ->
        checkExpressionArgument(csBuilder, argument, expectedType, diagnosticsHolder, isReceiver, parameterName)

    is SubKotlinCallArgument ->
        checkSubCallArgument(csBuilder, argument, expectedType, diagnosticsHolder, isReceiver, parameterName)

    else ->
        unexpectedArgument(argument)
}

private fun checkExpressionArgument(
    csBuilder: ConstraintSystemBuilder,
    expressionArgument: ExpressionKotlinCallArgument,
    expectedType: UnwrappedType?,
    diagnosticsHolder: KotlinDiagnosticsHolder,
    isReceiver: Boolean,
    parameterName: Name?
): ResolvedAtom {
    val resolvedKtExpression = ResolvedExpressionAtom(expressionArgument)
    if (expectedType == null) return resolvedKtExpression

    // todo run this approximation only once for call
    val argumentType = captureFromTypeParameterUpperBoundIfNeeded(expressionArgument.receiver.stableType, expectedType)

    fun unstableSmartCastOrSubtypeError(
        unstableType: UnwrappedType?, actualExpectedType: UnwrappedType, position: ConstraintPosition
    ): KotlinCallDiagnostic? {
        if (unstableType != null) {
            if (csBuilder.addSubtypeConstraintIfCompatible(unstableType, actualExpectedType, position)) {
                return UnstableSmartCast(expressionArgument, unstableType)
            }
        }
        csBuilder.addSubtypeConstraint(argumentType, actualExpectedType, position)
        return null
    }

    val expectedNullableType = expectedType.makeNullableAsSpecified(true)
    val position = if (isReceiver)
        ReceiverConstraintPosition(expressionArgument)
    else
        ArgumentConstraintPosition(expressionArgument, parameterName?.asString())
    if (expressionArgument.isSafeCall) {
        if (!csBuilder.addSubtypeConstraintIfCompatible(argumentType, expectedNullableType, position)) {
            diagnosticsHolder.addDiagnosticIfNotNull(
                unstableSmartCastOrSubtypeError(expressionArgument.receiver.unstableType, expectedNullableType, position)
            )
        }
        return resolvedKtExpression
    }

    if (!csBuilder.addSubtypeConstraintIfCompatible(argumentType, expectedType, position)) {
        if (!isReceiver) {
            diagnosticsHolder.addDiagnosticIfNotNull(
                unstableSmartCastOrSubtypeError(
                    expressionArgument.receiver.unstableType,
                    expectedType,
                    position
                )
            )
            return resolvedKtExpression
        }

        val unstableType = expressionArgument.receiver.unstableType
        if (unstableType != null && csBuilder.addSubtypeConstraintIfCompatible(unstableType, expectedType, position)) {
            diagnosticsHolder.addDiagnostic(UnstableSmartCast(expressionArgument, unstableType))
        } else if (csBuilder.addSubtypeConstraintIfCompatible(argumentType, expectedNullableType, position)) {
            diagnosticsHolder.addDiagnostic(UnsafeCallError(expressionArgument))
        } else {
            csBuilder.addSubtypeConstraint(argumentType, expectedType, position)
        }
    }

    return resolvedKtExpression
}

/**
 * interface Inv<T>
 * fun <Y> bar(l: Inv<Y>): Y = ...
 *
 * fun <X : Inv<out Int>> foo(x: X) {
 *      val xr = bar(x)
 * }
 * Here we try to capture from upper bound from type parameter.
 * We replace type of `x` to `Inv<out Int>`(we chose supertype which contains supertype with expectedTypeConstructor) and capture from this type.
 * It is correct, because it is like this code:
 * fun <X : Inv<out Int>> foo(x: X) {
 *      val inv: Inv<out Int> = x
 *      val xr = bar(inv)
 * }
 *
 */
private fun captureFromTypeParameterUpperBoundIfNeeded(argumentType: UnwrappedType, expectedType: UnwrappedType): UnwrappedType {
    val expectedTypeConstructor = expectedType.upperIfFlexible().constructor

    if (argumentType.lowerIfFlexible().constructor.declarationDescriptor is TypeParameterDescriptor) {
        val chosenSupertype = argumentType.lowerIfFlexible().supertypes().singleOrNull {
            it.constructor.declarationDescriptor is ClassifierDescriptorWithTypeParameters &&
                    it.unwrap().hasSupertypeWithGivenTypeConstructor(expectedTypeConstructor)
        }
        if (chosenSupertype != null) {
            return captureFromExpression(chosenSupertype.unwrap()) ?: argumentType
        }
    }

    return argumentType
}

private fun checkSubCallArgument(
    csBuilder: ConstraintSystemBuilder,
    subCallArgument: SubKotlinCallArgument,
    expectedType: UnwrappedType?,
    diagnosticsHolder: KotlinDiagnosticsHolder,
    isReceiver: Boolean,
    parameterName: Name?
): ResolvedAtom {
    val subCallResult = subCallArgument.callResult

    if (expectedType == null) return subCallResult

    val expectedNullableType = expectedType.makeNullableAsSpecified(true)
    val position = if (isReceiver)
        ReceiverConstraintPosition(subCallArgument)
    else
        ArgumentConstraintPosition(subCallArgument, parameterName?.asString())

    // subArgument cannot has stable smartcast
    // return type can contains fixed type variables
    val currentReturnType = csBuilder.buildCurrentSubstitutor().safeSubstitute(subCallArgument.receiver.receiverValue.type.unwrap())
    if (subCallArgument.isSafeCall) {
        csBuilder.addSubtypeConstraint(currentReturnType, expectedNullableType, position)
        return subCallResult
    }

    if (isReceiver && !csBuilder.addSubtypeConstraintIfCompatible(currentReturnType, expectedType, position) &&
        csBuilder.addSubtypeConstraintIfCompatible(currentReturnType, expectedNullableType, position)
    ) {
        diagnosticsHolder.addDiagnostic(UnsafeCallError(subCallArgument))
        return subCallResult
    }

    csBuilder.addSubtypeConstraint(currentReturnType, expectedType, position)
    return subCallResult
}

