/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.resolve.calls.inference.model.Constraint
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind
import org.jetbrains.kotlin.types.model.KotlinTypeMarker
import org.jetbrains.kotlin.types.model.TypeSystemInferenceExtensionContext

class TrivialConstraintTypeInferenceOracle {
    // The idea is to add knowledge that constraint `Nothing(?) <: T` is quite useless and
    // it's totally fine to go and resolve postponed argument without fixation T to Nothing(?).
    // In other words, constraint `Nothing(?) <: T` is *not* proper
    fun isTrivialConstraint(
        context: TypeSystemInferenceExtensionContext,
        constraint: Constraint
    ): Boolean = with(context) {
        // TODO: probably we also can take into account `T <: Any(?)` constraints
        return constraint.kind == ConstraintKind.LOWER && constraint.type.typeConstructor().isNothingConstructor()
    }

    // This function controls the choice between sub and super result type
    // Even that Nothing(?) is the most specific type for subtype, it doesn't bring valuable information to the user,
    // therefore it is discriminated in favor of supertype
    fun isSuitableResultedType(
        context: TypeSystemInferenceExtensionContext,
        resultType: KotlinTypeMarker
    ): Boolean = with(context) {
        return !resultType.typeConstructor().isNothingConstructor()
    }

    // It's possible to generate Nothing-like constraints inside incorporation mechanism:
    // For instance, when two type variables are in subtyping relation `T <: K`, after incorporation
    // there will be constraint `approximation(out K) <: K` => `Nothing <: K`, which is innocent
    // but can change result of the constraint system.
    // Therefore, here we avoid adding such trivial constraints to have stable constraint system
    fun isGeneratedConstraintTrivial(
        context: TypeSystemInferenceExtensionContext,
        otherConstraint: Constraint,
        generatedConstraintType: KotlinTypeMarker
    ): Boolean = with(context) {
        if (generatedConstraintType.isNothing()) return true

        // If type that will be used to generate new constraint already contains `Nothing(?)`,
        // then we can't decide that resulting constraint will be useless
        if (otherConstraint.type.contains { it.isNothingOrNullableNothing(context) }) return false

        // It's important to preserve constraints with nullable Nothing: `Nothing? <: T` (see implicitNothingConstraintFromReturn.kt test)
        if (generatedConstraintType.containsOnlyNonNullableNothing(context)) return true

        return false
    }


    private fun KotlinTypeMarker.isNothingOrNullableNothing(context: TypeSystemInferenceExtensionContext): Boolean =
        with(context) {
            typeConstructor().isNothingConstructor()
        }

    private fun KotlinTypeMarker.containsOnlyNonNullableNothing(context: TypeSystemInferenceExtensionContext): Boolean =
        with(context) { contains { it.isNothing() } && !contains { it.isNullableNothing() } }

}
