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
import org.jetbrains.kotlin.types.checker.NewKotlinTypeChecker.transformAndIsSubTypeOf
import org.jetbrains.kotlin.types.model.KotlinTypeIM
import org.jetbrains.kotlin.types.model.SimpleTypeIM
import org.jetbrains.kotlin.types.model.TypeConstructorIM

open class TypeCheckerContext(val errorTypeEqualsToAnything: Boolean, val allowedTypeVariable: Boolean = true) : ClassicTypeSystemContext, AbstractTypeCheckerContext() {
    override fun intersectTypes(projections: List<KotlinTypeIM>): KotlinTypeIM {
        return org.jetbrains.kotlin.types.checker.intersectTypes(projections as List<UnwrappedType>)
    }

    override fun nullabilityIsPossibleSupertype(subType: SimpleTypeIM, superType: SimpleTypeIM): Boolean {
        require(subType is SimpleType)
        require(superType is SimpleType)
        return NullabilityChecker.isPossibleSubtype(this, subType, superType)
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

    override fun substitutionSupertypePolicy(type: SimpleTypeIM): SupertypesPolicy.DoCustomTransform {
        val substitutor = TypeConstructorSubstitution.create(type as SimpleType).buildSubstitutor()

        return object : SupertypesPolicy.DoCustomTransform() {
            override fun transformType(context: AbstractTypeCheckerContext, type: KotlinTypeIM): SimpleTypeIM {
                return substitutor.safeSubstitute(
                    type.lowerBoundIfFlexible() as KotlinType,
                    Variance.INVARIANT
                ).asSimpleType()!!
            }
        }
    }


    val UnwrappedType.isAllowedTypeVariable: Boolean get() = allowedTypeVariable && constructor is NewTypeVariableConstructor
}
