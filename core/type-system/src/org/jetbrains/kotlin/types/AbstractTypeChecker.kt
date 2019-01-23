/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.types.model.KotlinTypeIM
import org.jetbrains.kotlin.types.model.SimpleTypeIM
import org.jetbrains.kotlin.types.model.TypeConstructorIM
import org.jetbrains.kotlin.types.model.TypeSystemContext


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


    private fun AbstractTypeCheckerContext.isCommonDenotableType(type: KotlinTypeIM): Boolean =
        type.typeConstructor().isDenotable() &&
                !type.isDynamic() && !type.isDefinitelyNotNullType() &&
                type.lowerBoundIfFlexible().typeConstructor() == type.upperBoundIfFlexible().typeConstructor()
}
