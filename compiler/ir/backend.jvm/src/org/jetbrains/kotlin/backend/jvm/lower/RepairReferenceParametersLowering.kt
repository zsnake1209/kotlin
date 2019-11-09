/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedValueParameterDescriptor
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.codegen.isExtensionFunctionType
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.name.Name

internal val repairReferenceParametersPhase = makeIrFilePhase(
    ::RepairReferenceParametersLowering,
    name = "RepairReferenceParameters",
    description = "Make sure callable references expect their parameters in the right positions"
)

// The data argument is true if the call context expects a function with receiver
private class RepairReferenceParametersLowering(val context: CommonBackendContext) : IrElementTransformerVoidWithContext(), FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid()
    }

    override fun visitFunctionAccess(expression: IrFunctionAccessExpression): IrExpression {
        val callee = expression.symbol.owner
        expression.dispatchReceiver = expression.dispatchReceiver?.wrapInLambdaIfNeeded(callee.dispatchReceiverParameter!!.type)
        expression.extensionReceiver = expression.extensionReceiver?.wrapInLambdaIfNeeded(callee.extensionReceiverParameter!!.type)
        callee.valueParameters.forEach { parameter ->
            expression.getValueArgument(parameter.index)?.let {
                expression.putValueArgument(parameter.index, it.wrapInLambdaIfNeeded(parameter.type))
            }
        }
        return super.visitFunctionAccess(expression)
    }

    private fun IrExpression.wrapInLambdaIfNeeded(expectedType: IrType): IrExpression =
        if (expectedType.isExtensionFunctionType && !type.isExtensionFunctionType)
            wrapInLambda(expectedType)
        else this

    private fun IrExpression.wrapInLambda(expectedType: IrType): IrExpression {
        require(expectedType is IrSimpleType)
        assert(type.isFunctionTypeOrSubtype())
        assert(expectedType.isExtensionFunctionType)
        val expression = this
        val functionType = type as IrSimpleType
        val descriptor = WrappedSimpleFunctionDescriptor()
        val lambda = IrFunctionImpl(
            startOffset, endOffset, IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA,
            IrSimpleFunctionSymbolImpl(descriptor),
            Name.special("<anonymous>"),
            Visibilities.LOCAL, Modality.FINAL,
            returnType = functionType.arguments.last().type,
            isInline = true,
            isExternal = false,
            isTailrec = false,
            isSuspend = type.isSuspendFunctionTypeOrSubtype(),
            isExpect = false,
            isFakeOverride = false
        ).apply {
            descriptor.bind(this)
            parent = currentScope!!.scope.scopeOwnerSymbol.owner as IrDeclarationParent
            extensionReceiverParameter = makeParameter(Name.identifier("\$receiver"), -1, expectedType.arguments[0].type)
            valueParameters.addAll((0 until expectedType.arguments.size - 2).map { i ->
                makeParameter(Name.identifier("arg$i"), i, expectedType.arguments[i + 1].type)
            })
            val invoke = (functionType.classifier.owner as IrClass).functions.single { it.name.asString() == "invoke" }
            body = context.createIrBuilder(symbol).irBlockBody {
                +irReturn(irCall(invoke).apply {
                    dispatchReceiver = expression
                    putValueArgument(0, irGet(extensionReceiverParameter!!))
                    for (parameter in valueParameters) {
                        putValueArgument(parameter.index + 1, irGet(parameter))
                    }
                })
            }
        }
        return IrFunctionExpressionImpl(
            startOffset, endOffset, expectedType, lambda, IrStatementOrigin.LAMBDA
        )
    }

    private val IrTypeArgument.type get() = (this as IrTypeProjection).type

    private fun IrSimpleFunction.makeParameter(
        name: Name,
        index: Int,
        type: IrType
    ) = IrValueParameterImpl(
        UNDEFINED_OFFSET, UNDEFINED_OFFSET, IrDeclarationOrigin.DEFINED,
        IrValueParameterSymbolImpl(WrappedValueParameterDescriptor()),
        Name.identifier("\$receiver"),
        index, type,
        varargElementType = null,
        isCrossinline = false,
        isNoinline = false
    ).also {
        (it.descriptor as WrappedValueParameterDescriptor).bind(it)
        it.parent = this
    }
}
