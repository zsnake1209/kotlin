/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.types.checker

import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker.isSubtypeOfForSingleClassifierType
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker.transformAndIsSubTypeOf
import org.jetbrains.kotlin.types.model.KotlinTypeIM
import org.jetbrains.kotlin.types.model.SimpleTypeIM
import org.jetbrains.kotlin.types.model.TypeConstructorIM
import org.jetbrains.kotlin.utils.SmartSet
import java.util.*

open class TypeCheckerContext(val errorTypeEqualsToAnything: Boolean, val allowedTypeVariable: Boolean = true) : ClassicTypeSystemContext, AbstractTypeCheckerContext() {
    override fun backupIsSubtypeOfForSingleClassifierType(subType: SimpleTypeIM, superType: SimpleTypeIM): Boolean {
        return this.isSubtypeOfForSingleClassifierType(subType as SimpleType, superType as SimpleType)
    }

    override fun enterIsSubTypeOf(subType: KotlinTypeIM, superType: KotlinTypeIM): Boolean {
        return transformAndIsSubTypeOf((subType as KotlinType).unwrap(), (superType as KotlinType).unwrap())
    }

    override val isErrorTypeEqualsToAnything: Boolean
        get() = errorTypeEqualsToAnything

    override fun areEqualTypeConstructors(a: TypeConstructorIM, b: TypeConstructorIM): Boolean {
        require(a is TypeConstructor)
        require(b is TypeConstructor)
        return areEqualTypeConstructors(a, b)
    }

    open fun areEqualTypeConstructors(a: TypeConstructor, b: TypeConstructor): Boolean {
        return a == b
    }


//    internal sealed class SupertypesPolicy {
//        abstract fun transformType(type: KotlinType): SimpleType
//
//        object None : SupertypesPolicy() {
//            override fun transformType(type: KotlinType) = throw UnsupportedOperationException("Should not be called")
//        }
//
//        object UpperIfFlexible : SupertypesPolicy() {
//            override fun transformType(type: KotlinType) = type.upperIfFlexible()
//        }
//
//        object LowerIfFlexible : SupertypesPolicy() {
//            override fun transformType(type: KotlinType) = type.lowerIfFlexible()
//        }
//
//        class LowerIfFlexibleWithCustomSubstitutor(val substitutor: TypeSubstitutor): SupertypesPolicy() {
//            override fun transformType(type: KotlinType) =
//                    substitutor.safeSubstitute(type.lowerIfFlexible(), Variance.INVARIANT).asSimpleType()
//        }
//    }


    val UnwrappedType.isAllowedTypeVariable: Boolean get() = allowedTypeVariable && constructor is NewTypeVariableConstructor
}
