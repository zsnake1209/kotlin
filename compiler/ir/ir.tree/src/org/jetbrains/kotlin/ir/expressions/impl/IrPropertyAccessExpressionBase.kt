/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions.impl

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.expressions.IrPropertyAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType

abstract class IrPropertyAccessExpressionBase(
    startOffset: Int,
    endOffset: Int,
    override val symbol: IrSimpleFunctionSymbol,
    override val descriptor: FunctionDescriptor,
    type: IrType,
    typeArgumentsCount: Int,
    valueArgumentsCount: Int,
    origin: IrStatementOrigin? = null,
    override val superQualifierSymbol: IrClassSymbol? = null
) :
    IrMemberAccessExpressionBase(startOffset, endOffset, type, typeArgumentsCount, valueArgumentsCount, origin),
    IrPropertyAccessExpression {

    override val superQualifier: ClassDescriptor?
        get() = superQualifierSymbol?.descriptor
}

