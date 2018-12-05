/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.declarations.lazy

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.DeclarationStubGenerator
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.descriptorUtil.isEffectivelyExternal
import org.jetbrains.kotlin.resolve.hasBackingField

class IrLazyProperty(
    startOffset: Int,
    endOffset: Int,
    origin: IrDeclarationOrigin,
    override val descriptor: PropertyDescriptor,
    private val stubGenerator: DeclarationStubGenerator,
    typeTranslator: TypeTranslator,
    override val name: Name = descriptor.name,
    override val visibility: Visibility = descriptor.visibility,
    override val modality: Modality = descriptor.modality,
    override val isVar: Boolean = descriptor.isVar,
    override val isConst: Boolean = descriptor.isConst,
    override val isLateinit: Boolean = descriptor.isLateInit,
    override val isDelegated: Boolean = descriptor.isDelegated,
    override val isExternal: Boolean = descriptor.isEffectivelyExternal(),
    private val bindingContext: BindingContext? = null
) :
    IrLazyDeclarationBase(startOffset, endOffset, origin, stubGenerator, typeTranslator),
    IrProperty {

    override var backingField: IrField? by lazyVar {
        if (descriptor.hasBackingField(bindingContext)) {
            stubGenerator.generateFieldStub(descriptor, bindingContext).apply {
                correspondingProperty = this@IrLazyProperty
            }
        } else null
    }
    override var getter: IrSimpleFunction? by lazyVar {
        descriptor.getter?.let { stubGenerator.generateFunctionStub(it, createPropertyIfNeeded = false) }?.apply {
            correspondingProperty = this@IrLazyProperty
        }
    }
    override var setter: IrSimpleFunction? by lazyVar {
        descriptor.setter?.let { stubGenerator.generateFunctionStub(it, createPropertyIfNeeded = false) }?.apply {
            correspondingProperty = this@IrLazyProperty
        }
    }

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitProperty(this, data)

    override fun <D> acceptChildren(visitor: IrElementVisitor<Unit, D>, data: D) {
        backingField?.accept(visitor, data)
        getter?.accept(visitor, data)
        setter?.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: IrElementTransformer<D>, data: D) {
        backingField = backingField?.transform(transformer, data) as? IrField
        getter = getter?.run { transform(transformer, data) as IrSimpleFunction }
        setter = setter?.run { transform(transformer, data) as IrSimpleFunction }
    }
}