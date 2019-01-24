/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types.checker

import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.CapturedType
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.model.*

interface ClassicTypeSystemContext : TypeSystemContext {
    override fun TypeConstructorIM.isDenotable(): Boolean {
        require(this is TypeConstructor)
        return this.isDenotable
    }

    override fun identicalArguments(a: SimpleTypeIM, b: SimpleTypeIM): Boolean {
        require(a is SimpleType)
        require(b is SimpleType)
        return a.arguments === b.arguments
    }

    override fun KotlinTypeIM.asSimpleType(): SimpleTypeIM? {
        require(this is KotlinType)
        return this.unwrap() as? SimpleType
    }

    override fun KotlinTypeIM.asFlexibleType(): FlexibleTypeIM? {
        require(this is KotlinType)
        return this.unwrap() as? FlexibleType
    }

    override fun FlexibleTypeIM.asDynamicType(): DynamicTypeIM? {
        assert(this is FlexibleType)
        return this as? DynamicType
    }

    override fun FlexibleTypeIM.asRawType(): RawTypeIM? {
        assert(this is FlexibleType)
        return this as? RawType
    }

    override fun FlexibleTypeIM.upperBound(): SimpleTypeIM {
        require(this is FlexibleType)
        return this.upperBound
    }

    override fun FlexibleTypeIM.lowerBound(): SimpleTypeIM {
        require(this is FlexibleType)
        return this.lowerBound
    }

    override fun SimpleTypeIM.asCapturedType(): CapturedTypeIM? {
        assert(this is SimpleType)
        return this as? NewCapturedType ?: this as? CapturedType//TODO ?!
    }

    override fun SimpleTypeIM.asDefinitelyNotNullType(): DefinitelyNotNullTypeIM? {
        assert(this is SimpleType)
        return this as? DefinitelyNotNullType
    }

    override fun SimpleTypeIM.isMarkedNullable(): Boolean {
        require(this is SimpleType)
        return this.isMarkedNullable
    }

    override fun SimpleTypeIM.typeConstructor(): TypeConstructorIM {
        require(this is SimpleType)
        return this.constructor
    }

    override fun SimpleTypeIM.argumentsCount(): Int {
        require(this is SimpleType)
        return this.arguments.size
    }

    override fun SimpleTypeIM.getArgument(index: Int): TypeArgumentIM {
        require(this is SimpleType)
        return this.arguments[index]
    }

    override fun TypeArgumentIM.isStarProjection(): Boolean {
        require(this is TypeProjection)
        return this.isStarProjection
    }

    override fun TypeArgumentIM.getVariance(): TypeVariance {
        require(this is TypeProjection)
        return this.projectionKind.convertVariance()
    }

    private fun Variance.convertVariance(): TypeVariance {
        return when (this) {
            Variance.INVARIANT -> TypeVariance.INV
            Variance.IN_VARIANCE -> TypeVariance.IN
            Variance.OUT_VARIANCE -> TypeVariance.OUT
        }
    }

    override fun TypeArgumentIM.getType(): KotlinTypeIM {
        require(this is TypeProjection)
        return this.type.unwrap()
    }

    override fun TypeConstructorIM.isErrorTypeConstructor(): Boolean {
        require(this is TypeConstructor)
        TODO("not implemented")
    }

    override fun TypeConstructorIM.parametersCount(): Int {
        require(this is TypeConstructor)
        return this.parameters.size
    }

    override fun TypeConstructorIM.getParameter(index: Int): TypeParameterIM {
        require(this is TypeConstructor)
        return this.parameters[index]
    }

    override fun TypeConstructorIM.supertypesCount(): Int {
        require(this is TypeConstructor)
        return this.supertypes.size
    }

    override fun TypeConstructorIM.getSupertype(index: Int): KotlinTypeIM {
        require(this is TypeConstructor)
        require(this.supertypes is List<*>) { "Expected to provide index access for supertypes" }
        return (this.supertypes as List<KotlinType>)[index] // TODO: Something better here?
    }

    override fun TypeParameterIM.getVariance(): TypeVariance {
        require(this is TypeParameterDescriptor)
        return this.variance.convertVariance()
    }

    override fun TypeParameterIM.upperBoundCount(): Int {
        require(this is TypeParameterDescriptor)
        return this.upperBounds.size
    }

    override fun TypeParameterIM.getUpperBound(index: Int): KotlinTypeIM {
        require(this is TypeParameterDescriptor)
        return this.upperBounds[index]
    }

    override fun TypeParameterIM.getTypeConstructor(): TypeConstructorIM {
        require(this is TypeParameterDescriptor)
        return this.typeConstructor
    }

    override fun isEqualTypeConstructors(c1: TypeConstructorIM, c2: TypeConstructorIM): Boolean {
        assert(c1 is TypeConstructor)
        assert(c2 is TypeConstructor)
        return c1 == c2
    }

}