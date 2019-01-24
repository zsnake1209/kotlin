/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.types.model.*


abstract class AbstractTypeCheckerContext : TypeSystemContext {
    abstract fun areEqualTypeConstructors(a: TypeConstructorIM, b: TypeConstructorIM): Boolean

    abstract fun backupIsSubType(subtype: KotlinTypeIM, supertype: KotlinTypeIM): Boolean

    abstract val isErrorTypeEqualsToAnything: Boolean

    protected var argumentsDepth = 0


    internal inline fun <T> runWithArgumentsSettings(subArgument: KotlinTypeIM, f: AbstractTypeCheckerContext.() -> T): T {
        if (argumentsDepth > 100) {
            error("Arguments depth is too high. Some related argument: $subArgument")
        }

        argumentsDepth++
        val result = f()
        argumentsDepth--
        return result
    }

interface AbstractTypeCheckerContext : TypeSystemContext {
    fun areEqualTypeConstructors(a: TypeConstructorIM, b: TypeConstructorIM): Boolean

    fun backupIsSubType(subtype: KotlinTypeIM, supertype: KotlinTypeIM): Boolean
}

object AbstractTypeChecker {
    fun isSubtypeOf(context: AbstractTypeCheckerContext, subtype: KotlinTypeIM, supertype: KotlinTypeIM): Boolean = with(context) {
        return context.backupIsSubType(subtype, supertype)
    }


    fun equalTypes(context: AbstractTypeCheckerContext, a: KotlinTypeIM, b: KotlinTypeIM): Boolean = with(context) {
        if (a === b) return true

        if (isCommonDenotableType(a) && isCommonDenotableType(b)) {
            val simpleA = a.lowerBoundIfFlexible()
            if (!areEqualTypeConstructors(a.typeConstructor(), b.typeConstructor())) return false
            if (simpleA.argumentsCount() == 0) {
                if (a.hasFlexibleNullability() || b.hasFlexibleNullability()) return true

                return simpleA.isMarkedNullable() == b.lowerBoundIfFlexible().isMarkedNullable()
            }
        }

        return isSubtypeOf(context, a, b) && isSubtypeOf(context, b, a)
    }


    fun AbstractTypeCheckerContext.isSubtypeForSameConstructor(
        capturedSubArguments: List<TypeArgumentIM>,
        superType: SimpleTypeIM
    ): Boolean {
        // No way to check, as no index sometimes
        //if (capturedSubArguments === superType.arguments) return true

        //val parameters = superType.constructor.parameters
        val superTypeConstructor = superType.typeConstructor()
        for (index in 0 until superTypeConstructor.parametersCount()) {
            val superProjection = superType.getArgument(index) // todo error index
            if (superProjection.isStarProjection()) continue // A<B> <: A<*>

            val superArgumentType = superProjection.getType()
            val subArgumentType = capturedSubArguments[index].let {
                assert(it.getVariance() == TypeVariance.INV) { "Incorrect sub argument: $it" }
                it.getType()
            }

            val variance = effectiveVariance(superTypeConstructor.getParameter(index).getVariance(), superProjection.getVariance())
                ?: return isErrorTypeEqualsToAnything // todo exception?

            val correctArgument = runWithArgumentsSettings(subArgumentType) {
                when (variance) {
                    TypeVariance.INV -> equalTypes(this, subArgumentType, superArgumentType)
                    TypeVariance.OUT -> isSubtypeOf(this, subArgumentType, superArgumentType)
                    TypeVariance.IN -> isSubtypeOf(this, superArgumentType, subArgumentType)
                }
            }
            if (!correctArgument) return false
        }
        return true
    }

    private fun AbstractTypeCheckerContext.isCommonDenotableType(type: KotlinTypeIM): Boolean =
        type.typeConstructor().isDenotable() &&
                !type.isDynamic() && !type.isDefinitelyNotNullType() &&
                type.lowerBoundIfFlexible().typeConstructor() == type.upperBoundIfFlexible().typeConstructor()

    private fun effectiveVariance(declared: TypeVariance, useSite: TypeVariance): TypeVariance? {
        if (declared == TypeVariance.INV) return useSite
        if (useSite == TypeVariance.INV) return declared

        // both not INVARIANT
        if (declared == useSite) return declared

        // composite In with Out
        return null
    }
}
