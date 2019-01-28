/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.types.model

interface KotlinTypeIM
interface TypeArgumentIM
interface TypeConstructorIM
interface TypeParameterIM

interface SimpleTypeIM : KotlinTypeIM
interface CapturedTypeIM : SimpleTypeIM
interface DefinitelyNotNullTypeIM : SimpleTypeIM

interface FlexibleTypeIM : KotlinTypeIM
interface DynamicTypeIM : FlexibleTypeIM
interface RawTypeIM : FlexibleTypeIM

interface TypeArgumentListIM


enum class TypeVariance {
    IN,
    OUT,
    INV
}


interface TypeSystemOptimizationContext {
    /**
     *  @return true is a.arguments == b.arguments, or false if not supported
     */
    fun identicalArguments(a: SimpleTypeIM, b: SimpleTypeIM) = false
}


class ArgumentList : ArrayList<TypeArgumentIM>(), TypeArgumentListIM


interface TypeSystemContext : TypeSystemOptimizationContext {
    fun KotlinTypeIM.asSimpleType(): SimpleTypeIM?
    fun KotlinTypeIM.asFlexibleType(): FlexibleTypeIM?

    fun KotlinTypeIM.isError(): Boolean

    fun FlexibleTypeIM.asDynamicType(): DynamicTypeIM?

    fun FlexibleTypeIM.asRawType(): RawTypeIM?
    fun FlexibleTypeIM.upperBound(): SimpleTypeIM

    fun FlexibleTypeIM.lowerBound(): SimpleTypeIM
    fun SimpleTypeIM.asCapturedType(): CapturedTypeIM?

    fun SimpleTypeIM.asDefinitelyNotNullType(): DefinitelyNotNullTypeIM?
    fun SimpleTypeIM.isMarkedNullable(): Boolean
    fun SimpleTypeIM.withNullability(nullable: Boolean): SimpleTypeIM
    fun SimpleTypeIM.typeConstructor(): TypeConstructorIM

    fun SimpleTypeIM.argumentsCount(): Int
    fun SimpleTypeIM.getArgument(index: Int): TypeArgumentIM

    fun SimpleTypeIM.getArgumentOrNull(index: Int): TypeArgumentIM? {
        if (index in 0 until argumentsCount()) return getArgument(index)
        return null
    }

    fun SimpleTypeIM.isStubType(): Boolean = false

    fun KotlinTypeIM.asTypeArgument(): TypeArgumentIM

    fun CapturedTypeIM.lowerType(): KotlinTypeIM?

    fun TypeArgumentIM.isStarProjection(): Boolean
    fun TypeArgumentIM.getVariance(): TypeVariance
    fun TypeArgumentIM.getType(): KotlinTypeIM

    fun TypeConstructorIM.isErrorTypeConstructor(): Boolean
    fun TypeConstructorIM.parametersCount(): Int
    fun TypeConstructorIM.getParameter(index: Int): TypeParameterIM
    fun TypeConstructorIM.supertypes(): Collection<KotlinTypeIM>
    fun TypeConstructorIM.isIntersection(): Boolean
    fun TypeConstructorIM.isClassTypeConstructor(): Boolean

    fun TypeParameterIM.getVariance(): TypeVariance
    fun TypeParameterIM.upperBoundCount(): Int
    fun TypeParameterIM.getUpperBound(index: Int): KotlinTypeIM
    fun TypeParameterIM.getTypeConstructor(): TypeConstructorIM

    fun isEqualTypeConstructors(c1: TypeConstructorIM, c2: TypeConstructorIM): Boolean

    fun TypeConstructorIM.isDenotable(): Boolean

    fun KotlinTypeIM.lowerBoundIfFlexible(): SimpleTypeIM = this.asFlexibleType()?.lowerBound() ?: this.asSimpleType()!!
    fun KotlinTypeIM.upperBoundIfFlexible(): SimpleTypeIM = this.asFlexibleType()?.upperBound() ?: this.asSimpleType()!!

    fun KotlinTypeIM.isDynamic(): Boolean = asFlexibleType()?.asDynamicType() != null
    fun KotlinTypeIM.isDefinitelyNotNullType(): Boolean = asSimpleType()?.asDefinitelyNotNullType() != null

    fun KotlinTypeIM.hasFlexibleNullability() =
        lowerBoundIfFlexible().isMarkedNullable() != upperBoundIfFlexible().isMarkedNullable()

    fun KotlinTypeIM.typeConstructor(): TypeConstructorIM =
        (asSimpleType() ?: lowerBoundIfFlexible()).typeConstructor()

    fun SimpleTypeIM.isClassType(): Boolean = typeConstructor().isClassTypeConstructor()

    fun TypeConstructorIM.isCommonFinalClassConstructor(): Boolean

    fun captureFromArguments(
        type: SimpleTypeIM,
        status: CaptureStatus
    ): SimpleTypeIM?

    fun SimpleTypeIM.asArgumentList(): TypeArgumentListIM

    fun TypeArgumentListIM.size(): Int
    operator fun TypeArgumentListIM.get(index: Int): TypeArgumentIM

    fun TypeConstructorIM.isAnyConstructor(): Boolean
    fun TypeConstructorIM.isNothingConstructor(): Boolean
}

enum class CaptureStatus {
    FOR_SUBTYPING,
    FOR_INCORPORATION,
    FROM_EXPRESSION
}