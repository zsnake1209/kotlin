/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types.checker

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns.FQ_NAMES
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.isFinalClass
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.model.*
import org.jetbrains.kotlin.types.model.CaptureStatus
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection

interface ClassicTypeSystemContext : TypeSystemContext {
    override fun TypeConstructorIM.isDenotable(): Boolean {
        require(this is TypeConstructor, this::errorMessage)
        return this.isDenotable
    }

    override fun SimpleTypeIM.withNullability(nullable: Boolean): SimpleTypeIM {
        require(this is SimpleType, this::errorMessage)
        return this.makeNullableAsSpecified(nullable)
    }

    override fun KotlinTypeIM.isError(): Boolean {
        require(this is KotlinType, this::errorMessage)
        return this.isError
    }

    override fun SimpleTypeIM.isStubType(): Boolean {
        assert(this is SimpleType, this::errorMessage)
        return this is StubType
    }

    override fun CapturedTypeIM.lowerType(): KotlinTypeIM? {
        require(this is NewCapturedType, this::errorMessage)
        return this.lowerType
    }

    override fun TypeConstructorIM.isIntersection(): Boolean {
        assert(this is TypeConstructor, this::errorMessage)
        return this is IntersectionTypeConstructor
    }

    override fun identicalArguments(a: SimpleTypeIM, b: SimpleTypeIM): Boolean {
        require(a is SimpleType, a::errorMessage)
        require(b is SimpleType, b::errorMessage)
        return a.arguments === b.arguments
    }

    override fun KotlinTypeIM.asSimpleType(): SimpleTypeIM? {
        require(this is KotlinType, this::errorMessage)
        return this.unwrap() as? SimpleType
    }

    override fun KotlinTypeIM.asFlexibleType(): FlexibleTypeIM? {
        require(this is KotlinType, this::errorMessage)
        return this.unwrap() as? FlexibleType
    }

    override fun FlexibleTypeIM.asDynamicType(): DynamicTypeIM? {
        assert(this is FlexibleType, this::errorMessage)
        return this as? DynamicType
    }

    override fun FlexibleTypeIM.asRawType(): RawTypeIM? {
        assert(this is FlexibleType, this::errorMessage)
        return this as? RawType
    }

    override fun FlexibleTypeIM.upperBound(): SimpleTypeIM {
        require(this is FlexibleType, this::errorMessage)
        return this.upperBound
    }

    override fun FlexibleTypeIM.lowerBound(): SimpleTypeIM {
        require(this is FlexibleType, this::errorMessage)
        return this.lowerBound
    }

    override fun SimpleTypeIM.asCapturedType(): CapturedTypeIM? {
        assert(this is SimpleType, this::errorMessage)
        return this as? NewCapturedType
    }

    override fun SimpleTypeIM.asDefinitelyNotNullType(): DefinitelyNotNullTypeIM? {
        assert(this is SimpleType, this::errorMessage)
        return this as? DefinitelyNotNullType
    }

    override fun SimpleTypeIM.isMarkedNullable(): Boolean {
        require(this is SimpleType, this::errorMessage)
        return this.isMarkedNullable
    }

    override fun SimpleTypeIM.typeConstructor(): TypeConstructorIM {
        require(this is SimpleType, this::errorMessage)
        return this.constructor
    }

    override fun SimpleTypeIM.argumentsCount(): Int {
        require(this is SimpleType, this::errorMessage)
        return this.arguments.size
    }

    override fun SimpleTypeIM.getArgument(index: Int): TypeArgumentIM {
        require(this is SimpleType, this::errorMessage)
        return this.arguments[index]
    }

    override fun TypeArgumentIM.isStarProjection(): Boolean {
        require(this is TypeProjection, this::errorMessage)
        return this.isStarProjection
    }

    override fun TypeArgumentIM.getVariance(): TypeVariance {
        require(this is TypeProjection, this::errorMessage)
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
        require(this is TypeProjection, this::errorMessage)
        return this.type.unwrap()
    }

    override fun TypeConstructorIM.isErrorTypeConstructor(): Boolean {
        require(this is TypeConstructor, this::errorMessage)
        TODO("not implemented")
    }

    override fun TypeConstructorIM.parametersCount(): Int {
        require(this is TypeConstructor, this::errorMessage)
        return this.parameters.size
    }

    override fun TypeConstructorIM.getParameter(index: Int): TypeParameterIM {
        require(this is TypeConstructor, this::errorMessage)
        return this.parameters[index]
    }

    override fun TypeConstructorIM.supertypes(): Collection<KotlinTypeIM> {
        require(this is TypeConstructor, this::errorMessage)
        return this.supertypes
    }

    override fun TypeParameterIM.getVariance(): TypeVariance {
        require(this is TypeParameterDescriptor, this::errorMessage)
        return this.variance.convertVariance()
    }

    override fun TypeParameterIM.upperBoundCount(): Int {
        require(this is TypeParameterDescriptor, this::errorMessage)
        return this.upperBounds.size
    }

    override fun TypeParameterIM.getUpperBound(index: Int): KotlinTypeIM {
        require(this is TypeParameterDescriptor, this::errorMessage)
        return this.upperBounds[index]
    }

    override fun TypeParameterIM.getTypeConstructor(): TypeConstructorIM {
        require(this is TypeParameterDescriptor, this::errorMessage)
        return this.typeConstructor
    }

    override fun isEqualTypeConstructors(c1: TypeConstructorIM, c2: TypeConstructorIM): Boolean {
        assert(c1 is TypeConstructor, c1::errorMessage)
        assert(c2 is TypeConstructor, c2::errorMessage)
        return c1 == c2
    }

    override fun TypeConstructorIM.isClassTypeConstructor(): Boolean {
        require(this is TypeConstructor, this::errorMessage) 
        return declarationDescriptor is ClassDescriptor
    }

    override fun TypeConstructorIM.isCommonFinalClassConstructor(): Boolean {
        require(this is TypeConstructor, this::errorMessage) 
        val classDescriptor = declarationDescriptor as? ClassDescriptor ?: return false
        return classDescriptor.isFinalClass &&
                classDescriptor.kind != ClassKind.ENUM_ENTRY &&
                classDescriptor.kind != ClassKind.ANNOTATION_CLASS
    }


    override fun TypeArgumentListIM.get(index: Int): TypeArgumentIM {
        return when (this) {
            is SimpleTypeIM -> getArgument(index)
            is ArgumentList -> get(index)
            else -> error("unknown type argument list type: $this, ${this::class}")
        }
    }

    override fun TypeArgumentListIM.size(): Int {
        return when (this) {
            is SimpleTypeIM -> argumentsCount()
            is ArgumentList -> size
            else -> error("unknown type argument list type: $this, ${this::class}")
        }
    }

    override fun SimpleTypeIM.asArgumentList(): TypeArgumentListIM {
        require(this is SimpleType, this::errorMessage) 
        return this
    }

    override fun captureFromArguments(type: SimpleTypeIM, status: CaptureStatus): SimpleTypeIM? {
        require(type is SimpleType, type::errorMessage) 
        return org.jetbrains.kotlin.types.checker.captureFromArguments(type, status)
    }

    override fun TypeConstructorIM.isAnyConstructor(): Boolean {
        require(this is TypeConstructor, this::errorMessage) 
        return KotlinBuiltIns.isTypeConstructorForGivenClass(this, FQ_NAMES.any)
    }

    override fun TypeConstructorIM.isNothingConstructor(): Boolean {
        require(this is TypeConstructor, this::errorMessage) 
        return KotlinBuiltIns.isTypeConstructorForGivenClass(this, FQ_NAMES.nothing)
    }

    override fun KotlinTypeIM.asTypeArgument(): TypeArgumentIM {
        require(this is KotlinType, this::errorMessage)
        return this.asTypeProjection()
    }
}


@Suppress("NOTHING_TO_INLINE")
private inline fun Any.errorMessage(): String {
    return "ClassicTypeSystemContext couldn't handle: $this, ${this::class}"
} 