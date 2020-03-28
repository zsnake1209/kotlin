/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.calls.components.transformToResolvedLambda
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.model.*
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeConstructorMarker
import org.jetbrains.kotlin.types.model.TypeVariableMarker
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.builtIns
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import kotlin.collections.LinkedHashSet

class KotlinConstraintSystemCompleter(
    private val resultTypeResolver: ResultTypeResolver,
    val variableFixationFinder: VariableFixationFinder,
) {
    enum class ConstraintSystemCompletionMode {
        FULL,
        PARTIAL
    }

    interface Context : VariableFixationFinder.Context, ResultTypeResolver.Context {
        override val notFixedTypeVariables: Map<TypeConstructorMarker, VariableWithConstraints>

        override val postponedTypeVariables: List<TypeVariableMarker>

        // type can be proper if it not contains not fixed type variables
        fun canBeProper(type: KotlinTypeMarker): Boolean

        fun containsOnlyFixedOrPostponedVariables(type: KotlinTypeMarker): Boolean

        // mutable operations
        fun addError(error: KotlinCallDiagnostic)

        fun fixVariable(variable: TypeVariableMarker, resultType: KotlinTypeMarker, atom: ResolvedAtom?)
    }

    fun runCompletion(
        c: Context,
        completionMode: ConstraintSystemCompletionMode,
        topLevelAtoms: List<ResolvedAtom>,
        topLevelType: UnwrappedType,
        diagnosticsHolder: KotlinDiagnosticsHolder,
        analyze: (PostponedResolvedAtom) -> Unit
    ) {
        c.runCompletion(
            completionMode,
            topLevelAtoms,
            topLevelType,
            diagnosticsHolder,
            collectVariablesFromContext = false,
            analyze = analyze
        )
    }

    fun completeConstraintSystem(
        c: Context,
        topLevelType: UnwrappedType,
        topLevelAtoms: List<ResolvedAtom>,
        diagnosticsHolder: KotlinDiagnosticsHolder
    ) {
        c.runCompletion(
            ConstraintSystemCompletionMode.FULL,
            topLevelAtoms,
            topLevelType,
            diagnosticsHolder,
            collectVariablesFromContext = true,
        ) {
            error("Shouldn't be called in complete constraint system mode")
        }
    }

    private fun Context.fixVariablesInsideType(type: KotlinType, topLevelAtoms: List<ResolvedAtom>) {
        val typeConstructor = type.constructor

        if (typeConstructor is TypeVariableTypeConstructor && variableFixationFinder.isTypeVariableHasProperConstraint(this, typeConstructor)) {
            val variableWithConstraints = notFixedTypeVariables.getValue(typeConstructor)
            if (variableWithConstraints.typeVariable !in postponedTypeVariables) {
                fixVariable(this, variableWithConstraints, topLevelAtoms)
            }
        } else if (type.arguments.isNotEmpty()) {
            for (argument in type.arguments) {
                fixVariablesInsideType(argument.type, topLevelAtoms)
            }
        }
    }

    private fun Context.fixVariablesForParameterTypes(type: KotlinType, topLevelAtoms: List<ResolvedAtom>) {
        for (parameter in type.arguments.dropLast(1)) {
            fixVariablesInsideType(parameter.type, topLevelAtoms)
        }
    }

    private fun extractParameterTypesFromDeclaration(atom: ResolutionAtom): List<KotlinType?>? {
        return if (atom is FunctionExpression && atom.receiverType != null) {
            listOf(atom.receiverType) + atom.parametersTypes.map { it }
        } else if (atom is LambdaKotlinCallArgument) atom.parametersTypes?.map { it }
        else null
    }

    private fun Context.extractParameterTypesInfoFromConstraints(
        expectedTypeVariable: TypeConstructor
    ): ExtractedParameterTypesInfo {
        if (expectedTypeVariable !in notFixedTypeVariables)
            return ExtractedParameterTypesInfo.EMPTY

        val foundFunctionalTypes = findFunctionalTypesInConstraints(notFixedTypeVariables.getValue(expectedTypeVariable))
            ?: return ExtractedParameterTypesInfo.EMPTY

        return ExtractedParameterTypesInfo(
            null,
            foundFunctionalTypes.map { it.arguments.dropLast(1).map { it.type } }.toSet(),
            Annotations.create(foundFunctionalTypes.map { it.annotations }.flatten()),
            foundFunctionalTypes.isNotEmpty() && foundFunctionalTypes.all { it.isSuspendFunctionTypeOrSubtype },
            foundFunctionalTypes.isNotEmpty() && foundFunctionalTypes.all { it.isMarkedNullable }
        )
    }

    private fun Context.transformToAtomWithNewFunctionalExpectedType(
        argument: PostponedAtomWithRevisableExpectedType,
        diagnosticsHolder: KotlinDiagnosticsHolder
    ) {
        if (this !is NewConstraintSystem) return

        val revisedExpectedType = argument.revisedExpectedType ?: return

        when (argument) {
            is PostponedCallableReferenceAtom -> {
                PostponedCallableReferenceAtom(EagerCallableReferenceAtom(argument.atom, revisedExpectedType)).also {
                    argument.setAnalyzedResults(null, listOf(it))
                }
            }
            is LambdaWithTypeVariableAsExpectedTypeAtom -> {
                val returnTypeVariableConstructor = revisedExpectedType.arguments.last().type.constructor
                val returnTypeVariable =
                    notFixedTypeVariables.getValue(returnTypeVariableConstructor).typeVariable as? TypeVariableForLambdaReturnType ?: return

                argument.transformToResolvedLambda(getBuilder(), diagnosticsHolder, revisedExpectedType, returnTypeVariable)
            }
        }
    }

    data class ExtractedParameterTypesInfo(
        val parametersFromDeclaration: List<KotlinType?>?,
        val parametersFromConstraints: Set<List<KotlinType>>?,
        val annotations: Annotations,
        val isSuspend: Boolean,
        val isNullable: Boolean
    ) {
        companion object {
            val EMPTY = ExtractedParameterTypesInfo(null, null, Annotations.EMPTY, false, false)
        }
    }

    private fun Context.collectParameterTypes(argument: PostponedAtomWithRevisableExpectedType): ExtractedParameterTypesInfo? {
        val expectedType = argument.expectedType ?: return null
        val atom = argument.atom

        val parameterTypesInfoFromConstraints = extractParameterTypesInfoFromConstraints(expectedType.constructor)
        val parameterTypesFromDeclaration = extractParameterTypesFromDeclaration(atom)

        if (parameterTypesInfoFromConstraints.parametersFromConstraints.isNullOrEmpty() && parameterTypesFromDeclaration.isNullOrEmpty())
            return null

        return parameterTypesInfoFromConstraints.copy(parametersFromDeclaration = parameterTypesFromDeclaration)
    }

    private fun NewConstraintSystem.buildNewFunctionalExpectedType(
        argument: PostponedAtomWithRevisableExpectedType,
        parameterTypesInfo: ExtractedParameterTypesInfo
    ): UnwrappedType? {
        val expectedTypeVariable = argument.expectedType ?: return null
        val atom = argument.atom
        val parametersFromDeclaration = parameterTypesInfo.parametersFromDeclaration
        val allParameterTypes = (parameterTypesInfo.parametersFromConstraints.orEmpty() + parametersFromDeclaration).filterNotNull()

        if (allParameterTypes.isEmpty())
            return null

        val allGroupedParameterTypes = allParameterTypes.first().indices.map { i -> allParameterTypes.map { it.getOrNull(i) } }
        val csBuilder = getBuilder()

        fun createTypeVariableForParameterType(index: Int) =
            when (argument) {
                is LambdaWithTypeVariableAsExpectedTypeAtom ->
                    TypeVariableForLambdaParameterType(atom, index, expectedTypeVariable.builtIns, "_RP")
                is PostponedCallableReferenceAtom ->
                    TypeVariableForCallableReferenceParameterType(expectedTypeVariable.builtIns, "_QP")
                else -> null
            }?.apply { csBuilder.registerVariable(this) }

        fun createTypeVariableForReturnType() =
            when (argument) {
                is LambdaWithTypeVariableAsExpectedTypeAtom -> TypeVariableForLambdaReturnType(expectedTypeVariable.builtIns, "_R")
                is PostponedCallableReferenceAtom -> TypeVariableForCallableReferenceReturnType(expectedTypeVariable.builtIns, "_Q")
                else -> null
            }?.apply { csBuilder.registerVariable(this) }

        val returnValueVariable = createTypeVariableForReturnType() ?: return null

        val variablesForParameterTypes = allGroupedParameterTypes.mapIndexedNotNull { index, types ->
            val parameterTypeVariable = createTypeVariableForParameterType(index) ?: return@mapIndexedNotNull null

            for (type in types.filterNotNull()) {
                csBuilder.addSubtypeConstraint(parameterTypeVariable.defaultType, type, ArgumentConstraintPosition(atom))
            }

            parameterTypeVariable.defaultType.asTypeProjection()
        }

        val functionDescriptor = when (argument) {
            is LambdaWithTypeVariableAsExpectedTypeAtom ->
                getFunctionDescriptor(expectedTypeVariable.builtIns, variablesForParameterTypes.size, parameterTypesInfo.isSuspend)
            is PostponedCallableReferenceAtom ->
                getKFunctionDescriptor(expectedTypeVariable.builtIns, variablesForParameterTypes.size, parameterTypesInfo.isSuspend)
            else -> null
        } ?: return null

        val isExtensionFunctionType = parameterTypesInfo.annotations.hasAnnotation(KotlinBuiltIns.FQ_NAMES.extensionFunctionType)
        val areAllParameterTypesFromDeclarationSpecified =
            !parametersFromDeclaration.isNullOrEmpty() && parametersFromDeclaration.all { it != null }
        /*
         * We need to exclude further considering a postponed argument as an extension function
         * to support cases with explicitly specified receiver as a value parameter (only if all parameter types are specified)
         *
         * Example: `val x: String.() -> Int = id { x: String -> 42 }`
         */
        val shouldDiscriminateExtensionFunctionAnnotation = isExtensionFunctionType && areAllParameterTypesFromDeclarationSpecified

        /*
         * We need to add an extension function annotation for anonymous functions with an explicitly specified receiver
         *
         * Example: `val x = id(fun String.() = this)`
         */
        val shouldAddExtensionFunctionAnnotation = atom is FunctionExpression && atom.receiverType != null

        val annotations = when {
            shouldDiscriminateExtensionFunctionAnnotation ->
                parameterTypesInfo.annotations.withoutExtensionFunctionAnnotation()
            shouldAddExtensionFunctionAnnotation ->
                parameterTypesInfo.annotations.withExtensionFunctionAnnotation(expectedTypeVariable.builtIns)
            else -> parameterTypesInfo.annotations
        }

        val nexExpectedType = KotlinTypeFactory.simpleType(
            annotations,
            functionDescriptor.typeConstructor,
            variablesForParameterTypes + returnValueVariable.defaultType.asTypeProjection(),
            parameterTypesInfo.isNullable
        )

        csBuilder.addSubtypeConstraint(
            nexExpectedType,
            expectedTypeVariable,
            ArgumentConstraintPosition(argument.atom)
        )

        return nexExpectedType
    }

    private fun Context.collectParameterTypesAndBuildNewExpectedTypes(postponedArguments: List<PostponedAtomWithRevisableExpectedType>) {
        if (this !is NewConstraintSystem) return

        do {
            val wasTransformedSomePostponedArgument =
                postponedArguments.filter { it.revisedExpectedType == null }.any { argument ->
                    val parameterTypesInfo = collectParameterTypes(argument) ?: return@any false
                    val newExpectedType = buildNewFunctionalExpectedType(argument, parameterTypesInfo) ?: return@any false

                    argument.revisedExpectedType = newExpectedType

                    true
                }
        } while (wasTransformedSomePostponedArgument)
    }

    private fun Context.runCompletion(
        completionMode: ConstraintSystemCompletionMode,
        topLevelAtoms: List<ResolvedAtom>,
        topLevelType: UnwrappedType,
        diagnosticsHolder: KotlinDiagnosticsHolder,
        collectVariablesFromContext: Boolean,
        analyze: (PostponedResolvedAtom) -> Unit
    ) {
        while (true) {
            val postponedArguments = getOrderedNotAnalyzedPostponedArguments(topLevelAtoms)

            val postponedArgumentsWithRevisableTypeVariableExpectedType = postponedArguments
                .filter { it.expectedType?.constructor is TypeVariableTypeConstructor }
                .filterIsInstance<PostponedAtomWithRevisableExpectedType>()

            // Stage 1: collect parameter types from constraints and lambda parameters' declaration
            collectParameterTypesAndBuildNewExpectedTypes(postponedArgumentsWithRevisableTypeVariableExpectedType)

            if (completionMode == ConstraintSystemCompletionMode.FULL) {
                // Stage 2: fix variables for parameter types
                for (argument in postponedArguments) {
                    val expectedType =
                        argument.run { safeAs<PostponedAtomWithRevisableExpectedType>()?.revisedExpectedType ?: expectedType }

                    if (expectedType != null && expectedType.isBuiltinFunctionalTypeOrSubtype) {
                        fixVariablesForParameterTypes(expectedType, postponedArguments)
                    }
                }

                // Stage 3: create atoms with revised expected types if needed
                for (argument in postponedArgumentsWithRevisableTypeVariableExpectedType) {
                    transformToAtomWithNewFunctionalExpectedType(argument, diagnosticsHolder)
                }
            }

            /*
             * We should get not analyzed postponed arguments again because they can be changed by the stage of fixation type variables for parameters,
             * namely, postponed arguments with type variable as expected type can be replaced with resolved postponed arguments with functional expected type.
             *
             * See `transformToAtomWithNewFunctionalExpectedType`
             */
            val revisedPostponedArguments = getOrderedNotAnalyzedPostponedArguments(topLevelAtoms)

            // Stage 4: analyze the first ready postponed argument and rerun stages if it has been analyzed
            if (analyzeNextReadyPostponedArgument(revisedPostponedArguments, completionMode, analyze))
                continue

            // Stage 5: force fixation remaining type variables: fix if possible or report not enough information
            fixRemainingVariablesOrReportNotEnoughInformation(
                completionMode,
                topLevelAtoms,
                topLevelType,
                collectVariablesFromContext,
                revisedPostponedArguments,
                diagnosticsHolder
            )

            // Stage 6: force analysis remaining not analyzed postponed arguments and rerun stages if there are
            if (completionMode == ConstraintSystemCompletionMode.FULL) {
                if (analyzeRemainingNotAnalyzedPostponedArgument(revisedPostponedArguments, analyze)) {
                    continue
                }
            }

            break
        }
    }

    private fun analyzeRemainingNotAnalyzedPostponedArgument(
        postponedArguments: List<PostponedResolvedAtom>,
        analyze: (PostponedResolvedAtom) -> Unit
    ): Boolean {
        val remainingNotAnalyzedPostponedArgument = postponedArguments.firstOrNull { !it.analyzed }

        if (remainingNotAnalyzedPostponedArgument != null) {
            analyze(remainingNotAnalyzedPostponedArgument)
            return true
        }

        return false
    }

    private fun Context.fixRemainingVariablesOrReportNotEnoughInformation(
        completionMode: ConstraintSystemCompletionMode,
        topLevelAtoms: List<ResolvedAtom>,
        topLevelType: UnwrappedType,
        collectVariablesFromContext: Boolean,
        postponedArguments: List<PostponedResolvedAtom>,
        diagnosticsHolder: KotlinDiagnosticsHolder
    ) {
        while (true) {
            val allTypeVariables = getOrderedAllTypeVariables(collectVariablesFromContext, topLevelAtoms)
            val variableForFixation = variableFixationFinder.findFirstVariableForFixation(
                this, allTypeVariables, postponedArguments, completionMode, topLevelType
            ) ?: break

            if (variableForFixation.hasProperConstraint || completionMode == ConstraintSystemCompletionMode.FULL) {
                val variableWithConstraints = notFixedTypeVariables.getValue(variableForFixation.variable)

                if (variableForFixation.hasProperConstraint) {
                    fixVariable(this, variableWithConstraints, topLevelAtoms)
                } else {
                    processVariableWhenNotEnoughInformation(this, variableWithConstraints, topLevelAtoms, diagnosticsHolder)
                }

                continue
            }

            break
        }
    }

    private fun Context.analyzeNextReadyPostponedArgument(
        postponedArguments: List<PostponedResolvedAtom>,
        completionMode: ConstraintSystemCompletionMode,
        analyze: (PostponedResolvedAtom) -> Unit
    ): Boolean {
        if (completionMode == ConstraintSystemCompletionMode.FULL) {
            val argumentWithTypeVariableAsExpectedType = findPostponedArgumentWithRevisableTypeVariableExpectedType(postponedArguments)

            if (argumentWithTypeVariableAsExpectedType != null) {
                analyze(argumentWithTypeVariableAsExpectedType)
                return true
            }
        }

        val argumentWithFixedOrPostponedInputTypes = findPostponedArgumentWithFixedOrPostponedInputTypes(postponedArguments)

        if (argumentWithFixedOrPostponedInputTypes != null) {
            analyze(argumentWithFixedOrPostponedInputTypes)
            return true
        }

        return false
    }

    private fun Context.findPostponedArgumentWithFixedOrPostponedInputTypes(postponedArguments: List<PostponedResolvedAtom>) =
        postponedArguments.firstOrNull { argument ->
            argument.inputTypes.all { containsOnlyFixedOrPostponedVariables(it) }
        }

    private fun findPostponedArgumentWithRevisableTypeVariableExpectedType(postponedArguments: List<PostponedResolvedAtom>) =
        postponedArguments.firstOrNull { argument ->
            argument is PostponedAtomWithRevisableExpectedType && argument.expectedType?.constructor is TypeVariableTypeConstructor
        }

    private fun Context.findFunctionalTypesInConstraints(
        variable: VariableWithConstraints,
        typeVariablesVisited: Set<TypeVariableTypeConstructor> = setOf()
    ): List<KotlinType>? {
        val typeVariableTypeConstructor = variable.typeVariable.freshTypeConstructor() as? TypeVariableTypeConstructor ?: return null
        if (typeVariableTypeConstructor in typeVariablesVisited) return null

        return variable.constraints.mapNotNull { constraint ->
            val type = constraint.type as? KotlinType ?: return@mapNotNull null

            when {
                type.isBuiltinFunctionalTypeOrSubtype -> listOf(type)
                type.constructor in notFixedTypeVariables -> {
                    findFunctionalTypesInConstraints(
                        notFixedTypeVariables.getValue(constraint.type.constructor),
                        typeVariablesVisited + typeVariableTypeConstructor
                    )
                }
                else -> null
            }
        }.flatten()
    }

    private fun Context.getOrderedAllTypeVariables(
        collectVariablesFromContext: Boolean,
        topLevelAtoms: List<ResolvedAtom>
    ): List<TypeConstructorMarker> {
        if (collectVariablesFromContext) return notFixedTypeVariables.keys.toList()

        fun ResolvedAtom.process(to: LinkedHashSet<TypeConstructor>) {
            val typeVariables = when (this) {
                is LambdaWithTypeVariableAsExpectedTypeAtom ->
                    revisedExpectedType?.arguments?.map { it.type.constructor }?.filterIsInstance<TypeVariableTypeConstructor>().orEmpty()
                is ResolvedCallAtom -> freshVariablesSubstitutor.freshVariables.map { it.freshTypeConstructor }
                is PostponedCallableReferenceAtom ->
                    revisedExpectedType?.arguments?.map { it.type.constructor }?.filterIsInstance<TypeVariableTypeConstructor>().orEmpty() + candidate?.freshSubstitutor?.freshVariables?.map { it.freshTypeConstructor }.orEmpty()
                is ResolvedCallableReferenceAtom -> candidate?.freshSubstitutor?.freshVariables?.map { it.freshTypeConstructor }.orEmpty()
                is ResolvedLambdaAtom -> listOfNotNull(typeVariableForLambdaReturnType?.freshTypeConstructor)
                else -> emptyList()
            }

            typeVariables.mapNotNullTo(to) {
                it.takeIf { notFixedTypeVariables.containsKey(it) }
            }

            /*
             * Hack for completing error candidates in delegate resolve
             */
            if (this is StubResolvedAtom && typeVariable in notFixedTypeVariables) {
                to += typeVariable
            }

            if (analyzed) {
                subResolvedAtoms?.forEach { it.process(to) }
            }
        }

        // Note that it's important to use Set here, because several atoms can share the same type variable
        val result = linkedSetOf<TypeConstructor>()
        for (primitive in topLevelAtoms) {
            primitive.process(result)
        }

        assert(result.size == notFixedTypeVariables.size) {
            val notFoundTypeVariables = notFixedTypeVariables.keys.toMutableSet().apply { removeAll(result) }
            "Not all type variables found: $notFoundTypeVariables"
        }

        return result.toList()
    }

    private fun fixVariable(
        c: Context,
        variableWithConstraints: VariableWithConstraints,
        topLevelAtoms: List<ResolvedAtom>
    ) {
        fixVariable(c, variableWithConstraints, TypeVariableDirectionCalculator.ResolveDirection.UNKNOWN, topLevelAtoms)
    }

    private fun fixVariable(
        c: Context,
        variableWithConstraints: VariableWithConstraints,
        direction: TypeVariableDirectionCalculator.ResolveDirection,
        topLevelAtoms: List<ResolvedAtom>
    ) {
        val resultType = resultTypeResolver.findResultType(c, variableWithConstraints, direction)
        val resolvedAtom = findResolvedAtomBy(variableWithConstraints.typeVariable, topLevelAtoms) ?: topLevelAtoms.firstOrNull()
        c.fixVariable(variableWithConstraints.typeVariable, resultType, resolvedAtom)
    }

    private fun processVariableWhenNotEnoughInformation(
        c: Context,
        variableWithConstraints: VariableWithConstraints,
        topLevelAtoms: List<ResolvedAtom>,
        diagnosticsHolder: KotlinDiagnosticsHolder
    ) {
        val typeVariable = variableWithConstraints.typeVariable

        val resolvedAtom = findResolvedAtomBy(typeVariable, topLevelAtoms) ?: topLevelAtoms.firstOrNull()
        if (resolvedAtom != null) {
            c.addError(NotEnoughInformationForTypeParameter(typeVariable, resolvedAtom))
        }

        val resultErrorType = when {
            typeVariable is TypeVariableFromCallableDescriptor ->
                ErrorUtils.createUninferredParameterType(typeVariable.originalTypeParameter)
            typeVariable is TypeVariableForLambdaParameterType && typeVariable.atom is LambdaKotlinCallArgument -> {
                diagnosticsHolder.addDiagnostic(NotEnoughInformationForLambdaParameter(typeVariable.atom, typeVariable.index))
                ErrorUtils.createErrorType("Cannot infer lambda parameter type")
            }
            else -> ErrorUtils.createErrorType("Cannot infer type variable $typeVariable")
        }

        c.fixVariable(typeVariable, resultErrorType, resolvedAtom)
    }

    private fun findResolvedAtomBy(typeVariable: TypeVariableMarker, topLevelAtoms: List<ResolvedAtom>): ResolvedAtom? {
        fun ResolvedAtom.check(): ResolvedAtom? {
            val suitableCall = when (this) {
                is ResolvedCallAtom -> typeVariable in freshVariablesSubstitutor.freshVariables
                is ResolvedCallableReferenceAtom -> candidate?.freshSubstitutor?.freshVariables?.let { typeVariable in it } ?: false
                is ResolvedLambdaAtom -> typeVariable == typeVariableForLambdaReturnType
                else -> false
            }

            if (suitableCall) {
                return this
            }

            subResolvedAtoms?.forEach { subResolvedAtom ->
                subResolvedAtom.check()?.let { result -> return@check result }
            }

            return null
        }

        for (topLevelAtom in topLevelAtoms) {
            topLevelAtom.check()?.let { return it }
        }

        return null
    }

    companion object {
        fun getOrderedNotAnalyzedPostponedArguments(topLevelAtoms: List<ResolvedAtom>): List<PostponedResolvedAtom> {
            fun ResolvedAtom.process(to: MutableList<PostponedResolvedAtom>) {
                to.addIfNotNull(this.safeAs<PostponedResolvedAtom>()?.takeUnless { it.analyzed })

                if (analyzed) {
                    subResolvedAtoms?.forEach { it.process(to) }
                }
            }

            val notAnalyzedArguments = arrayListOf<PostponedResolvedAtom>()
            for (primitive in topLevelAtoms) {
                primitive.process(notAnalyzedArguments)
            }

            return notAnalyzedArguments
        }
    }
}