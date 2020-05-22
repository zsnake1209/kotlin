/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.ir.declarations.impl

import org.jetbrains.kotlin.descriptors.ParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.impl.carriers.ValueParameterCarrier
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class IrValueParameterImpl(
    startOffset: Int,
    endOffset: Int,
    origin: IrDeclarationOrigin,
    override val symbol: IrValueParameterSymbol,
    override val name: Name = symbol.initialDescriptor.name,
    override val index: Int = symbol.initialDescriptor.safeAs<ValueParameterDescriptor>()?.index ?: -1,
    override val type: IrType,
    override val varargElementType: IrType?,
    override val isCrossinline: Boolean = symbol.initialDescriptor.safeAs<ValueParameterDescriptor>()?.isCrossinline ?: false,
    override val isNoinline: Boolean = symbol.initialDescriptor.safeAs<ValueParameterDescriptor>()?.isNoinline ?: false
) :
    IrDeclarationBase<ValueParameterCarrier>(startOffset, endOffset, origin),
    IrValueParameter,
    ValueParameterCarrier {

    constructor(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        symbol: IrValueParameterSymbol,
        type: IrType,
        varargElementType: IrType?
    ) :
            this(
                startOffset, endOffset, origin,
                symbol,
                symbol.initialDescriptor.name,
                symbol.initialDescriptor.safeAs<ValueParameterDescriptor>()?.index ?: -1,
                type,
                varargElementType,
                symbol.initialDescriptor.safeAs<ValueParameterDescriptor>()?.isCrossinline ?: false,
                symbol.initialDescriptor.safeAs<ValueParameterDescriptor>()?.isNoinline ?: false
            )

    constructor(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        descriptor: ParameterDescriptor,
        type: IrType,
        varargElementType: IrType?
    ) :
            this(startOffset, endOffset, origin, IrValueParameterSymbolImpl(descriptor), type, varargElementType)

    override val descriptor: ParameterDescriptor get() = symbol.descriptor
    override val initialDescriptor: ParameterDescriptor get() = symbol.initialDescriptor

    init {
        symbol.bind(this)
    }

    override var defaultValueField: IrExpressionBody? = null

    override var defaultValue: IrExpressionBody?
        get() = getCarrier().defaultValueField
        set(v) {
            if (defaultValue !== v) {
                if (v is IrBodyBase<*>) {
                    v.container = this
                }
                setCarrier().defaultValueField = v
            }
        }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitValueParameter(this, data)

    override fun <D> transform(transformer: IrElementTransformer<D>, data: D): IrValueParameter =
        transformer.visitValueParameter(this, data) as IrValueParameter

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        defaultValue?.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        defaultValue = defaultValue?.transform(transformer, data)
    }
}
