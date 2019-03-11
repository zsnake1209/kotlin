/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.ir.declarations.IrTypeParametersContainer
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.impl.IrDynamicTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrErrorTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.types.typesApproximation.approximateCapturedTypes

class TypeTranslator(
    private val symbolTable: ReferenceSymbolTable,
    val languageVersionSettings: LanguageVersionSettings,
    private val typeParametersResolver: TypeParametersResolver = ScopedTypeParametersResolver(),
    private val enterTableScope: Boolean = false
) {

    private val typeApproximatorForNI = TypeApproximator()
    lateinit var constantValueGenerator: ConstantValueGenerator

    fun enterScope(irElement: IrTypeParametersContainer) {
        typeParametersResolver.enterTypeParameterScope(irElement)
        if (enterTableScope) {
            symbolTable.enterScope(irElement.descriptor)
        }
    }

    fun leaveScope(irElement: IrTypeParametersContainer) {
        typeParametersResolver.leaveTypeParameterScope()
        if (enterTableScope) {
            symbolTable.leaveScope(irElement.descriptor)
        }
    }

    inline fun <T> buildWithScope(container: IrTypeParametersContainer, builder: () -> T): T {
        enterScope(container)
        val result = builder()
        leaveScope(container)
        return result
    }

    private fun resolveTypeParameter(typeParameterDescriptor: TypeParameterDescriptor) =
        typeParametersResolver.resolveScopedTypeParameter(typeParameterDescriptor)
            ?: symbolTable.referenceTypeParameter(typeParameterDescriptor)

    fun translateType(ktType: KotlinType): IrType =
        translateType(ktType, Variance.INVARIANT).type

    private fun translateType(ktType: KotlinType, variance: Variance): IrTypeProjection {
        val approximatedType = LegacyTypeApproximation().approximate(ktType)

        when {
            approximatedType.isError ->
                return IrErrorTypeImpl(approximatedType, translateTypeAnnotations(approximatedType.annotations), variance)
            approximatedType.isDynamic() ->
                return IrDynamicTypeImpl(approximatedType, translateTypeAnnotations(approximatedType.annotations), variance)
            approximatedType.isFlexible() ->
                return translateType(approximatedType.upperIfFlexible(), variance)
        }

        val ktTypeConstructor = approximatedType.constructor
        val ktTypeDescriptor = ktTypeConstructor.declarationDescriptor
            ?: throw AssertionError("No descriptor for type $approximatedType")

        return when (ktTypeDescriptor) {
            is TypeParameterDescriptor ->
                IrSimpleTypeImpl(
                    approximatedType,
                    resolveTypeParameter(ktTypeDescriptor),
                    approximatedType.isMarkedNullable,
                    emptyList(),
                    null,
                    translateTypeAnnotations(approximatedType.annotations),
                    variance
                )

            is ClassDescriptor ->
                translatePossiblyInnerType(approximatedType, variance)

            else ->
                throw AssertionError("Unexpected type descriptor $ktTypeDescriptor :: ${ktTypeDescriptor::class}")
        }
    }

    private fun translatePossiblyInnerType(kotlinType: KotlinType, variance: Variance): IrSimpleTypeImpl {
        val classDescriptor = kotlinType.constructor.declarationDescriptor as? ClassDescriptor
            ?: error("Expected class type, got: $kotlinType")
        val classSymbol = symbolTable.referenceClass(classDescriptor)

        val ownTypeArguments = kotlinType.arguments.subList(0, classDescriptor.declaredTypeParameters.size)
        val irTypeArguments = translateTypeArguments(ownTypeArguments)

        val outerType = kotlinType.outerType()?.let {
            translatePossiblyInnerType(it, variance)
        }

        val irTypeAnnotations = translateTypeAnnotations(kotlinType.annotations)

        return IrSimpleTypeImpl(
            kotlinType,
            classSymbol,
            kotlinType.isMarkedNullable,
            irTypeArguments,
            outerType,
            irTypeAnnotations,
            variance
        )
    }

    private fun KotlinType.outerType(): KotlinType? {
        val typeDescriptor = constructor.declarationDescriptor ?: return null
        val classDescriptor = typeDescriptor as? ClassDescriptor ?: return null

        // TODO there were some bugs with 'isInner' for local classes - investigate
        if (!classDescriptor.isInner) return null

        val outerClassDescriptor = classDescriptor.containingDeclaration as? ClassDescriptor
            ?: error("Containing declaration of inner class $classDescriptor is not a class: ${classDescriptor.containingDeclaration}")

        val outerTypeArguments = arguments.subList(classDescriptor.declaredTypeParameters.size, arguments.size)

        return KotlinTypeFactory.simpleNotNullType(Annotations.EMPTY, outerClassDescriptor, outerTypeArguments)
    }

    private inner class LegacyTypeApproximation {

        fun approximate(ktType: KotlinType): KotlinType {
            val properlyApproximatedType = approximateByKotlinRules(ktType)

            // If there's an intersection type, take the most common supertype of its intermediate supertypes.
            // That's what old back-end effectively does.
            val typeConstructor = properlyApproximatedType.constructor
            if (typeConstructor is IntersectionTypeConstructor) {
                val commonSupertype = CommonSupertypes.commonSupertype(typeConstructor.supertypes)
                return approximate(commonSupertype.replaceArgumentsWithStarProjections())
            }

            // Other types should be approximated properly. Right? Riiight?
            return properlyApproximatedType
        }


        private fun approximateByKotlinRules(ktType: KotlinType): KotlinType {
            if (ktType.constructor.isDenotable) return ktType

            return if (languageVersionSettings.supportsFeature(LanguageFeature.NewInference))
                typeApproximatorForNI.approximateDeclarationType(ktType, local = false, languageVersionSettings = languageVersionSettings)
            else
                approximateCapturedTypes(ktType).upper
        }

    }


    private fun translateTypeAnnotations(annotations: Annotations): List<IrCall> =
        annotations.map(constantValueGenerator::generateAnnotationConstructorCall)

    private fun translateTypeArguments(arguments: List<TypeProjection>) =
        arguments.map {
            if (it.isStarProjection)
                IrStarProjectionImpl
            else
                translateType(it.type, it.projectionKind)
        }
}
