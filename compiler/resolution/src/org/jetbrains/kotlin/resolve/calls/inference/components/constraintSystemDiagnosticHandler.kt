/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.typeUtil.contains
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

fun handleErrorFromConstraintSystem(
    lowerType: UnwrappedType,
    upperType: UnwrappedType,
    typeVariable: NewTypeVariable? = null,
    position: IncorporationConstraintPosition,
    c: ConstraintInjector.Context
): ConstraintSystemCallDiagnostic {
    return contradictoryTypeVariableError(position.from, typeVariable, c) ?: NewConstraintError(lowerType, upperType, position)
}

fun contradictoryTypeVariableError(
    constraintPosition: ConstraintPosition,
    typeVariable: NewTypeVariable?,
    c: ConstraintInjector.Context
): ContradictoryTypeVariableError? {
    if (typeVariable == null) return null

    // for this case we separately report upper bound violated error
    if (constraintPosition is ExplicitTypeParameterConstraintPosition) return null
    if (constraintPosition is ReceiverConstraintPosition) return null

    val variableWithConstraints = c.notFixedTypeVariables[typeVariable.freshTypeConstructor] ?: return null
    val properConstraints = variableWithConstraints.constraints.filter { c.canBeProper(it.type) }
    if (properConstraints.isEmpty()) return null

    // Probably we shouldn't report anything at all, but now we'll report old type mismatch error to be conservative
    if (presentConstraintDiagnosticOn(constraintPosition, c)) return null

    return ContradictoryTypeVariableError(
        constraintPosition, typeVariable, extractKind(typeVariable), divideByConstraints(properConstraints)
    )
}

private fun presentConstraintDiagnosticOn(position: ConstraintPosition, c: ConstraintInjector.Context): Boolean {
    if (c !is NewConstraintSystem) return false

    return c.diagnostics.any { diagnostic ->
        val error = diagnostic.safeAs<ContradictoryTypeVariableError>()
        error?.constraintPosition == position
    }
}

private fun ConstraintInjector.Context.canBeProper(type: UnwrappedType): Boolean {
    return !type.contains { notFixedTypeVariables.containsKey(it.constructor) }
}

data class SortedConstraints(val upper: List<Constraint>, val equality: List<Constraint>, val lower: List<Constraint>)

private fun extractKind(typeVariable: NewTypeVariable): SpecialTypeVariableKind? {
    val freshTypeConstructor = typeVariable.freshTypeConstructor as? TypeVariableTypeConstructor ?: return null

    val name = freshTypeConstructor.debugName // todo: improve this code
    return SPECIAL_TYPE_PARAMETER_NAME_TO_KIND[name]
}

enum class SpecialTypeVariableKind(val expressionName: String) {
    IF("if"), ELVIS("elvis"), WHEN("when")
}

// TODO: Get names of type parameters from ControlStructureTypingUtils.ResolveConstruct
private val SPECIAL_TYPE_PARAMETER_NAME_TO_KIND = mapOf(
    "<TYPE-PARAMETER-FOR-IF-RESOLVE>" to SpecialTypeVariableKind.IF,
    "<TYPE-PARAMETER-FOR-WHEN-RESOLVE>" to SpecialTypeVariableKind.WHEN,
    "<TYPE-PARAMETER-FOR-ELVIS-RESOLVE>" to SpecialTypeVariableKind.ELVIS
)

private fun divideByConstraints(constraints: List<Constraint>): SortedConstraints {
    return with(constraints) {
        SortedConstraints(getWith(ConstraintKind.UPPER), getWith(ConstraintKind.EQUALITY), getWith(ConstraintKind.LOWER))
    }
}

private fun List<Constraint>.getWith(kind: ConstraintKind) = filter { it.kind == kind }