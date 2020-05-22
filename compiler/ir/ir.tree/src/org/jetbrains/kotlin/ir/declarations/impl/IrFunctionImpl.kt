/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations.impl

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.ir.declarations.IrAttributeContainer
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.carriers.FunctionCarrier
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.Name

class IrFunctionImpl(
    startOffset: Int,
    endOffset: Int,
    origin: IrDeclarationOrigin,
    override val symbol: IrSimpleFunctionSymbol,
    name: Name = symbol.initialDescriptor.name,
    visibility: Visibility = symbol.initialDescriptor.visibility,
    override val modality: Modality = symbol.initialDescriptor.modality,
    returnType: IrType,
    isInline: Boolean = symbol.initialDescriptor.isInline,
    isExternal: Boolean = symbol.initialDescriptor.isExternal,
    override val isTailrec: Boolean = symbol.initialDescriptor.isTailrec,
    override val isSuspend: Boolean = symbol.initialDescriptor.isSuspend,
    override val isOperator: Boolean = symbol.initialDescriptor.isOperator,
    isExpect: Boolean = symbol.initialDescriptor.isExpect,
    override val isFakeOverride: Boolean = origin == IrDeclarationOrigin.FAKE_OVERRIDE
) :
    IrFunctionBase<FunctionCarrier>(startOffset, endOffset, origin, name, visibility, isInline, isExternal, isExpect, returnType),
    IrSimpleFunction,
    FunctionCarrier {

    constructor(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrSimpleFunctionSymbol,
        returnType: IrType,
        visibility: Visibility = symbol.initialDescriptor.visibility,
        modality: Modality = symbol.initialDescriptor.modality
    ) : this(
        startOffset, endOffset, origin, symbol,
        symbol.initialDescriptor.name,
        visibility,
        modality,
        returnType,
        isInline = symbol.initialDescriptor.isInline,
        isExternal = symbol.initialDescriptor.isExternal,
        isTailrec = symbol.initialDescriptor.isTailrec,
        isSuspend = symbol.initialDescriptor.isSuspend,
        isExpect = symbol.initialDescriptor.isExpect,
        isFakeOverride = origin == IrDeclarationOrigin.FAKE_OVERRIDE,
        isOperator = symbol.initialDescriptor.isOperator
    )

    override val descriptor: FunctionDescriptor get() = symbol.descriptor
    override val initialDescriptor: FunctionDescriptor get() = symbol.initialDescriptor

    override var overriddenSymbolsField: List<IrSimpleFunctionSymbol> = emptyList()

    override var overriddenSymbols: List<IrSimpleFunctionSymbol>
        get() = getCarrier().overriddenSymbolsField
        set(v) {
            if (overriddenSymbols !== v) {
                setCarrier().overriddenSymbolsField = v
            }
        }

    override var attributeOwnerIdField: IrAttributeContainer = this

    override var attributeOwnerId: IrAttributeContainer
        get() = getCarrier().attributeOwnerIdField
        set(v) {
            if (attributeOwnerId !== v) {
                setCarrier().attributeOwnerIdField = v
            }
        }

    override var correspondingPropertySymbolField: IrPropertySymbol? = null

    override var correspondingPropertySymbol: IrPropertySymbol?
        get() = getCarrier().correspondingPropertySymbolField
        set(v) {
            if (correspondingPropertySymbol !== v) {
                setCarrier().correspondingPropertySymbolField = v
            }
        }

    // Used by kotlin-native in InteropLowering.kt and IrUtils2.kt
    constructor(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        descriptor: FunctionDescriptor,
        returnType: IrType
    ) : this(
        startOffset, endOffset, origin,
        IrSimpleFunctionSymbolImpl(descriptor), returnType
    )

    init {
        symbol.bind(this)
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitSimpleFunction(this, data)
}
