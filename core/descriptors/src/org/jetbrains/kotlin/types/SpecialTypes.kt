/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.types

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.checker.NewTypeVariableConstructor
import org.jetbrains.kotlin.types.checker.NullabilityChecker
import org.jetbrains.kotlin.types.typeUtil.canHaveUndefinedNullability
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

abstract class DelegatingSimpleType : SimpleType() {
    protected abstract val delegate: SimpleType

    override val annotations: Annotations get() = delegate.annotations
    override val constructor: TypeConstructor get() = delegate.constructor
    override val arguments: List<TypeProjection> get() = delegate.arguments
    override val isMarkedNullable: Boolean get() = delegate.isMarkedNullable
    override val memberScope: MemberScope get() = delegate.memberScope
}

class AbbreviatedType(override val delegate: SimpleType, val abbreviation: SimpleType) : DelegatingSimpleType() {
    val expandedType: SimpleType get() = delegate

    override fun replaceAnnotations(newAnnotations: Annotations)
            = AbbreviatedType(delegate.replaceAnnotations(newAnnotations), abbreviation)

    override fun makeNullableAsSpecified(newNullability: Boolean)
            = AbbreviatedType(delegate.makeNullableAsSpecified(newNullability), abbreviation.makeNullableAsSpecified(newNullability))
}

fun KotlinType.getAbbreviatedType(): AbbreviatedType? = unwrap() as? AbbreviatedType
fun KotlinType.getAbbreviation(): SimpleType? = getAbbreviatedType()?.abbreviation

fun SimpleType.withAbbreviation(abbreviatedType: SimpleType): SimpleType {
    if (isError) return this
    return AbbreviatedType(this, abbreviatedType)
}

class LazyWrappedType(storageManager: StorageManager, computation: () -> KotlinType): WrappedType() {
    private val lazyValue = storageManager.createLazyValue(computation)

    override val delegate: KotlinType get() = lazyValue()

    override fun isComputed(): Boolean = lazyValue.isComputed()
}

class DefinitelyNotNullType private constructor(val original: SimpleType) : DelegatingSimpleType(), CustomTypeVariable {
    companion object {
        internal fun makeDefinitelyNotNull(type: UnwrappedType): DefinitelyNotNullType? {
            return when {
                type is DefinitelyNotNullType -> type

                makesSenseToBeDefinitelyNotNull(type) -> {
                    if (type is FlexibleType) {
                        assert(type.lowerBound.constructor == type.upperBound.constructor) {
                            "DefinitelyNotNullType for flexible type ($type) can be created only from type variable with the same constructor for bounds"
                        }
                    }


                    DefinitelyNotNullType(type.lowerIfFlexible())
                }

                else -> null
            }
        }

        fun makesSenseToBeDefinitelyNotNull(type: UnwrappedType): Boolean =
                type.canHaveUndefinedNullability() && !NullabilityChecker.isSubtypeOfAny(type)
    }

    override val delegate: SimpleType
        get() = original

    override val isMarkedNullable: Boolean
        get() = false

    override val isTypeVariable: Boolean
        get() = delegate.constructor is NewTypeVariableConstructor ||
                delegate.constructor.declarationDescriptor is TypeParameterDescriptor

    override fun substitutionResult(replacement: KotlinType): KotlinType =
            replacement.unwrap().makeDefinitelyNotNullOrNotNull()

    override fun replaceAnnotations(newAnnotations: Annotations): DefinitelyNotNullType =
            DefinitelyNotNullType(delegate.replaceAnnotations(newAnnotations))

    override fun makeNullableAsSpecified(newNullability: Boolean): SimpleType =
            if (newNullability) delegate.makeNullableAsSpecified(newNullability) else this

    override fun toString(): String = "$delegate!!"
}

val KotlinType.isDefinitelyNotNullType: Boolean
    get() = unwrap() is DefinitelyNotNullType

fun SimpleType.makeSimpleTypeDefinitelyNotNullOrNotNull(): SimpleType =
        DefinitelyNotNullType.makeDefinitelyNotNull(this) ?: makeNullableAsSpecified(false)

fun UnwrappedType.makeDefinitelyNotNullOrNotNull(): UnwrappedType =
        DefinitelyNotNullType.makeDefinitelyNotNull(this) ?: makeNullableAsSpecified(false)

class IntegerValueType(
    val builtIns: KotlinBuiltIns,
    val supertypes: List<KotlinType>,
    override val memberScope: MemberScope,
    override val isMarkedNullable: Boolean = false,
    override val annotations: Annotations = Annotations.EMPTY
) : SimpleType() {
    override val constructor: TypeConstructor
        get() = NewIntegerValueTypeConstructor(builtIns, this, supertypes)

    override val arguments: List<TypeProjection> =
        emptyList()

    override fun replaceAnnotations(newAnnotations: Annotations): SimpleType {
        return IntegerValueType(builtIns, supertypes, memberScope, isMarkedNullable, newAnnotations)
    }

    override fun makeNullableAsSpecified(newNullability: Boolean): SimpleType {
        return if (isMarkedNullable == newNullability)
            this
        else
            IntegerValueType(builtIns, supertypes, memberScope, newNullability, annotations)
    }

    override fun toString(): String {
        return "IntegerValueType($supertypes)"
    }
}

class NewIntegerValueTypeConstructor(
    private val builtIns: KotlinBuiltIns,
    val integerValueType: IntegerValueType,
    val integerSupertypes: List<KotlinType>
) : TypeConstructor {
    val comparableSupertype = builtIns.comparable.defaultType.replace(listOf(TypeProjectionImpl(integerValueType)))
    private val hashCode = integerValueType.supertypes.hashCode()

    override fun getSupertypes(): Collection<KotlinType> = listOf(builtIns.number.defaultType, comparableSupertype)

    override fun getParameters(): List<TypeParameterDescriptor> = emptyList()

    override fun isFinal() = false

    override fun isDenotable() = false

    override fun getDeclarationDescriptor() = null

    override fun getBuiltIns(): KotlinBuiltIns {
        return builtIns
    }

    override fun toString() = "NewIntegerValueType($supertypes ; $integerSupertypes)"

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return integerValueType.supertypes == other.safeAs<NewIntegerValueTypeConstructor>()?.integerValueType?.supertypes
    }
}
