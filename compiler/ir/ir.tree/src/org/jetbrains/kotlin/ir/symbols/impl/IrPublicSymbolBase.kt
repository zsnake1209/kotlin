/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.symbols.impl

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.descriptors.WrappedDeclarationDescriptor
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.util.UniqId

abstract class IrPublicSymbolBase<out D : DeclarationDescriptor>(override val descriptor: D, override val uniqId: UniqId) : IrSymbol

abstract class IrBindablePublicSymbolBase<out D : DeclarationDescriptor, B : IrSymbolOwner>(
    descriptor: D, id: UniqId, private val mangle: String
) :
    IrBindableSymbol<D, B>, IrPublicSymbolBase<D>(descriptor, id) {

    init {
        assert(isOriginalDescriptor(descriptor)) {
            "Substituted descriptor $descriptor for ${descriptor.original}"
        }
    }

    private fun isOriginalDescriptor(descriptor: DeclarationDescriptor): Boolean =
        descriptor is WrappedDeclarationDescriptor<*> ||
                // TODO fix declaring/referencing value parameters: compute proper original descriptor
                descriptor is ValueParameterDescriptor && isOriginalDescriptor(descriptor.containingDeclaration) ||
                descriptor == descriptor.original

    private var _owner: B? = null
    override val owner: B
        get() = _owner ?: throw IllegalStateException("Symbol for $mangle is unbound")

    override fun bind(owner: B) {
        if (_owner == null) {
            _owner = owner
        } else {
            throw IllegalStateException("${javaClass.simpleName} for $mangle is already bound")
        }
    }

    override val isPublicApi: Boolean = true

    override val isBound: Boolean
        get() = _owner != null
}

class IrClassPublicSymbolImpl(descriptor: ClassDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<ClassDescriptor, IrClass>(descriptor, id, m),
    IrClassSymbol {
}

class IrEnumEntryPublicSymbolImpl(descriptor: ClassDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<ClassDescriptor, IrEnumEntry>(descriptor, id, m),
    IrEnumEntrySymbol {
}

class IrFieldPublicSymbolImpl(descriptor: PropertyDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<PropertyDescriptor, IrField>(descriptor, id, m),
    IrFieldSymbol {
}

class IrTypeParameterPublicSymbolImpl(descriptor: TypeParameterDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<TypeParameterDescriptor, IrTypeParameter>(descriptor, id, m),
    IrTypeParameterSymbol {
}

class IrSimpleFunctionPublicSymbolImpl(descriptor: FunctionDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<FunctionDescriptor, IrSimpleFunction>(descriptor, id, m),
    IrSimpleFunctionSymbol {
}

class IrConstructorPublicSymbolImpl(descriptor: ClassConstructorDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<ClassConstructorDescriptor, IrConstructor>(descriptor, id, m),
    IrConstructorSymbol {
}

class IrPropertyPublicSymbolImpl(descriptor: PropertyDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<PropertyDescriptor, IrProperty>(descriptor, id, m),
    IrPropertySymbol {
}

class IrTypeAliasPublicSymbolImpl(descriptor: TypeAliasDescriptor, id: UniqId, m: String) :
    IrBindablePublicSymbolBase<TypeAliasDescriptor, IrTypeAlias>(descriptor, id, m),
    IrTypeAliasSymbol {
}
