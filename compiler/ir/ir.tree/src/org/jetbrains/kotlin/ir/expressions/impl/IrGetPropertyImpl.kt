/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetProperty
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

class IrGetPropertyImpl(
    startOffset: Int,
    endOffset: Int,
    type: IrType,
    symbol: IrSimpleFunctionSymbol,
    descriptor: FunctionDescriptor,
    typeArgumentsCount: Int,
    origin: IrStatementOrigin? = null,
    superQualifierSymbol: IrClassSymbol? = null
) :
    IrPropertyAccessExpressionBase(
        startOffset, endOffset,
        symbol, descriptor,
        type,
        typeArgumentsCount,
        0,
        origin,
        superQualifierSymbol
    ),
    IrGetProperty {

    override fun getValueArgument(index: Int): IrExpression? =
        throw AssertionError("$this: IrGetProperty has no value arguments")

    override fun putValueArgument(index: Int, valueArgument: IrExpression?) {
        throw AssertionError("$this: IrGetProperty has no value arguments")
    }

    override fun removeValueArgument(index: Int) {
        throw AssertionError("$this: IrGetProperty has no value arguments")
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitGetProperty(this, data)
}