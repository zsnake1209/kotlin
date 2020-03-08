/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu.compiler.extensions

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.ClassConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetFieldImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrSetFieldImpl
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.types.Variance
import kotlin.random.Random

interface TransformerHelpers {

    val context: IrPluginContext

    fun buildCall(
        target: IrFunctionSymbol,
        type: IrType? = null,
        origin: IrStatementOrigin,
        typeArguments: List<IrType>? = null,
        valueArguments: List<IrExpression?>? = null
    ): IrCall =
        IrCallImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            type ?: target.owner.returnType,
            target,
            target.descriptor.typeParametersCount,
            origin
        ).apply {
            typeArguments?.let {
                assert(typeArguments.size == typeArgumentsCount)
                it.withIndex().forEach { (i, t) -> putTypeArgument(i, t) }
            }
            valueArguments?.let {
                assert(valueArguments.size == valueArgumentsCount)
                it.withIndex().forEach { (i, arg) -> putValueArgument(i, arg) }
            }
        }

    fun buildSetField(
        symbol: IrFieldSymbol,
        receiver: IrExpression?,
        value: IrExpression,
        superQualifierSymbol: IrClassSymbol? = null
    ) =
        IrSetFieldImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            symbol,
            receiver,
            value,
            value.type,
            IrStatementOrigin.INVOKE,
            superQualifierSymbol
        )

    fun buildGetField(symbol: IrFieldSymbol, receiver: IrExpression?, superQualifierSymbol: IrClassSymbol? = null, type: IrType? = null) =
        IrGetFieldImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            symbol,
            type ?: symbol.owner.type,
            receiver,
            IrStatementOrigin.GET_PROPERTY,
            superQualifierSymbol
        )

    fun buildFunctionSimpleType(typeParameters: List<IrType>): IrSimpleType {
        val parametersCount = typeParameters.size - 1
        val classDesc = context.irBuiltIns.builtIns.getFunction(parametersCount)
        val symbol = context.symbolTable.referenceClass(classDesc)
        return IrSimpleTypeImpl(
            classifier = symbol,
            hasQuestionMark = false,
            arguments = typeParameters.map { it.toTypeArgument() },
            annotations = emptyList()
        )
    }

    fun getterName(name: String) = "<get-$name>${Random.nextInt()}"
    fun setterName(name: String) = "<set-$name>${Random.nextInt()}"
    fun Name.getFieldName() = "<get-(\\w+)>".toRegex().find(asString())?.groupValues?.get(1)

    private fun IrType.toTypeArgument(): IrTypeArgument {
        return makeTypeProjection(this, Variance.INVARIANT)
    }

    fun IrCall.getValueArguments() = (0 until valueArgumentsCount).map { i ->
        getValueArgument(i)
    }

    fun IrValueParameter.capture() = JsIrBuilder.buildGetValue(symbol)

    fun referencePackageFunction(
        packageName: String,
        name: String,
        predicate: (FunctionDescriptor) -> Boolean = { true }
    ) = referenceMemberScopeFunction(context.moduleDescriptor.getPackage(FqName(packageName)).memberScope, name, predicate)

    fun referenceBuiltInFunction(
        classDescriptor: ClassDescriptor,
        name: String,
        predicate: (FunctionDescriptor) -> Boolean = { true }
    ) = referenceMemberScopeFunction(classDescriptor.unsubstitutedMemberScope, name, predicate)

    fun referenceBuiltInConstructor(
        classDescriptor: ClassDescriptor,
        predicate: (ClassConstructorDescriptor) -> Boolean
    ): IrConstructorSymbol {
        val constructorDescriptor = classDescriptor.constructors.single(predicate)
        return context.symbolTable.referenceConstructor(constructorDescriptor)
    }

    fun referenceMemberScopeFunction(
        memberScope: MemberScope,
        name: String,
        predicate: (FunctionDescriptor) -> Boolean
    ): IrSimpleFunctionSymbol {
        val functionDescriptor = memberScope.getContributedFunctions(
            Name.identifier(name),
            NoLookupLocation.FROM_BUILTINS
        ).single(predicate)
        return context.symbolTable.referenceSimpleFunction(functionDescriptor)
    }
}