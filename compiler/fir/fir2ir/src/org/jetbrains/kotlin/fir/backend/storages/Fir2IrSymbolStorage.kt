/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend.storages

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.fir.backend.FirSignatureComposer
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.descriptors.FirPackageFragmentDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.descriptors.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.util.IdSignature
import org.jetbrains.kotlin.ir.util.SymbolStorageSkeleton
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DescriptorWithContainerSource

class Fir2IrSymbolStorage(
    private val signaturer: FirSignatureComposer
) {

    private abstract class SymbolTableBase<F : FirDeclaration, B : IrSymbolOwner, S : IrSymbolWithOwner<B>> :
        SymbolStorageSkeleton<F, B, S>() {

        protected open fun signature(declaration: F): IdSignature? = null

        @Suppress("UNCHECKED_CAST")
        override val F.originalValue: F
            get() = this

        inline fun referenced(declaration: F, sig: IdSignature, orElse: () -> S): S {
            return get(sig) ?: run {
                val new = orElse()
                assert(unboundSymbols.add(new)) {
                    "Symbol for ${new.signature} was already referenced"
                }
                set(declaration, new)
                new
            }
        }
    }

    private open inner class FlatSymbolTable<F : FirDeclaration, B : IrSymbolOwner, S : IrSymbolWithOwner<B>> :
        SymbolTableBase<F, B, S>() {
        val descriptorToSymbol = linkedMapOf<F, S>()
        val idSigToSymbol = linkedMapOf<IdSignature, S>()

        override fun get(d: F): S? {
            return if (d !is WrappedDeclarationDescriptor<*>) {
                val sig = signature(d)
                if (sig != null) {
                    idSigToSymbol[sig]
                } else {
                    descriptorToSymbol[d]
                }
            } else {
                descriptorToSymbol[d]
            }
        }

        override fun set(d: F, s: S) {
            if (s.isPublicApi) {
                idSigToSymbol[s.signature] = s
            } else {
                descriptorToSymbol[d] = s
            }
        }

        override fun get(sig: IdSignature): S? = idSigToSymbol[sig]
    }

    private inner class EnumEntrySymbolTable : FlatSymbolTable<FirEnumEntry, IrEnumEntry, IrEnumEntrySymbol>() {
        override fun signature(declaration: FirEnumEntry): IdSignature? = null
    }

    private inner class FieldSymbolTable : FlatSymbolTable<FirField, IrField, IrFieldSymbol>() {
        override fun signature(declaration: FirField): IdSignature? = null
    }

    private inner class ScopedSymbolTable<F : FirDeclaration, B : IrSymbolOwner, S : IrSymbolWithOwner<B>>
        : SymbolTableBase<F, B, S>() {
        inner class Scope(val owner: FirDeclaration, val parent: Scope?) {
            private val descriptorToSymbol = linkedMapOf<F, S>()
            private val idSigToSymbol = linkedMapOf<IdSignature, S>()

            private fun getByDescriptor(d: F): S? {
                return descriptorToSymbol[d] ?: parent?.getByDescriptor(d)
            }

            private fun getByIdSignature(sig: IdSignature): S? {
                return idSigToSymbol[sig] ?: parent?.getByIdSignature(sig)
            }

            operator fun get(d: F): S? {
                return if (d !is WrappedDeclarationDescriptor<*>) {
                    val sig = signature(d)
                    if (sig != null) {
                        getByIdSignature(sig)
                    } else {
                        getByDescriptor(d)
                    }
                } else {
                    getByDescriptor(d)
                }
            }

            fun getLocal(d: F) = descriptorToSymbol[d]

            operator fun set(d: F, s: S) {
                if (s.isPublicApi) {
                    require(d is FirTypeParameter)
                    idSigToSymbol[s.signature] = s
                } else {
                    descriptorToSymbol[d] = s
                }
            }

            operator fun get(sig: IdSignature): S? = idSigToSymbol[sig] ?: parent?.get(sig)

            fun dumpTo(stringBuilder: StringBuilder): StringBuilder =
                stringBuilder.also {
                    it.append("owner=")
                    it.append(owner)
                    it.append("; ")
                    descriptorToSymbol.keys.joinTo(prefix = "[", postfix = "]", buffer = it)
                    it.append('\n')
                    parent?.dumpTo(it)
                }

            fun dump(): String = dumpTo(StringBuilder()).toString()
        }

        private var currentScope: Scope? = null

        override fun get(d: F): S? {
            val scope = currentScope ?: return null
            return scope[d]
        }

        override fun set(d: F, s: S) {
            val scope = currentScope ?: throw AssertionError("No active scope")
            scope[d] = s
        }

        override fun get(sig: IdSignature): S? {
            val scope = currentScope ?: return null
            return scope[sig]
        }

        inline fun declareLocal(d: F, createSymbol: () -> S, createOwner: (S) -> B): B {
            val scope = currentScope ?: throw AssertionError("No active scope")
            val symbol = scope.getLocal(d) ?: createSymbol().also { scope[d] = it }
            return createOwner(symbol)
        }

        fun introduceLocal(descriptor: F, symbol: S) {
            val scope = currentScope ?: throw AssertionError("No active scope")
            scope[descriptor]?.let {
                throw AssertionError("$descriptor is already bound to $it")
            }
            scope[descriptor] = symbol
        }

        fun enterScope(owner: FirDeclaration) {
            currentScope = Scope(owner, currentScope)
        }

        fun leaveScope(owner: FirDeclaration) {
            currentScope?.owner.let {
                assert(it == owner) { "Unexpected leaveScope: owner=$owner, currentScope.owner=$it" }
            }

            currentScope = currentScope?.parent

            if (currentScope != null && unboundSymbols.isNotEmpty()) {
                throw AssertionError("Local scope contains unbound symbols: ${unboundSymbols.joinToString { it.descriptor.toString() }}")
            }
        }

        fun dump(): String =
            currentScope?.dump() ?: "<none>"
    }

    private val externalPackageFragmentTable =
        FlatSymbolTable<FirPackageFragmentDescriptor, IrExternalPackageFragment, IrExternalPackageFragmentSymbol>()
    private val classSymbolTable = FlatSymbolTable<FirRegularClass, IrClass, IrClassSymbol>()
    private val constructorSymbolTable = FlatSymbolTable<FirConstructor, IrConstructor, IrConstructorSymbol>()
    private val enumEntrySymbolTable = EnumEntrySymbolTable()
    private val fieldSymbolTable = FieldSymbolTable()
    private val simpleFunctionSymbolTable = FlatSymbolTable<FirSimpleFunction, IrSimpleFunction, IrSimpleFunctionSymbol>()
    private val propertySymbolTable = FlatSymbolTable<FirProperty, IrProperty, IrPropertySymbol>()
    private val typeAliasSymbolTable = FlatSymbolTable<FirTypeAlias, IrTypeAlias, IrTypeAliasSymbol>()

    private val globalTypeParameterSymbolTable = FlatSymbolTable<FirTypeParameter, IrTypeParameter, IrTypeParameterSymbol>()
    private val scopedTypeParameterSymbolTable = ScopedSymbolTable<FirTypeParameter, IrTypeParameter, IrTypeParameterSymbol>()
    private val valueParameterSymbolTable = ScopedSymbolTable<FirValueParameter, IrValueParameter, IrValueParameterSymbol>()
    private val variableSymbolTable = ScopedSymbolTable<FirVariable<*>, IrVariable, IrVariableSymbol>()
    private val localDelegatedPropertySymbolTable =
        ScopedSymbolTable<FirVariable<*>, IrLocalDelegatedProperty, IrLocalDelegatedPropertySymbol>()
    private val scopedSymbolTables =
        listOf(valueParameterSymbolTable, variableSymbolTable, scopedTypeParameterSymbolTable, localDelegatedPropertySymbolTable)

    fun referenceExternalPackageFragment(descriptor: FirPackageFragmentDescriptor) =
        externalPackageFragmentTable.referenced(descriptor) { IrExternalPackageFragmentSymbolImpl(descriptor) }

    fun declareExternalPackageFragment(descriptor: FirPackageFragmentDescriptor): IrExternalPackageFragment {
        return externalPackageFragmentTable.declare(
            descriptor,
            { IrExternalPackageFragmentSymbolImpl(descriptor) },
            { IrExternalPackageFragmentImpl(it) }
        )
    }

    fun declareAnonymousInitializer(
        startOffset: Int,
        endOffset: Int,
        origin: IrDeclarationOrigin,
        descriptor: ClassDescriptor
    ): IrAnonymousInitializer =
        IrAnonymousInitializerImpl(
            startOffset, endOffset, origin,
            IrAnonymousInitializerSymbolImpl(descriptor)
        )

    private fun createClassSymbol(klass: FirRegularClass): IrClassSymbol {
        val descriptor = WrappedClassDescriptor()
        return signaturer.composeSignature(klass)?.let { IrClassPublicSymbolImpl(descriptor, it) } ?: IrClassSymbolImpl(descriptor)
    }

    fun declareClass(
        klass: FirRegularClass,
        classFactory: (IrClassSymbol) -> IrClass
    ): IrClass {
        return classSymbolTable.declare(
            klass,
            { createClassSymbol(klass) },
            classFactory
        )
    }

    fun declareClassIfNotExists(klass: FirRegularClass, classFactory: (IrClassSymbol) -> IrClass): IrClass {
        return classSymbolTable.declareIfNotExists(klass, { createClassSymbol(klass) }, classFactory)
    }

    fun declareClassFromLinker(klass: FirRegularClass, sig: IdSignature, factory: (IrClassSymbol) -> IrClass): IrClass {
        return classSymbolTable.run {
            if (sig.isPublic) {
                declare(sig, klass, { IrClassPublicSymbolImpl(WrappedClassDescriptor(), sig) }, factory)
            } else {
                declare(klass, { IrClassSymbolImpl(WrappedClassDescriptor()) }, factory)
            }
        }
    }

    fun referenceClass(klass: FirRegularClass) =
        classSymbolTable.referenced(klass) { createClassSymbol(klass) }

    fun referenceClassFromLinker(klass: FirRegularClass, sig: IdSignature): IrClassSymbol =
        classSymbolTable.run {
            if (sig.isPublic) referenced(klass, sig) { IrClassPublicSymbolImpl(WrappedClassDescriptor(), sig) }
            else referenced(klass) { IrClassSymbolImpl(WrappedClassDescriptor()) }
        }

    val unboundClasses: Set<IrClassSymbol> get() = classSymbolTable.unboundSymbols

    private fun createConstructorSymbol(constructor: FirConstructor): IrConstructorSymbol {
        val descriptor = WrappedClassConstructorDescriptor()
        return signaturer.composeSignature(constructor)?.let { IrConstructorPublicSymbolImpl(descriptor, it) } ?: IrConstructorSymbolImpl(
            descriptor
        )
    }

    fun declareConstructor(
        constructor: FirConstructor,
        constructorFactory: (IrConstructorSymbol) -> IrConstructor
    ): IrConstructor =
        constructorSymbolTable.declare(
            constructor,
            { createConstructorSymbol(constructor) },
            constructorFactory
        )

    fun declareConstructorIfNotExists(
        constructor: FirConstructor,
        constructorFactory: (IrConstructorSymbol) -> IrConstructor
    ): IrConstructor =
        constructorSymbolTable.declareIfNotExists(
            constructor,
            { createConstructorSymbol(constructor) },
            constructorFactory
        )

    fun referenceConstructor(constructor: FirConstructor) =
        constructorSymbolTable.referenced(constructor) { createConstructorSymbol(constructor) }

    fun declareConstructorFromLinker(
        constructor: FirConstructor,
        sig: IdSignature,
        constructorFactory: (IrConstructorSymbol) -> IrConstructor
    ): IrConstructor {
        return constructorSymbolTable.run {
            if (sig.isPublic) {
                declare(sig, constructor, { IrConstructorPublicSymbolImpl(WrappedClassConstructorDescriptor(), sig) }, constructorFactory)
            } else {
                declare(constructor, { IrConstructorSymbolImpl(WrappedClassConstructorDescriptor()) }, constructorFactory)
            }
        }
    }

    fun referenceConstructorFromLinker(constructor: FirConstructor, sig: IdSignature): IrConstructorSymbol =
        constructorSymbolTable.run {
            if (sig.isPublic) referenced(constructor, sig) { IrConstructorPublicSymbolImpl(WrappedClassConstructorDescriptor(), sig) }
            else referenced(constructor) { IrConstructorSymbolImpl(WrappedClassConstructorDescriptor()) }
        }

    val unboundConstructors: Set<IrConstructorSymbol> get() = constructorSymbolTable.unboundSymbols

    private fun createEnumEntrySymbol(enumEntry: FirEnumEntry): IrEnumEntrySymbol {
        val descriptor = WrappedEnumEntryDescriptor()
        return signaturer.composeEnumEntrySignature(enumEntry)?.let { IrEnumEntryPublicSymbolImpl(descriptor, it) }
            ?: IrEnumEntrySymbolImpl(descriptor)
    }

    fun declareEnumEntry(
        enumEntry: FirEnumEntry,
        factory: (IrEnumEntrySymbol) -> IrEnumEntry
    ): IrEnumEntry =
        enumEntrySymbolTable.declare(
            enumEntry,
            { createEnumEntrySymbol(enumEntry) },
            factory
        )

    fun declareEnumEntryIfNotExists(enumEntry: FirEnumEntry, factory: (IrEnumEntrySymbol) -> IrEnumEntry): IrEnumEntry {
        return enumEntrySymbolTable.declareIfNotExists(enumEntry, { createEnumEntrySymbol(enumEntry) }, factory)
    }

    fun declareEnumEntryFromLinker(
        enumEntry: FirEnumEntry,
        sig: IdSignature,
        factory: (IrEnumEntrySymbol) -> IrEnumEntry
    ): IrEnumEntry {
        return enumEntrySymbolTable.run {
            if (sig.isPublic) {
                declare(sig, enumEntry, { IrEnumEntryPublicSymbolImpl(WrappedEnumEntryDescriptor(), sig) }, factory)
            } else {
                declare(enumEntry, { IrEnumEntrySymbolImpl(WrappedEnumEntryDescriptor()) }, factory)
            }
        }
    }

    fun referenceEnumEntry(enumEntry: FirEnumEntry) =
        enumEntrySymbolTable.referenced(enumEntry) { createEnumEntrySymbol(enumEntry) }

    fun referenceEnumEntryFromLinker(enumEntry: FirEnumEntry, sig: IdSignature) =
        enumEntrySymbolTable.run {
            if (sig.isPublic) referenced(enumEntry, sig) { IrEnumEntryPublicSymbolImpl(WrappedEnumEntryDescriptor(), sig) } else
                referenced(enumEntry) { IrEnumEntrySymbolImpl(WrappedEnumEntryDescriptor()) }
        }

    val unboundEnumEntries: Set<IrEnumEntrySymbol> get() = enumEntrySymbolTable.unboundSymbols

    private fun createFieldSymbol(): IrFieldSymbol {
        return IrFieldSymbolImpl(WrappedFieldDescriptor())
    }

    fun declareField(
        field: FirField,
        fieldFactory: (IrFieldSymbol) -> IrField
    ): IrField =
        fieldSymbolTable.declare(
            field,
            { createFieldSymbol() },
            fieldFactory
        )

    fun declareFieldFromLinker(field: FirField, sig: IdSignature, factory: (IrFieldSymbol) -> IrField): IrField {
        return fieldSymbolTable.run {
            require(sig.isLocal)
            declare(field, { createFieldSymbol() }, factory)
        }
    }

    fun referenceField(field: FirField) =
        fieldSymbolTable.referenced(field) { createFieldSymbol() }

    fun referenceFieldFromLinker(field: FirField, sig: IdSignature) =
        fieldSymbolTable.run {
            require(sig.isLocal)
            referenced(field) { createFieldSymbol() }
        }

    val unboundFields: Set<IrFieldSymbol> get() = fieldSymbolTable.unboundSymbols

    private fun createPropertyDescriptor(property: FirProperty): WrappedPropertyDescriptor {
        val containerSource = property.containerSource
        return containerSource?.let { WrappedPropertyDescriptorWithContainerSource(it) } ?: WrappedPropertyDescriptor()
    }

    private fun createPropertySymbol(property: FirProperty): IrPropertySymbol {
        val descriptor = createPropertyDescriptor(property)
        return signaturer.composeSignature(property)?.let { IrPropertyPublicSymbolImpl(descriptor, it) } ?: IrPropertySymbolImpl(
            descriptor
        )
    }

    fun declareProperty(
        property: FirProperty,
        propertyFactory: (IrPropertySymbol) -> IrProperty
    ): IrProperty =
        propertySymbolTable.declare(
            property,
            { createPropertySymbol(property) },
            propertyFactory
        )

    fun declarePropertyIfNotExists(property: FirProperty, propertyFactory: (IrPropertySymbol) -> IrProperty): IrProperty =
        propertySymbolTable.declareIfNotExists(property, { createPropertySymbol(property) }, propertyFactory)

    fun declarePropertyFromLinker(property: FirProperty, sig: IdSignature, factory: (IrPropertySymbol) -> IrProperty): IrProperty {
        return propertySymbolTable.run {
            if (sig.isPublic) {
                declare(sig, property, { IrPropertyPublicSymbolImpl(createPropertyDescriptor(property), sig) }, factory)
            } else {
                declare(property, { IrPropertySymbolImpl(createPropertyDescriptor(property)) }, factory)
            }
        }
    }

    fun referenceProperty(property: FirProperty): IrPropertySymbol =
        propertySymbolTable.referenced(property) { createPropertySymbol(property) }

    fun referencePropertyFromLinker(property: FirProperty, sig: IdSignature): IrPropertySymbol =
        propertySymbolTable.run {
            if (sig.isPublic) referenced(property, sig) { IrPropertyPublicSymbolImpl(createPropertyDescriptor(property), sig) }
            else referenced(property) { IrPropertySymbolImpl(createPropertyDescriptor(property)) }
        }

    val unboundProperties: Set<IrPropertySymbol> get() = propertySymbolTable.unboundSymbols

    private fun createTypeAliasSymbol(typeAlias: FirTypeAlias): IrTypeAliasSymbol {
        val descriptor = WrappedTypeAliasDescriptor()
        return signaturer.composeSignature(typeAlias)?.let { IrTypeAliasPublicSymbolImpl(descriptor, it) } ?: IrTypeAliasSymbolImpl(
            descriptor
        )
    }

    fun referenceTypeAlias(typeAlias: FirTypeAlias): IrTypeAliasSymbol =
        typeAliasSymbolTable.referenced(typeAlias) { createTypeAliasSymbol(typeAlias) }

    fun declareTypeAliasFromLinker(
        typeAlias: FirTypeAlias,
        sig: IdSignature,
        factory: (IrTypeAliasSymbol) -> IrTypeAlias
    ): IrTypeAlias {
        return typeAliasSymbolTable.run {
            if (sig.isPublic) {
                declare(sig, typeAlias, { IrTypeAliasPublicSymbolImpl(WrappedTypeAliasDescriptor(), sig) }, factory)
            } else {
                declare(typeAlias, { IrTypeAliasSymbolImpl(WrappedTypeAliasDescriptor()) }, factory)
            }
        }
    }

    fun referenceTypeAliasFromLinker(typeAlias: FirTypeAlias, sig: IdSignature) =
        typeAliasSymbolTable.run {
            if (sig.isPublic) referenced(typeAlias, sig) { IrTypeAliasPublicSymbolImpl(WrappedTypeAliasDescriptor(), sig) } else
                referenced(typeAlias) { IrTypeAliasSymbolImpl(WrappedTypeAliasDescriptor()) }
        }

    fun declareTypeAlias(typeAlias: FirTypeAlias, factory: (IrTypeAliasSymbol) -> IrTypeAlias): IrTypeAlias =
        typeAliasSymbolTable.declare(typeAlias, { createTypeAliasSymbol(typeAlias) }, factory)

    fun declareTypeAliasIfNotExists(typeAlias: FirTypeAlias, factory: (IrTypeAliasSymbol) -> IrTypeAlias): IrTypeAlias =
        typeAliasSymbolTable.declareIfNotExists(typeAlias, { createTypeAliasSymbol(typeAlias) }, factory)

    val unboundTypeAliases: Set<IrTypeAliasSymbol> get() = typeAliasSymbolTable.unboundSymbols

    private fun createSimpleFunctionDescriptor(function: FirSimpleFunction): WrappedSimpleFunctionDescriptor {
        val containerSource = function.containerSource
        return containerSource?.let { WrappedFunctionDescriptorWithContainerSource(it) } ?: WrappedSimpleFunctionDescriptor()
    }

    private fun createSimpleFunctionSymbol(function: FirSimpleFunction): IrSimpleFunctionSymbol {
        val descriptor = createSimpleFunctionDescriptor(function)
        return signaturer.composeSignature(function)?.let { IrSimpleFunctionPublicSymbolImpl(descriptor, it) }
            ?: IrSimpleFunctionSymbolImpl(descriptor)
    }

    fun declareSimpleFunction(
        function: FirSimpleFunction,
        functionFactory: (IrSimpleFunctionSymbol) -> IrSimpleFunction
    ): IrSimpleFunction {
        return simpleFunctionSymbolTable.declare(
            function,
            { createSimpleFunctionSymbol(function) },
            functionFactory
        )
    }

    fun declareSimpleFunctionIfNotExists(
        function: FirSimpleFunction,
        functionFactory: (IrSimpleFunctionSymbol) -> IrSimpleFunction
    ): IrSimpleFunction {
        return simpleFunctionSymbolTable.declareIfNotExists(function, { createSimpleFunctionSymbol(function) }, functionFactory)
    }

    fun declareSimpleFunctionFromLinker(
        function: FirSimpleFunction,
        sig: IdSignature,
        functionFactory: (IrSimpleFunctionSymbol) -> IrSimpleFunction
    ): IrSimpleFunction {
        return simpleFunctionSymbolTable.run {
            if (sig.isPublic) {
                declare(sig, function, { IrSimpleFunctionPublicSymbolImpl(createSimpleFunctionDescriptor(function), sig) }, functionFactory)
            } else {
                declare(function, { IrSimpleFunctionSymbolImpl(createSimpleFunctionDescriptor(function)) }, functionFactory)
            }
        }
    }

    fun referenceSimpleFunction(function: FirSimpleFunction) =
        simpleFunctionSymbolTable.referenced(function) { createSimpleFunctionSymbol(function) }

    fun referenceSimpleFunctionFromLinker(function: FirSimpleFunction, sig: IdSignature): IrSimpleFunctionSymbol {
        return simpleFunctionSymbolTable.run {
            if (sig.isPublic) referenced(function, sig) { IrSimpleFunctionPublicSymbolImpl(createSimpleFunctionDescriptor(function), sig) } else
                referenced(function) { IrSimpleFunctionSymbolImpl(createSimpleFunctionDescriptor(function)) }
        }
    }

    fun referenceDeclaredFunction(function: FirSimpleFunction) =
        simpleFunctionSymbolTable.referenced(function) { throw AssertionError("Function is not declared: $function") }

    val unboundSimpleFunctions: Set<IrSimpleFunctionSymbol> get() = simpleFunctionSymbolTable.unboundSymbols

    private fun createTypeParameterSymbol(): IrTypeParameterSymbol {
        return IrTypeParameterSymbolImpl(WrappedTypeParameterDescriptor())
    }

    fun declareGlobalTypeParameter(
        typeParameter: FirTypeParameter,
        typeParameterFactory: (IrTypeParameterSymbol) -> IrTypeParameter
    ): IrTypeParameter =
        globalTypeParameterSymbolTable.declare(
            typeParameter,
            { createTypeParameterSymbol() },
            typeParameterFactory
        )

    fun declareGlobalTypeParameterFromLinker(
        typeParameter: FirTypeParameter,
        sig: IdSignature,
        typeParameterFactory: (IrTypeParameterSymbol) -> IrTypeParameter
    ): IrTypeParameter {
        require(sig.isLocal)
        return globalTypeParameterSymbolTable.declare(typeParameter, { createTypeParameterSymbol() }, typeParameterFactory)
    }

    fun declareScopedTypeParameter(
        typeParameter: FirTypeParameter,
        typeParameterFactory: (IrTypeParameterSymbol) -> IrTypeParameter
    ): IrTypeParameter =
        scopedTypeParameterSymbolTable.declare(
            typeParameter,
            { createTypeParameterSymbol() },
            typeParameterFactory
        )

    fun declareScopedTypeParameterFromLinker(
        typeParameter: FirTypeParameter,
        sig: IdSignature,
        typeParameterFactory: (IrTypeParameterSymbol) -> IrTypeParameter
    ): IrTypeParameter {
        require(sig.isLocal)
        return scopedTypeParameterSymbolTable.declare(typeParameter, { createTypeParameterSymbol() }, typeParameterFactory)
    }

    fun referenceTypeParameter(typeParameter: FirTypeParameter): IrTypeParameterSymbol =
        scopedTypeParameterSymbolTable.get(typeParameter) ?: globalTypeParameterSymbolTable.referenced(typeParameter) {
            createTypeParameterSymbol()
        }

    fun referenceTypeParameterFromLinker(typeParameter: FirTypeParameter, sig: IdSignature): IrTypeParameterSymbol {
        require(sig.isLocal)
        return scopedTypeParameterSymbolTable.get(typeParameter)
            ?: globalTypeParameterSymbolTable.referenced(typeParameter) { createTypeParameterSymbol() }
    }

    val unboundTypeParameters: Set<IrTypeParameterSymbol> get() = globalTypeParameterSymbolTable.unboundSymbols

    private fun createValueParameterSymbol(): IrValueParameterSymbol {
        return IrValueParameterSymbolImpl(WrappedValueParameterDescriptor())
    }

    fun declareValueParameter(
        parameter: FirValueParameter,
        valueParameterFactory: (IrValueParameterSymbol) -> IrValueParameter
    ): IrValueParameter =
        valueParameterSymbolTable.declareLocal(
            parameter,
            { createValueParameterSymbol() },
            valueParameterFactory
        )

    fun referenceValueParameter(parameter: FirValueParameter) =
        valueParameterSymbolTable.referenced(parameter) {
            throw AssertionError("Undefined parameter referenced: $parameter\n${valueParameterSymbolTable.dump()}")
        }

    private fun createVariableSymbol() = IrVariableSymbolImpl(WrappedVariableDescriptor())

    fun declareVariable(
        variable: FirVariable<*>,
        variableFactory: (IrVariableSymbol) -> IrVariable
    ): IrVariable =
        variableSymbolTable.declareLocal(
            variable,
            { createVariableSymbol() },
            variableFactory
        )

    fun referenceVariable(variable: FirVariable<*>) =
        variableSymbolTable.referenced(variable) { throw AssertionError("Undefined variable referenced: $variable") }

    fun enterScope(owner: FirDeclaration) {
        scopedSymbolTables.forEach { it.enterScope(owner) }
    }

    fun leaveScope(owner: FirDeclaration) {
        scopedSymbolTables.forEach { it.leaveScope(owner) }
    }

    fun referenceValue(value: FirVariable<*>): IrValueSymbol =
        when (value) {
            is FirValueParameter ->
                valueParameterSymbolTable.referenced(value) { throw AssertionError("Undefined parameter referenced: $value") }
            else ->
                variableSymbolTable.referenced(value) { throw AssertionError("Undefined variable referenced: $value") }
        }

    fun wrappedTopLevelCallableDescriptors(): Set<DescriptorWithContainerSource> {
        val result = mutableSetOf<DescriptorWithContainerSource>()
        for (simpleFunction in simpleFunctionSymbolTable.descriptorToSymbol.values) {
            val descriptor = simpleFunction.descriptor
            if (descriptor is WrappedFunctionDescriptorWithContainerSource && descriptor.owner.parent !is IrClass) {
                result.add(descriptor)
            }
        }
        for (property in propertySymbolTable.descriptorToSymbol.values) {
            val descriptor = property.descriptor
            if (descriptor is WrappedPropertyDescriptorWithContainerSource && descriptor.owner.parent !is IrClass) {
                result.add(descriptor)
            }
        }
        return result
    }

    private inline fun <F : FirDeclaration, IR : IrSymbolOwner, S : IrSymbolWithOwner<IR>> FlatSymbolTable<F, IR, S>.forEachPublicSymbolImpl(
        block: (IrSymbol) -> Unit
    ) {
        idSigToSymbol.forEach { (_, sym) ->
            assert(sym.isPublicApi)
            block(sym)
        }
    }

    fun forEachPublicSymbol(block: (IrSymbol) -> Unit) {
        classSymbolTable.forEachPublicSymbolImpl { block(it) }
        constructorSymbolTable.forEachPublicSymbolImpl { block(it) }
        simpleFunctionSymbolTable.forEachPublicSymbolImpl { block(it) }
        propertySymbolTable.forEachPublicSymbolImpl { block(it) }
        enumEntrySymbolTable.forEachPublicSymbolImpl { block(it) }
        typeAliasSymbolTable.forEachPublicSymbolImpl { block(it) }
        fieldSymbolTable.forEachPublicSymbolImpl { block(it) }
    }
}