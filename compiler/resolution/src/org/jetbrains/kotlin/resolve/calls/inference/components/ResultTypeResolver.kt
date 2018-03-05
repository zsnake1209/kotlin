/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.resolve.calls.NewCommonSuperTypeCalculator
import org.jetbrains.kotlin.resolve.calls.inference.components.TypeVariableDirectionCalculator.ResolveDirection
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind
import org.jetbrains.kotlin.resolve.calls.inference.model.VariableWithConstraints
import org.jetbrains.kotlin.resolve.calls.inference.model.checkConstraint
import org.jetbrains.kotlin.types.TypeApproximator
import org.jetbrains.kotlin.types.TypeApproximatorConfiguration
import org.jetbrains.kotlin.types.UnwrappedType
import org.jetbrains.kotlin.types.checker.intersectTypes
import org.jetbrains.kotlin.types.singleBestRepresentative
import org.jetbrains.kotlin.types.typeUtil.foldNumberTypes

class ResultTypeResolver(
    val typeApproximator: TypeApproximator
) {
    interface Context {
        fun isProperType(type: UnwrappedType): Boolean
    }

    fun findResultType(c: Context, variableWithConstraints: VariableWithConstraints, direction: ResolveDirection): UnwrappedType {
        findResultTypeOrNull(c, variableWithConstraints, direction)?.let { return it }

        // no proper constraints
        return variableWithConstraints.typeVariable.freshTypeConstructor.builtIns.run {
            if (direction == ResolveDirection.TO_SUBTYPE) nothingType else nullableAnyType
        }
    }

    fun findResultTypeOrNull(c: Context, variableWithConstraints: VariableWithConstraints, direction: ResolveDirection): UnwrappedType? {
        findResultIfThereIsEqualsConstraint(c, variableWithConstraints, allowedFixToNotProperType = false)?.let { return it }

        val subType = findSubType(c, variableWithConstraints)
        val superType = findSuperType(c, variableWithConstraints)
        val result = if (direction == ResolveDirection.TO_SUBTYPE || direction == ResolveDirection.UNKNOWN) {
            c.resultType(subType, superType, variableWithConstraints)
        } else {
            c.resultType(superType, subType, variableWithConstraints)
        }

        return result
    }

    private fun Context.resultType(
        firstCandidate: UnwrappedType?,
        secondCandidate: UnwrappedType?,
        variableWithConstraints: VariableWithConstraints
    ): UnwrappedType? {
        if (firstCandidate == null || secondCandidate == null) return firstCandidate ?: secondCandidate

        if (isSuitableType(firstCandidate, variableWithConstraints)) return firstCandidate

        if (isSuitableType(secondCandidate, variableWithConstraints)) {
            return secondCandidate
        } else {
            return firstCandidate
        }
    }

    private fun Context.isSuitableType(resultType: UnwrappedType, variableWithConstraints: VariableWithConstraints): Boolean {
        for (constraint in variableWithConstraints.constraints) {
            if (!isProperType(constraint.type)) continue
            if (!checkConstraint(constraint.type, constraint.kind, resultType)) return false
        }
        return true
    }

    private fun findSubType(c: Context, variableWithConstraints: VariableWithConstraints): UnwrappedType? {
        val lowerConstraints = variableWithConstraints.constraints.filter { it.kind == ConstraintKind.LOWER && c.isProperType(it.type) }
        if (lowerConstraints.isNotEmpty()) {
            val commonSuperType = NewCommonSuperTypeCalculator.commonSuperType(lowerConstraints.map { it.type })
            val adjustedCommonSuperType = commonSuperType.foldNumberTypes()
            /**
             *
             * fun <T> Array<out T>.intersect(other: Iterable<T>) {
             *      val set = toMutableSet()
             *      set.retainAll(other)
             * }
             * fun <X> Array<out X>.toMutableSet(): MutableSet<X> = ...
             * fun <Y> MutableCollection<in Y>.retainAll(elements: Iterable<Y>) {}
             *
             * Here, when we solve type system for `toMutableSet` we have the following constrains:
             * Array<C(out T)> <: Array<out X> => C(out X) <: T.
             * If we fix it to T = C(out X) then return type of `toMutableSet()` will be `MutableSet<C(out X)>`
             * and type of variable `set` will be `MutableSet<out T>` and the following line will have contradiction.
             *
             * To fix this problem when we fix variable, we will approximate captured types before fixation.
             *
             */

            return typeApproximator.approximateToSuperType(
                adjustedCommonSuperType,
                TypeApproximatorConfiguration.CapturedTypesApproximation
            )
                    ?: adjustedCommonSuperType
        }

        return null
    }

    private fun findSuperType(c: Context, variableWithConstraints: VariableWithConstraints): UnwrappedType? {
        val upperConstraints = variableWithConstraints.constraints.filter { it.kind == ConstraintKind.UPPER && c.isProperType(it.type) }
        if (upperConstraints.isNotEmpty()) {
            val upperType = intersectTypes(upperConstraints.map { it.type })

            return typeApproximator.approximateToSubType(upperType, TypeApproximatorConfiguration.CapturedTypesApproximation) ?: upperType
        }
        return null
    }

    fun findResultIfThereIsEqualsConstraint(
        c: Context,
        variableWithConstraints: VariableWithConstraints,
        allowedFixToNotProperType: Boolean = false
    ): UnwrappedType? {
        val properEqualsConstraint = variableWithConstraints.constraints.filter {
            it.kind == ConstraintKind.EQUALITY && c.isProperType(it.type)
        }

        if (properEqualsConstraint.isNotEmpty()) {
            return properEqualsConstraint.map { it.type }.singleBestRepresentative()?.unwrap()
                    ?: properEqualsConstraint.first().type // seems like constraint system has contradiction
        }
        if (!allowedFixToNotProperType) return null

        val notProperEqualsConstraint = variableWithConstraints.constraints.filter { it.kind == ConstraintKind.EQUALITY }

        // may be we should just firstOrNull
        return notProperEqualsConstraint.singleOrNull()?.type
    }
}