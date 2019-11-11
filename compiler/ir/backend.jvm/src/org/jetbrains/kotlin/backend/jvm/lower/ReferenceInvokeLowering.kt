/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.BodyLoweringPass
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.util.isSubclassOf
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal val referenceInvokePhase = makeIrFilePhase(
    ::ReferenceInvokeLowering,
    "ReferenceInvoke",
    "Convert invoke() calls applied to function references to function calls",
    prerequisite = setOf(repairReferenceParametersPhase)
)

/*
    When `invoke` is applied to a functional refeence object, we can replace it by a simple call.
    In particular, such configurations arise from RepairReferenceParametersLowering.
    Conversion may be needed to perform inlining.
 */
private class ReferenceInvokeLowering(val context: CommonBackendContext) : IrElementTransformerVoid(), BodyLoweringPass {
    override fun lower(irBody: IrBody) {
        irBody.transformChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val callee = expression.symbol.owner
        if (callee.isInvoke() && expression.dispatchReceiver is IrFunctionReference) {
            val reference = expression.dispatchReceiver as IrFunctionReference
            val referenceTarget = reference.symbol.owner
            val newCall = when (referenceTarget) {
                is IrConstructor -> IrConstructorCallImpl(
                    expression.startOffset,
                    expression.endOffset,
                    expression.type,
                    referenceTarget.symbol,
                    referenceTarget.descriptor,
                    referenceTarget.typeParameters.size,
                    referenceTarget.parentAsClass.typeParameters.size,
                    referenceTarget.valueParameters.size,
                    expression.origin
                )
                is IrSimpleFunction -> IrCallImpl(
                    expression.startOffset, expression.endOffset, expression.type,
                    referenceTarget.symbol, referenceTarget.descriptor,
                    referenceTarget.typeParameters.size, referenceTarget.valueParameters.size,
                    expression.origin
                )
                else -> error("Unknown type of function reference target: ${referenceTarget}")
            }
            newCall.apply {
                val receiver = reference.dispatchReceiver ?: reference.extensionReceiver // at most one can be present
                var shift = 0
                copyTypeArgumentsFrom(reference)
                referenceTarget.dispatchReceiverParameter?.let {
                    dispatchReceiver = receiver ?: run { shift++; expression.getValueArgument(0) }
                }
                referenceTarget.extensionReceiverParameter?.let {
                    extensionReceiver = receiver ?: run { shift++; expression.getValueArgument(0) }
                }
                for (index in 0 until referenceTarget.valueParameters.size) {
                    putValueArgument(index, expression.getValueArgument(index + shift))
                }
            }
            return super.visitFunctionAccess(newCall)
        } else return super.visitCall(expression)
    }

    // No guarantee that IrClasses will be the same; have to compare by fqName
    private fun IrFunction.isInvoke(): Boolean {
        return name.asString() == "invoke" &&
                (parent as? IrClass)?.isSubclassOf(context.ir.symbols.functionN(valueParameters.size).owner) == true
    }
}