/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrSetProperty
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

class IrSetPropertyImpl(
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
        1,
        origin,
        superQualifierSymbol
    ),
    IrSetProperty {

    private var argument0: IrExpression? = null

    override fun getValueArgument(index: Int): IrExpression? =
        if (index != 0)
            throw AssertionError("$this: IrSetProperty has single value argument")
        else
            argument0

    override fun putValueArgument(index: Int, valueArgument: IrExpression?) {
        if (index != 0)
            throw AssertionError("$this: IrSetProperty has single value argument")
        else
            argument0 = valueArgument
    }

    override fun removeValueArgument(index: Int) {
        throw AssertionError("$this: IrSetProperty should have a single value argument")
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitSetProperty(this, data)
}