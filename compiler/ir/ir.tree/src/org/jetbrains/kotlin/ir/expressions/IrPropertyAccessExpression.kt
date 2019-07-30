/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.expressions

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol

interface IrPropertyAccessExpression : IrMemberAccessExpression, IrDeclarationReference {
    override val descriptor: FunctionDescriptor
    override val symbol: IrSimpleFunctionSymbol

    val superQualifier: ClassDescriptor?
    val superQualifierSymbol: IrClassSymbol?
}

interface IrGetProperty : IrPropertyAccessExpression

interface IrSetProperty : IrPropertyAccessExpression

var IrSetProperty.value: IrExpression?
    get() = getValueArgument(0)
    set(v) {
        putValueArgument(0, v)
    }

val IrPropertyAccessExpression.property
    get() = symbol.owner.correspondingPropertySymbol!!