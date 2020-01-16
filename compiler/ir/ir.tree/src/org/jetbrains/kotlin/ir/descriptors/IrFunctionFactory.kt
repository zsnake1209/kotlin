/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.descriptors

import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrTypeParameterImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrTypeParameterSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeBuilder
import org.jetbrains.kotlin.ir.types.impl.buildSimpleType
import org.jetbrains.kotlin.ir.types.impl.buildTypeProjection
import org.jetbrains.kotlin.ir.util.IrProvider
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.explicitParameters
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance

class IrFunctionFactory(private val irBuiltIns: IrBuiltIns, private val symbolTable: SymbolTable) : IrProvider {
    override fun getDeclaration(symbol: IrSymbol): IrDeclaration? {
        return symbol.descriptor.let {
            if (it is FunctionClassDescriptor) it.createFunctionClass()
            if (it is FunctionInvokeDescriptor) (it.containingDeclaration as FunctionClassDescriptor).createFunctionClass().declarations.find { d ->
                d is IrSimpleFunction && d.isOperator
            }
            null
        }
    }

    fun functionN(n: Int): IrClass {
        val descriptor = WrappedClassDescriptor()
        val name = functionClassName(false, false, n)
        val mangle = functionClassSymbolName(name)
        val factory = FunctionDescriptorFactory.WrappedDescriptorFactory(name, symbolTable)
        return symbolTable.declareBuiltInClass(descriptor, mangle) {
            createFunctionClass(it, false, false, n, irBuiltIns.functionClass, kotlinPackageFragment, factory).also { c ->
                descriptor.bind(c)
            }
        }
    }

    fun suspendFunctionN(n: Int): IrClass {
        val descriptor = WrappedClassDescriptor()
        val name = functionClassName(false, true, n)
        val mangle = functionClassSymbolName(name)
        val factory = FunctionDescriptorFactory.WrappedDescriptorFactory(name, symbolTable)
        return symbolTable.declareBuiltInClass(descriptor, mangle) {
            createFunctionClass(it, false, true, n, irBuiltIns.functionClass, kotlinCoroutinesPackageFragment, factory).also { c ->
                descriptor.bind(c)
            }
        }
    }

    fun kFunctionN(n: Int): IrClass {
        val descriptor = WrappedClassDescriptor()
        val name = functionClassName(true, false, n)
        val mangle = functionClassSymbolName(name)
        val factory = FunctionDescriptorFactory.WrappedDescriptorFactory(name, symbolTable)
        return symbolTable.declareBuiltInClass(descriptor, mangle) {
            createFunctionClass(it, true, false, n, irBuiltIns.kFunctionClass, kotlinReflectPackageFragment, factory).also { c ->
                descriptor.bind(c)
            }
        }
    }

    fun kSuspendFunctionN(n: Int): IrClass {
        val descriptor = WrappedClassDescriptor()
        val name = functionClassName(true, true, n)
        val mangle = functionClassSymbolName(name)
        val factory = FunctionDescriptorFactory.WrappedDescriptorFactory(name, symbolTable)
        return symbolTable.declareBuiltInClass(descriptor, mangle) {
            createFunctionClass(it, true, true, n, irBuiltIns.kFunctionClass, kotlinReflectPackageFragment, factory).also { c ->
                descriptor.bind(c)
            }
        }
    }

    companion object {
        private const val FUNCTION_PREFIX = "<BUILT-IN-FUNCTION>"
        private fun functionClassSymbolName(name: String) = "ktype:${FUNCTION_PREFIX}$name"
        private fun functionInvokeSymbolName(name: String) = "kfun:${FUNCTION_PREFIX}$name.invoke"
        private fun functionMemberSymbolName(name: String, memberName: String) = "kfun:${FUNCTION_PREFIX}$name.$memberName"

        private fun functionClassName(isK: Boolean, isSuspend: Boolean, arity: Int): String =
            "${if (isK) "K" else ""}${if (isSuspend) "Suspend" else ""}Function$arity"

        private val classOrigin = object : IrDeclarationOriginImpl("FUNCTION_INTERFACE_CLASS") {}
        private val memberOrigin = object : IrDeclarationOriginImpl("FUNCTION_INTERFACE_MEMBER") {}
        private const val offset = UNDEFINED_OFFSET
    }

    private sealed class FunctionDescriptorFactory(protected val symbolTable: SymbolTable) {
        abstract fun memberDescriptor(name: String, factory: (IrSimpleFunctionSymbol) -> IrSimpleFunction): IrSimpleFunctionSymbol
        abstract fun FunctionDescriptor.valueParameterDescriptor(index: Int): ValueParameterDescriptor
        abstract fun typeParameterDescriptor(index: Int, factory: (IrTypeParameterSymbol) -> IrTypeParameter): IrTypeParameterSymbol
        abstract fun classReceiverParameterDescriptor(): ReceiverParameterDescriptor
        abstract fun FunctionDescriptor.memberReceiverParameterDescriptor(): ReceiverParameterDescriptor

        class WrappedDescriptorFactory(private val className: String, symbolTable: SymbolTable) : FunctionDescriptorFactory(symbolTable) {
            override fun memberDescriptor(name: String, factory: (IrSimpleFunctionSymbol) -> IrSimpleFunction): IrSimpleFunctionSymbol {
                val mangle = functionMemberSymbolName(className, name)
                val descriptor = WrappedSimpleFunctionDescriptor()
                return symbolTable.declareBuiltInOperator(descriptor, mangle, factory).let {
                    descriptor.bind(it)
                    it.symbol
                }
            }

            override fun classReceiverParameterDescriptor(): ReceiverParameterDescriptor = WrappedReceiverParameterDescriptor()

            override fun typeParameterDescriptor(index: Int, factory: (IrTypeParameterSymbol) -> IrTypeParameter): IrTypeParameterSymbol {
                val descriptor = WrappedTypeParameterDescriptor()
                return factory(IrTypeParameterSymbolImpl(descriptor)).let {
                    descriptor.bind(it)
                    it.symbol
                }
            }

            override fun FunctionDescriptor.valueParameterDescriptor(index: Int): ValueParameterDescriptor =
                WrappedValueParameterDescriptor()

            override fun FunctionDescriptor.memberReceiverParameterDescriptor(): ReceiverParameterDescriptor =
                WrappedReceiverParameterDescriptor()
        }

        class RealDescriptorFactory(private val classDescriptor: FunctionClassDescriptor, symbolTable: SymbolTable) :
            FunctionDescriptorFactory(symbolTable) {
            override fun memberDescriptor(name: String, factory: (IrSimpleFunctionSymbol) -> IrSimpleFunction): IrSimpleFunctionSymbol {
                val descriptor = classDescriptor.unsubstitutedMemberScope.run {
                    if (name[0] == '<') {
                        val propertyName = name.drop(5).dropLast(1)
                        val property = getContributedVariables(Name.identifier(propertyName), NoLookupLocation.FROM_BACKEND).single()
                        property.accessors.first { it.name.asString() == name }
                    } else {
                        getContributedFunctions(Name.identifier(name), NoLookupLocation.FROM_BACKEND).first()
                    }
                }
                return symbolTable.declareSimpleFunction(offset, offset, memberOrigin, descriptor, factory).symbol
            }

            override fun FunctionDescriptor.valueParameterDescriptor(index: Int): ValueParameterDescriptor {
                assert(containingDeclaration === classDescriptor)
                return valueParameters[index]
            }

            override fun typeParameterDescriptor(index: Int, factory: (IrTypeParameterSymbol) -> IrTypeParameter): IrTypeParameterSymbol {
                val descriptor = classDescriptor.declaredTypeParameters[index]
                return symbolTable.declareGlobalTypeParameter(offset, offset, classOrigin, descriptor, factory).symbol
            }

            override fun classReceiverParameterDescriptor(): ReceiverParameterDescriptor {
                return classDescriptor.thisAsReceiverParameter
            }

            override fun FunctionDescriptor.memberReceiverParameterDescriptor(): ReceiverParameterDescriptor {
                assert(containingDeclaration === classDescriptor)
                return dispatchReceiverParameter ?: error("Expected dispatch receiver at $this")
            }
        }
    }

    private fun IrTypeParametersContainer.createTypeParameters(n: Int, descriptorFactory: FunctionDescriptorFactory): IrTypeParameter {

        var index = 0

        for (i in 1 until (n + 1)) {
            val pName = Name.identifier("P$i")

            val pSymbol = descriptorFactory.typeParameterDescriptor(index) {
                IrTypeParameterImpl(offset, offset, classOrigin, it, pName, index++, false, Variance.IN_VARIANCE)
            }
            val pDeclaration = pSymbol.owner

            pDeclaration.superTypes += irBuiltIns.anyNType
            pDeclaration.parent = this
            typeParameters += pDeclaration
        }

        val rSymbol = descriptorFactory.typeParameterDescriptor(index) {
            IrTypeParameterImpl(offset, offset, classOrigin, it, Name.identifier("R"), index, false, Variance.OUT_VARIANCE)
        }
        val rDeclaration = rSymbol.owner

        rDeclaration.superTypes += irBuiltIns.anyNType
        rDeclaration.parent = this
        typeParameters += rDeclaration

        return rDeclaration
    }

    private val kotlinPackageFragment: IrPackageFragment by lazy {
        irBuiltIns.builtIns.getFunction(0).let {
            symbolTable.declareExternalPackageFragment(it.containingDeclaration as PackageFragmentDescriptor)
        }
    }
    private val kotlinCoroutinesPackageFragment: IrPackageFragment by lazy {
        irBuiltIns.builtIns.getSuspendFunction(0).let {
            symbolTable.declareExternalPackageFragment(it.containingDeclaration as PackageFragmentDescriptor)
        }
    }

    private val kotlinReflectPackageFragment: IrPackageFragment by lazy {
        irBuiltIns.kPropertyClass.descriptor.let {
            symbolTable.declareExternalPackageFragment(it.containingDeclaration as PackageFragmentDescriptor)
        }
    }

    private fun IrClass.createThisReceiver(descriptorFactory: FunctionDescriptorFactory): IrValueParameter {
        val vDescriptor = descriptorFactory.classReceiverParameterDescriptor()
        val vSymbol = IrValueParameterSymbolImpl(vDescriptor)
        val type = with(IrSimpleTypeBuilder()) {
            classifier = symbol
            arguments = typeParameters.run {
                val builder = IrSimpleTypeBuilder()
                mapTo(ArrayList(size)) {
                    builder.classifier = it.symbol
                    buildTypeProjection()
                }
            }
            buildSimpleType()
        }
        val vDeclaration = IrValueParameterImpl(
            offset, offset, classOrigin, vSymbol, Name.special("<this>"), -1, type, null,
            isCrossinline = false,
            isNoinline = false
        )

        if (vDescriptor is WrappedReceiverParameterDescriptor) vDescriptor.bind(vDeclaration)

        return vDeclaration
    }

    private fun FunctionClassDescriptor.createFunctionClass(): IrClass {
        val s = symbolTable.referenceClass(this)
        if (s.isBound) return s.owner
        return symbolTable.declareClass(offset, offset, classOrigin, this, modality) {
            val factory = FunctionDescriptorFactory.RealDescriptorFactory(this, symbolTable)
            when (functionKind) {
                FunctionClassDescriptor.Kind.Function ->
                    createFunctionClass(it, false, false, arity, irBuiltIns.functionClass, kotlinPackageFragment, factory)
                FunctionClassDescriptor.Kind.SuspendFunction ->
                    createFunctionClass(it, false, true, arity, irBuiltIns.functionClass, kotlinCoroutinesPackageFragment, factory)
                FunctionClassDescriptor.Kind.KFunction ->
                    createFunctionClass(it, true, false, arity, irBuiltIns.kFunctionClass, kotlinReflectPackageFragment, factory)
                FunctionClassDescriptor.Kind.KSuspendFunction ->
                    createFunctionClass(it, true, true, arity, irBuiltIns.kFunctionClass, kotlinReflectPackageFragment, factory)
            }
        }
    }

    private fun IrClass.createMembers(isK: Boolean, isSuspend: Boolean, arity: Int, name: String, descriptorFactory: FunctionDescriptorFactory) {
        if (!isK) {
            val invokeSymbol = descriptorFactory.memberDescriptor("invoke") {
                val returnType = with(IrSimpleTypeBuilder()) {
                    classifier = typeParameters.last().symbol
                    buildSimpleType()
                }

                IrFunctionImpl(offset, offset, memberOrigin, it, Name.identifier("invoke"), Visibilities.PUBLIC, Modality.ABSTRACT,
                    returnType,
                    isInline = false,
                    isExternal = false,
                    isTailrec = false,
                    isSuspend = isSuspend,
                    isOperator = true,
                    isExpect = false,
                    isFakeOverride = false
                )
            }

            val fDeclaration = invokeSymbol.owner

            fDeclaration.dispatchReceiverParameter = createThisReceiver(descriptorFactory).also { it.parent = fDeclaration }

            val typeBuilder = IrSimpleTypeBuilder()
            for (i in 1 until typeParameters.size) {
                val vTypeParam = typeParameters[i - 1]
                val vDescriptor = with(descriptorFactory) { invokeSymbol.descriptor.valueParameterDescriptor(i - 1) }
                val vSymbol = IrValueParameterSymbolImpl(vDescriptor)
                val vType = with(typeBuilder) {
                    classifier = vTypeParam.symbol
                    buildSimpleType()
                }
                val vDeclaration = IrValueParameterImpl(
                    offset, offset, memberOrigin, vSymbol, Name.identifier("p$i"), i - 1, vType, null,
                    isCrossinline = false,
                    isNoinline = false
                )
                vDeclaration.parent = fDeclaration
                if (vDescriptor is WrappedValueParameterDescriptor) vDescriptor.bind(vDeclaration)
                fDeclaration.valueParameters += vDeclaration
            }

            fDeclaration.parent = this
            declarations += fDeclaration
        }

        addFakeOverrides(descriptorFactory)
    }

    private fun IrClass.addFakeOverrides(descriptorFactory: FunctionDescriptorFactory) {
        fun IrDeclaration.toList() = when (this) {
            is IrSimpleFunction -> listOf(this)
            is IrProperty -> listOfNotNull(getter, setter)
            else -> emptyList()
        }

        val overriddenFunctions = declarations
            .flatMap { it.toList() }
            .flatMap { it.overriddenSymbols.map { it.owner } }
            .toSet()

        val unoverriddenSuperFunctions = superTypes
            .map { it.getClass()!! }
            .flatMap { irClass ->
                irClass.declarations
                    .flatMap { it.toList() }
                    .filter { it !in overriddenFunctions }
                    .filter { it.visibility != Visibilities.PRIVATE }
            }
            .toMutableSet()

        fun IrFunction.allParameters(): List<IrValueParameter> {
            return if (this is IrConstructor) {
                listOf(this.constructedClass.thisReceiver ?: error(this.descriptor)) + explicitParameters
            } else {
                explicitParameters
            }
        }

        // TODO: A dirty hack.
        val groupedUnoverriddenSuperFunctions = unoverriddenSuperFunctions.groupBy { it.name.asString() + it.allParameters().size }

        fun createFakeOverride(overriddenFunctions: List<IrSimpleFunction>) =
            overriddenFunctions.first().let { irFunction ->
                val function = descriptorFactory.memberDescriptor(irFunction.name.asString()) { symbol ->
                    IrFunctionImpl(
                        UNDEFINED_OFFSET,
                        UNDEFINED_OFFSET,
                        IrDeclarationOrigin.FAKE_OVERRIDE,
                        symbol,
                        irFunction.name,
                        Visibilities.INHERITED,
                        irFunction.modality,
                        irFunction.returnType,
                        isInline = irFunction.isInline,
                        isExternal = irFunction.isExternal,
                        isTailrec = irFunction.isTailrec,
                        isSuspend = irFunction.isSuspend,
                        isExpect = irFunction.isExpect,
                        isFakeOverride = true,
                        isOperator = irFunction.isOperator
                    )
                }.owner

                function.apply {
                    parent = this@addFakeOverrides
                    overriddenSymbols += overriddenFunctions.map { it.symbol }
//                    copyParameterDeclarationsFrom(irFunction)
                    copyAttributes(irFunction)
                }
            }

        val fakeOverriddenFunctions = groupedUnoverriddenSuperFunctions
            .asSequence()
            .associate { it.value.first() to createFakeOverride(it.value) }
            .toMutableMap()

        declarations += fakeOverriddenFunctions.values
    }

    private fun createFunctionClass(
        symbol: IrClassSymbol,
        isK: Boolean,
        isSuspend: Boolean,
        n: Int,
        baseClass: IrClassSymbol,
        packageFragment: IrPackageFragment,
        descriptorFactory: FunctionDescriptorFactory
    ): IrClass {
        val name = functionClassName(isK, isSuspend, n)
        val klass = IrClassImpl(
            offset, offset, classOrigin, symbol, Name.identifier(name), ClassKind.INTERFACE, Visibilities.PUBLIC, Modality.ABSTRACT,
            isCompanion = false,
            isInner = false,
            isData = false,
            isExternal = false,
            isInline = false,
            isExpect = false
        )

        val r = klass.createTypeParameters(n, descriptorFactory)

        klass.thisReceiver = klass.createThisReceiver(descriptorFactory).also { it.parent = klass }

        klass.superTypes += with(IrSimpleTypeBuilder()) {
            classifier = baseClass
            arguments = listOf(with(IrSimpleTypeBuilder()) {
                classifier = r.symbol
                buildTypeProjection()
            })
            buildSimpleType()
        }

        klass.createMembers(isK, isSuspend, n, klass.name.identifier, descriptorFactory)

        klass.parent = packageFragment
        packageFragment.declarations += klass

        return klass
    }
}