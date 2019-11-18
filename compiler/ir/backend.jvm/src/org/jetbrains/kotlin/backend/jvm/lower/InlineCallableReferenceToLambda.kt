/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlock
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.backend.jvm.ir.IrInlineReferenceLocator
import org.jetbrains.kotlin.backend.jvm.ir.createJvmIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.irArray
import org.jetbrains.kotlin.backend.jvm.ir.isLambda
import org.jetbrains.kotlin.codegen.AsmUtil.BOUND_REFERENCE_RECEIVER
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

internal val inlineCallableReferenceToLambdaPhase = makeIrFilePhase(
    ::InlineCallableReferenceToLambdaPhase,
    name = "InlineCallableReferenceToLambdaPhase",
    description = "Transform callable reference to inline lambda"
)

// This lowering transforms CR passed to inline function to lambda which would be inlined
//
//      inline fun foo(inlineParameter: (A) -> B): B {
//          return inlineParameter()
//      }
//
//      foo(::smth) -> foo { a -> smth(a) }
//
internal class InlineCallableReferenceToLambdaPhase(val context: JvmBackendContext) : FileLoweringPass,
    IrElementTransformerVoidWithContext() {

    private lateinit var inlinableReferences: MutableMap<IrCallableReference, Boolean>

    override fun lower(irFile: IrFile) {
        IrInlineReferenceLocator.scan(context, irFile).let {
            inlinableReferences = it.inlineReferences
        }
        irFile.transformChildrenVoid(this)
    }

    override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
        if (expression.origin.isLambda) return expression
        val expectingExtensionFunction = inlinableReferences[expression] ?: return expression

        return expandInlineFunctionReferenceToLambda(expression, expression.symbol.owner, expectingExtensionFunction)
    }

    override fun visitPropertyReference(expression: IrPropertyReference): IrExpression {
        val expectingExtensionFunction = inlinableReferences[expression] ?: return expression

        return if (expression.field?.owner == null) {
            // Use getter if field is absent ...
            expandInlineFunctionReferenceToLambda(expression, expression.getter!!.owner, expectingExtensionFunction)
        } else {
            // ... else use field itself
            expandInlineFieldReferenceToLambda(expression, expression.field!!.owner)
        }
    }

    private fun expandInlineFieldReferenceToLambda(expression: IrPropertyReference, field: IrField): IrExpression {
        val irBuilder = context.createJvmIrBuilder(currentScope!!.scope.scopeOwnerSymbol, expression.startOffset, expression.endOffset)
        return irBuilder.irBlock(expression, IrStatementOrigin.LAMBDA) {
            val function = buildFun {
                setSourceRange(expression)
                origin = JvmLoweredDeclarationOrigin.FUNCTION_REFERENCE_IMPL
                name = Name.identifier("stub_for_inline")
                visibility = Visibilities.LOCAL
                returnType = field.type
                isSuspend = false
            }.apply {
                val boundReceiver = expression.dispatchReceiver ?: expression.extensionReceiver

                val receiver =
                    when {
                        field.isStatic -> null
                        boundReceiver != null -> irGet(irTemporary(boundReceiver, BOUND_REFERENCE_RECEIVER))
                        else -> irGet(addValueParameter("receiver", field.parentAsClass.defaultType))
                    }

                body = this@InlineCallableReferenceToLambdaPhase.context.createIrBuilder(symbol).run {
                    irExprBody(irGetField(receiver, field))
                }
            }

            +function
            +IrFunctionReferenceImpl(
                expression.startOffset,
                expression.endOffset,
                field.type,
                function.symbol,
                typeArgumentsCount = 0,
                origin = IrStatementOrigin.LAMBDA
            ).apply {
                copyAttributes(expression)
            }
        }
    }

    private fun expandInlineFunctionReferenceToLambda(
        expression: IrCallableReference, referencedFunction: IrFunction, expectingExtensionFunction: Boolean
    ): IrExpression {
        val irBuilder = context.createJvmIrBuilder(currentScope!!.scope.scopeOwnerSymbol, expression.startOffset, expression.endOffset)
        return irBuilder.irBlock(expression, IrStatementOrigin.LAMBDA) {

            val parameterTypes = (expression.type as IrSimpleType).arguments.map { (it as IrTypeProjection).type }
            val receiverType: IrType?
            val argumentTypes: List<IrType>
            if (expectingExtensionFunction) {
                receiverType = parameterTypes[0]
                argumentTypes = parameterTypes.dropLast(1).drop(1)
            } else {
                receiverType = null
                argumentTypes = parameterTypes.dropLast(1)
            }

            val boundReceiver: Pair<IrValueParameter, IrExpression>? = expression.getArgumentsWithIr().singleOrNull()

            val function = buildFun {
                setSourceRange(expression)
                origin = JvmLoweredDeclarationOrigin.FUNCTION_REFERENCE_IMPL
                name = Name.identifier("stub_for_inlining")
                visibility = Visibilities.LOCAL
                returnType = referencedFunction.returnType
                isSuspend = false
            }.apply {
                extensionReceiverParameter = receiverType?.let {
                    buildValueParameter {
                        name = Name.identifier("receiver")
                        type = receiverType
                    }
                }
                for ((index, argumentType) in argumentTypes.withIndex()) {
                    addValueParameter {
                        name = Name.identifier("p$index")
                        type = argumentType
                    }
                }

                val allLambdaParameters = listOfNotNull(extensionReceiverParameter) + valueParameters

                body = this@InlineCallableReferenceToLambdaPhase.context.createJvmIrBuilder(
                    this.symbol,
                    expression.startOffset,
                    expression.endOffset
                ).run {
                    irExprBody(irCall(referencedFunction).apply {
                        referencedFunction.allTypeParameters.forEach {
                            putTypeArgument(it.index, expression.getTypeArgument(it.index))
                        }

                        var unboundIndex = 0
                        for (parameter in referencedFunction.explicitParameters) {
                            when {
                                boundReceiver?.first == parameter ->
                                    irGet(irTemporary(boundReceiver.second))
                                parameter.isVararg && unboundIndex < allLambdaParameters.size &&
                                        parameter.type == allLambdaParameters[unboundIndex].type ->
                                    irGet(allLambdaParameters[unboundIndex++])
                                parameter.isVararg && (unboundIndex < allLambdaParameters.size || !parameter.hasDefaultValue()) ->
                                    irArray(parameter.type) {
                                        (unboundIndex until allLambdaParameters.size).forEach { +irGet(allLambdaParameters[unboundIndex++]) }
                                    }
                                unboundIndex >= allLambdaParameters.size ->
                                    null
                                else ->
                                    irGet(allLambdaParameters[unboundIndex++])
                            }?.let { putArgument(referencedFunction, parameter, it) }
                        }
                    })
                }
            }

            +function
            +IrFunctionReferenceImpl(
                expression.startOffset,
                expression.endOffset,
                referencedFunction.returnType,
                function.symbol,
                referencedFunction.typeParameters.size,
                IrStatementOrigin.LAMBDA
            ).apply {
                copyAttributes(expression)
            }
        }
    }
}