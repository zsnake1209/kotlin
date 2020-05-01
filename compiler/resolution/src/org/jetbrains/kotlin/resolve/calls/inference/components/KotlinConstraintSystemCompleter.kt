/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.builtins.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.calls.components.transformToResolvedLambda
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
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
        val allTypeVariables: Map<TypeConstructorMarker, TypeVariableMarker>
        override val notFixedTypeVariables: Map<TypeConstructorMarker, VariableWithConstraints>
        override val postponedTypeVariables: List<TypeVariableMarker>

        fun getBuilder(): ConstraintSystemBuilder

        // type can be proper if it not contains not fixed type variables
        fun canBeProper(type: KotlinTypeMarker): Boolean

        fun containsOnlyFixedOrPostponedVariables(type: KotlinTypeMarker): Boolean

        // mutable operations
        fun addError(error: KotlinCallDiagnostic)

        fun fixVariable(variable: TypeVariableMarker, resultType: KotlinTypeMarker, atom: ResolvedAtom?)
    }

    data class PostponedArgumentParameterTypesInfo(
        val parametersFromDeclaration: List<UnwrappedType?>?,
        val parametersFromDeclarationOfRelatedLambdas: Set<List<UnwrappedType?>>?,
        val parametersFromConstraints: Set<List<TypeWithKind>>?,
        val annotations: Annotations,
        val isSuspend: Boolean,
        val isNullable: Boolean
    )

    data class TypeWithKind(
        val type: KotlinType,
        val direction: ConstraintKind = ConstraintKind.UPPER
    )

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

    // TODO: investigate type variables fixation order and incorporation depth (see `diagnostics/tests/inference/completion/postponedArgumentsAnalysis/lackOfDeepIncorporation.kt`)
    private fun Context.runCompletion(
        completionMode: ConstraintSystemCompletionMode,
        topLevelAtoms: List<ResolvedAtom>,
        topLevelType: UnwrappedType,
        diagnosticsHolder: KotlinDiagnosticsHolder,
        collectVariablesFromContext: Boolean,
        analyze: (PostponedResolvedAtom) -> Unit
    ) {
        completion@ while (true) {
            val postponedArguments = getOrderedNotAnalyzedPostponedArguments(topLevelAtoms)

            val isThereAnyReadyForFixationVariable = isThereAnyReadyForFixationVariable(
                completionMode, topLevelAtoms, topLevelType, collectVariablesFromContext, postponedArguments
            )

            // If there aren't any postponed arguments and ready for fixation variables, then completion isn't needed: nothing to do
            if (postponedArguments.isEmpty() && !isThereAnyReadyForFixationVariable)
                break

            // Stage 1: analyze postponed arguments with fixed parameter types
            if (analyzeArgumentWithFixedParameterTypes(postponedArguments, analyze))
                continue

            val postponedArgumentsWithRevisableType = postponedArguments.filterIsInstance<PostponedAtomWithRevisableExpectedType>()
            val dependencyProvider =
                TypeVariableDependencyInformationProvider(notFixedTypeVariables, postponedArguments, topLevelType, this)

            // Stage 2: collect parameter types from constraints and lambda parameters' declaration
            collectParameterTypesAndBuildNewExpectedTypes(postponedArgumentsWithRevisableType, completionMode, dependencyProvider)

            if (completionMode == ConstraintSystemCompletionMode.FULL) {
                // Stage 3: fix variables for parameter types of all postponed arguments
                for (argument in postponedArguments) {
                    val expectedType =
                        argument.run { safeAs<PostponedAtomWithRevisableExpectedType>()?.revisedExpectedType ?: expectedType }

                    if (expectedType != null && expectedType.isBuiltinFunctionalTypeOrSubtype) {
                        val wasFixedSomeVariable = fixNextReadyVariableForParameterType(
                            expectedType, postponedArguments, topLevelType, topLevelAtoms, dependencyProvider
                        )

                        if (wasFixedSomeVariable)
                            continue@completion
                    }
                }

                // Stage 4: create atoms with revised expected types if needed
                for (argument in postponedArgumentsWithRevisableType) {
                    transformToAtomWithNewFunctionalExpectedType(argument, diagnosticsHolder)
                }
            }

            /*
             * We should get not analyzed postponed arguments again because they can be changed by
             * the stage of fixation type variables for parameters or analysing postponed arguments with fixed parameter types (see stage #1 and #4)
             */
            val revisedPostponedArguments = getOrderedNotAnalyzedPostponedArguments(topLevelAtoms)

            // Stage 5: analyze the next ready postponed argument
            if (analyzeNextReadyPostponedArgument(revisedPostponedArguments, completionMode, analyze))
                continue

            // Stage 6: fix type variables – fix if possible or report not enough information (if completion mode is full)
            val wasFixedSomeVariable = fixVariablesOrReportNotEnoughInformation(
                completionMode, topLevelAtoms, topLevelType, collectVariablesFromContext, revisedPostponedArguments, diagnosticsHolder
            )
            if (wasFixedSomeVariable)
                continue

            // Stage 7: force analysis of remaining not analyzed postponed arguments and rerun stages if there are
            if (completionMode == ConstraintSystemCompletionMode.FULL) {
                if (analyzeRemainingNotAnalyzedPostponedArgument(revisedPostponedArguments, analyze))
                    continue
            }

            break
        }
    }

    private fun Context.extractParameterTypesInfo(
        argument: PostponedAtomWithRevisableExpectedType,
        postponedArguments: List<PostponedAtomWithRevisableExpectedType>,
        variableDependencyProvider: TypeVariableDependencyInformationProvider
    ): PostponedArgumentParameterTypesInfo? {
        val expectedType = argument.expectedType ?: return null
        val variableWithConstraints = notFixedTypeVariables[expectedType.constructor] ?: return null

        // We shouldn't collect functional types from constraints for anonymous functions as they have fully explicit declaration form
        val functionalTypesFromConstraints = if (!isAnonymousFunction(argument)) {
            findFunctionalTypesInConstraints(variableWithConstraints, variableDependencyProvider)
        } else null

        // Don't create functional expected type for further error reporting about a different number of arguments
        if (functionalTypesFromConstraints != null && functionalTypesFromConstraints.distinctBy { it.type.argumentsCount() }.size > 1)
            return null

        val parameterTypesFromDeclaration =
            if (argument is LambdaWithTypeVariableAsExpectedTypeAtom) argument.parameterTypesFromDeclaration else null

        val parameterTypesFromConstraints = functionalTypesFromConstraints?.map { typeWithKind ->
            typeWithKind.type.getPureArgumentsForFunctionalTypeOrSubtype().map {
                // We should use opposite kind as lambda's parameters are contravariant
                TypeWithKind(it, typeWithKind.direction.opposite())
            }
        }?.toSet()

        val annotations = functionalTypesFromConstraints?.run {
            Annotations.create(map { it.type.annotations }.flatten())
        }

        val parameterTypesFromDeclarationOfRelatedLambdas =
            getDeclaredParametersFromRelatedLambdas(argument, postponedArguments, variableDependencyProvider)

        return PostponedArgumentParameterTypesInfo(
            parameterTypesFromDeclaration,
            parameterTypesFromDeclarationOfRelatedLambdas,
            parameterTypesFromConstraints,
            annotations ?: Annotations.EMPTY,
            isSuspend = !functionalTypesFromConstraints.isNullOrEmpty() && functionalTypesFromConstraints.any { it.type.isSuspendFunctionTypeOrSubtype },
            isNullable = !functionalTypesFromConstraints.isNullOrEmpty() && functionalTypesFromConstraints.all { it.type.isMarkedNullable }
        )
    }

    private fun Context.findFunctionalTypesInConstraints(
        variable: VariableWithConstraints,
        variableDependencyProvider: TypeVariableDependencyInformationProvider
    ): List<TypeWithKind>? {
        fun List<Constraint>.extractFunctionalTypes() = mapNotNull { constraint ->
            val type = constraint.type as? KotlinType ?: return@mapNotNull null
            TypeWithKind(type.extractFunctionalTypeFromSupertypes(), constraint.kind)
        }

        val typeVariableTypeConstructor = variable.typeVariable.freshTypeConstructor() as? TypeVariableTypeConstructor ?: return null
        val dependentVariables =
            variableDependencyProvider.getShallowlyDependentVariables(typeVariableTypeConstructor).orEmpty() + typeVariableTypeConstructor

        return dependentVariables.mapNotNull { type ->
            val constraints = notFixedTypeVariables[type]?.constraints ?: return@mapNotNull null
            val constraintsWithFunctionalType = constraints.filter { (it.type as? KotlinType)?.isBuiltinFunctionalTypeOrSubtype == true }
            constraintsWithFunctionalType.extractFunctionalTypes()
        }.flatten()
    }

    private fun extractParameterTypesFromDeclaration(atom: ResolutionAtom) =
        when (atom) {
            is FunctionExpression -> {
                val receiverType = atom.receiverType
                if (receiverType != null) listOf(receiverType) + atom.parametersTypes else atom.parametersTypes.toList()
            }
            is LambdaKotlinCallArgument -> atom.parametersTypes?.toList()
            else -> null
        }

    private fun Context.createTypeVariableForParameterType(
        argument: PostponedAtomWithRevisableExpectedType,
        index: Int
    ): NewTypeVariable {
        val expectedType = argument.expectedType
            ?: throw IllegalStateException("Postponed argument's expected type must not be null")

        return when (argument) {
            is LambdaWithTypeVariableAsExpectedTypeAtom -> TypeVariableForLambdaParameterType(
                argument.atom,
                index,
                expectedType.builtIns,
                TYPE_VARIABLE_NAME_PREFIX_FOR_LAMBDA_PARAMETER_TYPE + (index + 1)
            )
            is PostponedCallableReferenceAtom -> TypeVariableForCallableReferenceParameterType(
                expectedType.builtIns,
                TYPE_VARIABLE_NAME_PREFIX_FOR_CR_PARAMETER_TYPE + (index + 1)
            )
            else -> throw IllegalStateException("Unsupported postponed argument type of $argument")
        }.apply { getBuilder().registerVariable(this) }
    }

    private fun Context.createTypeVariableForReturnType(argument: PostponedAtomWithRevisableExpectedType): NewTypeVariable {
        val expectedType = argument.expectedType
            ?: throw IllegalStateException("Postponed argument's expected type must not be null")

        return when (argument) {
            is LambdaWithTypeVariableAsExpectedTypeAtom -> TypeVariableForLambdaReturnType(
                expectedType.builtIns,
                TYPE_VARIABLE_NAME_FOR_LAMBDA_RETURN_TYPE
            )
            is PostponedCallableReferenceAtom -> TypeVariableForCallableReferenceReturnType(
                expectedType.builtIns,
                TYPE_VARIABLE_NAME_FOR_CR_RETURN_TYPE
            )
            else -> throw IllegalStateException("Unsupported postponed argument type of $argument")
        }.apply { getBuilder().registerVariable(this) }
    }

    private fun Context.createTypeVariablesForParameters(
        argument: PostponedAtomWithRevisableExpectedType,
        parameterTypes: List<List<TypeWithKind?>>
    ): List<TypeProjection> {
        val atom = argument.atom
        val csBuilder = getBuilder()
        val allGroupedParameterTypes = parameterTypes.first().indices.map { i -> parameterTypes.map { it.getOrNull(i) } }

        return allGroupedParameterTypes.mapIndexed { index, types ->
            val parameterTypeVariable = createTypeVariableForParameterType(argument, index)

            for (typeWithKind in types.filterNotNull()) {
                when (typeWithKind.direction) {
                    ConstraintKind.EQUALITY -> csBuilder.addEqualityConstraint(
                        parameterTypeVariable.defaultType, typeWithKind.type, ArgumentConstraintPosition(atom)
                    )
                    ConstraintKind.UPPER -> csBuilder.addSubtypeConstraint(
                        parameterTypeVariable.defaultType, typeWithKind.type, ArgumentConstraintPosition(atom)
                    )
                    ConstraintKind.LOWER -> csBuilder.addSubtypeConstraint(
                        typeWithKind.type, parameterTypeVariable.defaultType, ArgumentConstraintPosition(atom)
                    )
                }
            }

            parameterTypeVariable.defaultType.asTypeProjection()
        }
    }

    private fun Context.collectParameterTypesAndBuildNewExpectedTypes(
        postponedArguments: List<PostponedAtomWithRevisableExpectedType>,
        completionMode: ConstraintSystemCompletionMode,
        dependencyProvider: TypeVariableDependencyInformationProvider
    ) {
        // We can collect parameter types from declaration in any mode, they can't change during completion.
        val postponedArgumentsToCollectTypesFromDeclaredParameters = postponedArguments
            .filterIsInstance<LambdaWithTypeVariableAsExpectedTypeAtom>()
            .filter { it.parameterTypesFromDeclaration == null }

        for (argument in postponedArgumentsToCollectTypesFromDeclaredParameters) {
            argument.parameterTypesFromDeclaration = extractParameterTypesFromDeclaration(argument.atom)
        }

        /*
         * We can build new functional expected types in partial mode only for anonymous functions,
         * because more exact type can't appear from constraints in full mode (anonymous functions have fully explicit declaration).
         * It can be so for lambdas: for instance, an extension function type can appear in full mode (it may not be known in partial mode).
         *
         * TODO: investigate why we can't do it for anonymous functions in full mode always (see `diagnostics/tests/resolve/resolveWithSpecifiedFunctionLiteralWithId.kt`)
         */
        val postponedArgumentsToCollectParameterTypesAndBuildNewExpectedType =
            if (completionMode == ConstraintSystemCompletionMode.PARTIAL) {
                postponedArguments.filter(::isAnonymousFunction)
            } else {
                postponedArguments
            }

        do {
            val wasTransformedSomePostponedArgument =
                postponedArgumentsToCollectParameterTypesAndBuildNewExpectedType.filter { it.revisedExpectedType == null }.any { argument ->
                    val parameterTypesInfo = extractParameterTypesInfo(argument, postponedArguments, dependencyProvider) ?: return@any false
                    val newExpectedType = buildNewFunctionalExpectedType(argument, parameterTypesInfo) ?: return@any false

                    argument.revisedExpectedType = newExpectedType

                    true
                }
        } while (wasTransformedSomePostponedArgument)
    }

    private fun Context.getDeclaredParametersFromRelatedLambdas(
        argument: PostponedAtomWithRevisableExpectedType,
        postponedArguments: List<PostponedAtomWithRevisableExpectedType>,
        dependencyProvider: TypeVariableDependencyInformationProvider
    ): Set<List<UnwrappedType?>>? {
        fun PostponedAtomWithRevisableExpectedType.getExpectedTypeConstructor() = expectedType?.typeConstructor()

        val parameterTypesFromDeclarationOfRelatedLambdas = postponedArguments
            .filterIsInstance<LambdaWithTypeVariableAsExpectedTypeAtom>()
            .filter { it.parameterTypesFromDeclaration != null && it != argument }
            .mapNotNull { anotherArgument ->
                val argumentExpectedTypeConstructor = argument.getExpectedTypeConstructor() ?: return@mapNotNull null
                val anotherArgumentExpectedTypeConstructor = anotherArgument.getExpectedTypeConstructor() ?: return@mapNotNull null
                val areTypeVariablesRelated = dependencyProvider.areVariablesDependentShallowly(
                    argumentExpectedTypeConstructor,
                    anotherArgumentExpectedTypeConstructor
                )

                if (areTypeVariablesRelated) anotherArgument.parameterTypesFromDeclaration else null
            }

        return parameterTypesFromDeclarationOfRelatedLambdas.toSet().takeIf { it.isNotEmpty() }
    }

    private fun Context.computeResultingFunctionalConstructor(
        argument: PostponedAtomWithRevisableExpectedType,
        parametersNumber: Int,
        isSuspend: Boolean
    ): TypeConstructor {
        val expectedType = argument.expectedType
            ?: throw IllegalStateException("Postponed argument's expected type must not be null")

        val expectedTypeConstructor = expectedType.constructor

        return when (argument) {
            is LambdaWithTypeVariableAsExpectedTypeAtom ->
                getFunctionDescriptor(expectedTypeConstructor.builtIns, parametersNumber, isSuspend).typeConstructor
            is PostponedCallableReferenceAtom -> {
                val computedResultType = resultTypeResolver.findResultType(
                    this,
                    notFixedTypeVariables.getValue(expectedTypeConstructor),
                    TypeVariableDirectionCalculator.ResolveDirection.TO_SUPERTYPE
                )

                // Avoid KFunction<...>/Function<...> types
                if (computedResultType.isBuiltinFunctionalTypeOrSubtype() && computedResultType.argumentsCount() > 1) {
                    computedResultType.typeConstructor() as TypeConstructor
                } else {
                    getKFunctionDescriptor(expectedTypeConstructor.builtIns, parametersNumber, isSuspend).typeConstructor
                }
            }
            else -> throw IllegalStateException("Unsupported postponed argument type of $argument")
        }
    }

    private fun Context.buildNewFunctionalExpectedType(
        argument: PostponedAtomWithRevisableExpectedType,
        parameterTypesInfo: PostponedArgumentParameterTypesInfo
    ): UnwrappedType? {
        val expectedType = argument.expectedType

        if (expectedType == null || expectedType.constructor !in notFixedTypeVariables)
            return null

        val atom = argument.atom
        val parametersFromConstraints = parameterTypesInfo.parametersFromConstraints
        val parametersFromDeclaration = getDeclaredParametersWrtExtensionFunctionsPresence(parameterTypesInfo)
        val areAllParameterTypesSpecified = !parametersFromDeclaration.isNullOrEmpty() && parametersFromDeclaration.all { it != null }
        val isExtensionFunction = parameterTypesInfo.annotations.hasExtensionFunctionAnnotation()
        val parametersFromDeclarations = parameterTypesInfo.parametersFromDeclarationOfRelatedLambdas.orEmpty() + parametersFromDeclaration

        /*
         * We shouldn't create synthetic functional type if all lambda's parameter types are specified explicitly
         *
         * TODO: extension function with explicitly specified receiver
         * TODO: wrt anonymous functions – see info about need for analysis in partial mode in `collectParameterTypesAndBuildNewExpectedTypes`
         */
        if (areAllParameterTypesSpecified && !isExtensionFunction && !isAnonymousFunction(argument))
            return null

        val allParameterTypes =
            (parametersFromConstraints.orEmpty() + parametersFromDeclarations.map { parameters -> parameters?.map { it.wrapToTypeWithKind() } }).filterNotNull()

        if (allParameterTypes.isEmpty())
            return null

        val variablesForParameterTypes = createTypeVariablesForParameters(argument, allParameterTypes)
        val variableForReturnType = createTypeVariableForReturnType(argument)
        val functionalConstructor =
            computeResultingFunctionalConstructor(argument, variablesForParameterTypes.size, parameterTypesInfo.isSuspend)

        val isExtensionFunctionType = parameterTypesInfo.annotations.hasExtensionFunctionAnnotation()
        val areParametersNumberInDeclarationAndConstraintsEqual =
            !parametersFromDeclaration.isNullOrEmpty() && !parametersFromConstraints.isNullOrEmpty()
                    && parametersFromDeclaration.size == parametersFromConstraints.first().size

        /*
         * We need to exclude further considering a postponed argument as an extension function
         * to support cases with explicitly specified receiver as a value parameter (only if all parameter types are specified)
         *
         * Example: `val x: String.() -> Int = id { x: String -> 42 }`
         */
        val shouldDiscriminateExtensionFunctionAnnotation =
            isExtensionFunctionType && areAllParameterTypesSpecified && areParametersNumberInDeclarationAndConstraintsEqual

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
                parameterTypesInfo.annotations.withExtensionFunctionAnnotation(expectedType.builtIns)
            else -> parameterTypesInfo.annotations
        }

        val nexExpectedType = KotlinTypeFactory.simpleType(
            annotations,
            functionalConstructor,
            variablesForParameterTypes + variableForReturnType.defaultType.asTypeProjection(),
            parameterTypesInfo.isNullable
        )

        getBuilder().addSubtypeConstraint(
            nexExpectedType,
            expectedType,
            ArgumentConstraintPosition(argument.atom)
        )

        return nexExpectedType
    }

    private fun Context.transformToAtomWithNewFunctionalExpectedType(
        argument: PostponedAtomWithRevisableExpectedType,
        diagnosticsHolder: KotlinDiagnosticsHolder
    ) {
        val revisedExpectedType = argument.revisedExpectedType?.takeIf { it.isBuiltinFunctionalTypeOrSubtype } ?: return

        when (argument) {
            is PostponedCallableReferenceAtom -> {
                PostponedCallableReferenceAtom(EagerCallableReferenceAtom(argument.atom, revisedExpectedType)).also {
                    argument.setAnalyzedResults(null, listOf(it))
                }
            }
            is LambdaWithTypeVariableAsExpectedTypeAtom -> {
                val returnTypeVariableConstructor = revisedExpectedType.getReturnTypeFromFunctionType().constructor
                val returnTypeVariable =
                    notFixedTypeVariables[returnTypeVariableConstructor]?.typeVariable as? TypeVariableForLambdaReturnType ?: return

                argument.transformToResolvedLambda(getBuilder(), diagnosticsHolder, revisedExpectedType, returnTypeVariable)
            }
        }
    }

    private fun getAllDeeplyRelatedTypeVariables(
        type: KotlinType,
        variableDependencyProvider: TypeVariableDependencyInformationProvider
    ): List<TypeVariableTypeConstructor> {
        val typeConstructor = type.constructor

        return when {
            typeConstructor is TypeVariableTypeConstructor -> {
                val relatedVariables = variableDependencyProvider.getDeeplyDependentVariables(typeConstructor).orEmpty()
                listOf(typeConstructor) + relatedVariables.filterIsInstance<TypeVariableTypeConstructor>()
            }
            type.arguments.isNotEmpty() -> {
                type.arguments.map { getAllDeeplyRelatedTypeVariables(it.type, variableDependencyProvider) }.flatten()
            }
            else -> listOf()
        }
    }

    private fun Context.fixNextReadyVariableForParameterType(
        type: KotlinType,
        postponedArguments: List<PostponedResolvedAtom>,
        topLevelType: UnwrappedType,
        topLevelAtoms: List<ResolvedAtom>,
        dependencyProvider: TypeVariableDependencyInformationProvider
    ): Boolean {
        val relatedVariables = type.getPureArgumentsForFunctionalTypeOrSubtype()
            .map { getAllDeeplyRelatedTypeVariables(it, dependencyProvider) }.flatten()
        val variableForFixation = variableFixationFinder.findFirstVariableForFixation(
            this, relatedVariables, postponedArguments, ConstraintSystemCompletionMode.FULL, topLevelType
        )

        if (variableForFixation == null || !variableForFixation.hasProperConstraint)
            return false

        fixVariable(this, notFixedTypeVariables.getValue(variableForFixation.variable), topLevelAtoms)

        return true
    }

    private fun getDeclaredParametersWrtExtensionFunctionsPresence(parameterTypesInfo: PostponedArgumentParameterTypesInfo): List<UnwrappedType?>? {
        val (parametersFromDeclaration, _, parametersFromConstraints, annotations) = parameterTypesInfo

        if (parametersFromConstraints.isNullOrEmpty() || parametersFromDeclaration.isNullOrEmpty())
            return parametersFromDeclaration

        val oneLessParameterInDeclarationThanInConstraints = parametersFromConstraints.first().size == parametersFromDeclaration.size + 1

        return if (oneLessParameterInDeclarationThanInConstraints && annotations.hasExtensionFunctionAnnotation()) {
            listOf(null) + parametersFromDeclaration
        } else {
            parametersFromDeclaration
        }
    }

    private fun Context.analyzeArgumentWithFixedParameterTypes(
        postponedArguments: List<PostponedResolvedAtom>,
        analyze: (PostponedResolvedAtom) -> Unit
    ): Boolean {
        val argumentWithFixedOrPostponedInputTypes = findPostponedArgumentWithFixedOrPostponedInputTypes(postponedArguments)

        if (argumentWithFixedOrPostponedInputTypes != null) {
            analyze(argumentWithFixedOrPostponedInputTypes)
            return true
        }

        return false
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

        return analyzeArgumentWithFixedParameterTypes(postponedArguments, analyze)
    }

    private fun findPostponedArgumentWithRevisableTypeVariableExpectedType(postponedArguments: List<PostponedResolvedAtom>) =
        postponedArguments.firstOrNull { argument ->
            argument is PostponedAtomWithRevisableExpectedType && argument.expectedType?.constructor is TypeVariableTypeConstructor
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

    private fun Context.getVariableReadyForFixation(
        completionMode: ConstraintSystemCompletionMode,
        topLevelAtoms: List<ResolvedAtom>,
        topLevelType: UnwrappedType,
        collectVariablesFromContext: Boolean,
        postponedArguments: List<PostponedResolvedAtom>
    ) = variableFixationFinder.findFirstVariableForFixation(
        this,
        getOrderedAllTypeVariables(collectVariablesFromContext, topLevelAtoms),
        postponedArguments,
        completionMode,
        topLevelType
    )

    private fun Context.isThereAnyReadyForFixationVariable(
        completionMode: ConstraintSystemCompletionMode,
        topLevelAtoms: List<ResolvedAtom>,
        topLevelType: UnwrappedType,
        collectVariablesFromContext: Boolean,
        postponedArguments: List<PostponedResolvedAtom>
    ) = getVariableReadyForFixation(completionMode, topLevelAtoms, topLevelType, collectVariablesFromContext, postponedArguments) != null

    private fun Context.fixVariablesOrReportNotEnoughInformation(
        completionMode: ConstraintSystemCompletionMode,
        topLevelAtoms: List<ResolvedAtom>,
        topLevelType: UnwrappedType,
        collectVariablesFromContext: Boolean,
        postponedArguments: List<PostponedResolvedAtom>,
        diagnosticsHolder: KotlinDiagnosticsHolder
    ): Boolean {
        var wasFixedSomeVariable = false

        while (true) {
            val variableForFixation = getVariableReadyForFixation(
                completionMode,
                topLevelAtoms,
                topLevelType,
                collectVariablesFromContext,
                postponedArguments
            ) ?: break

            if (variableForFixation.hasProperConstraint || completionMode == ConstraintSystemCompletionMode.FULL) {
                val variableWithConstraints = notFixedTypeVariables.getValue(variableForFixation.variable)

                if (variableForFixation.hasProperConstraint) {
                    fixVariable(this, variableWithConstraints, topLevelAtoms)
                    wasFixedSomeVariable = true
                } else {
                    processVariableWhenNotEnoughInformation(this, variableWithConstraints, topLevelAtoms, diagnosticsHolder)
                }

                continue
            }

            break
        }

        return wasFixedSomeVariable
    }

    private fun Context.findPostponedArgumentWithFixedOrPostponedInputTypes(postponedArguments: List<PostponedResolvedAtom>) =
        postponedArguments.firstOrNull { argument ->
            argument.inputTypes.all { containsOnlyFixedOrPostponedVariables(it) }
        }

    private fun Context.getOrderedAllTypeVariables(
        collectVariablesFromContext: Boolean,
        topLevelAtoms: List<ResolvedAtom>
    ): List<TypeConstructorMarker> {
        if (collectVariablesFromContext)
            return notFixedTypeVariables.keys.toList()

        fun getVariablesFromRevisedExpectedType(revisedExpectedType: KotlinType?) =
            revisedExpectedType?.arguments?.map { it.type.constructor }?.filterIsInstance<TypeVariableTypeConstructor>()

        fun ResolvedAtom.process(to: LinkedHashSet<TypeConstructor>) {
            val typeVariables = when (this) {
                is LambdaWithTypeVariableAsExpectedTypeAtom -> getVariablesFromRevisedExpectedType(revisedExpectedType).orEmpty()
                is ResolvedCallAtom -> freshVariablesSubstitutor.freshVariables.map { it.freshTypeConstructor }
                is PostponedCallableReferenceAtom ->
                    getVariablesFromRevisedExpectedType(revisedExpectedType).orEmpty() +
                            candidate?.freshSubstitutor?.freshVariables?.map { it.freshTypeConstructor }.orEmpty()
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
                diagnosticsHolder.addDiagnostic(
                    NotEnoughInformationForLambdaParameter(typeVariable.atom, typeVariable.index)
                )
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

    private fun KotlinType?.wrapToTypeWithKind() = this?.let { TypeWithKind(it) }

    private fun isAnonymousFunction(argument: PostponedAtomWithRevisableExpectedType) = argument.atom is FunctionExpression

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

        private const val TYPE_VARIABLE_NAME_PREFIX_FOR_LAMBDA_PARAMETER_TYPE = "_RP"
        private const val TYPE_VARIABLE_NAME_FOR_LAMBDA_RETURN_TYPE = "_R"
        private const val TYPE_VARIABLE_NAME_PREFIX_FOR_CR_PARAMETER_TYPE = "_QP"
        private const val TYPE_VARIABLE_NAME_FOR_CR_RETURN_TYPE = "_Q"
    }
}
