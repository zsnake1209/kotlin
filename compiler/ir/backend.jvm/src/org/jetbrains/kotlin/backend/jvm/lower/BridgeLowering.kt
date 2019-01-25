/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.WrappedSimpleFunctionDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedValueParameterDescriptor
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.backend.common.ir.isMethodOfAny
import org.jetbrains.kotlin.backend.common.ir.isStatic
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.common.utils.isSubtypeOf
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrValueParameterImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.IrStarProjectionImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.commons.Method

class BridgeLowering(val context: JvmBackendContext) : ClassLoweringPass {

    private val state = context.state

    private val typeMapper = state.typeMapper

    override fun lower(irClass: IrClass) {
        if (irClass.isInterface || irClass.origin == JvmLoweredDeclarationOrigin.DEFAULT_IMPLS) {
            return
        }

        val functions = irClass.declarations.filterIsInstance<IrSimpleFunction>().filterNot {
            it.isStatic || it.origin == IrDeclarationOrigin.FAKE_OVERRIDE
        }

        functions.forEach {
            generateBridges(it, irClass)
        }

        //additional bridges for inherited interface methods
        if (!irClass.isInterface && irClass.origin != JvmLoweredDeclarationOrigin.DEFAULT_IMPLS) {
            for (member in irClass.declarations.filterIsInstance<IrSimpleFunction>()) {
                if (member.origin != IrDeclarationOrigin.FAKE_OVERRIDE) continue
                if (member.isMethodOfAny()) continue

                // This should run after `PropertiesLowering`, so no need to worry about properties separately.
                val implementation = member.resolveFakeOverride()
                if ((implementation != null && !implementation.parentAsClass.isInterface) ||
                    (member.modality == Modality.ABSTRACT &&
                            member.allOverridden().filter { it.origin != IrDeclarationOrigin.FAKE_OVERRIDE }.all { it.comesFromJava() })
                ) {
                    generateBridges(member, irClass)
                }
            }
        }

        // Bridges for abstract fake overrides
        irClass.declarations.filterIsInstance<IrSimpleFunction>().filter { member ->
            member.origin == IrDeclarationOrigin.FAKE_OVERRIDE &&
                    member.modality == Modality.ABSTRACT &&
                    member.getJvmName().let { memberJvmName ->
                        member.overriddenSymbols.all { it.owner.getJvmName() != memberJvmName }
                    }
        }.forEach {member ->
            irClass.declarations.add(member.orphanedCopy())
        }
    }


    sealed class BridgeTableElement {
        object AlreadyDefined : BridgeTableElement()
        class NeedsDefinition(val function: IrSimpleFunction) : BridgeTableElement()
    }

    private fun generateBridges(irFunction: IrSimpleFunction, irClass: IrClass) {
        val ourSignature = irFunction.getJvmSignature()
        val ourMethodName = ourSignature.name

        if (irFunction.origin == IrDeclarationOrigin.FAKE_OVERRIDE &&
            irFunction.overriddenSymbols.all { it.owner.modality != Modality.ABSTRACT && !it.owner.comesFromJava() }
        ) {
            // All needed bridges will be generated where functions are implemented.
            return
        }

        val (specialOverrideSignature, specialOverrideValueGenerator) =
            findSpecialWithOverride(irFunction) ?: Pair(null, null)

        var refToJavaGenerated = false
        var targetForCommonBridges = irFunction

        // Special case: fake override redirecting to an implementation with a different JVM name
        if (irFunction.origin === IrDeclarationOrigin.FAKE_OVERRIDE &&
            irFunction.modality !== Modality.ABSTRACT &&
            irFunction.visibility !== Visibilities.INVISIBLE_FAKE &&
            irFunction.overriddenInClasses().firstOrNull { it.getJvmSignature() != ourSignature || it.origin != IrDeclarationOrigin.FAKE_OVERRIDE }
                ?.let { (it.getJvmName() != ourMethodName || it.getJvmSignature() == specialOverrideSignature) && it.comesFromJava() } == true
        ) {
            val resolved = irFunction.resolveFakeOverride()!!
            val resolvedSignature = resolved.getJvmSignature()
            if (!resolvedSignature.sameCallAs(ourSignature)) {
                val bridge = createBridgeHeader(irClass, resolved, resolvedSignature, irFunction, ourSignature, isSpecial = false, isSynthetic = false)
                bridge.createBridgeBody(resolved, null, isSpecial = false, invokeStatically = true)
                irClass.declarations.add(bridge)
                refToJavaGenerated = true
                targetForCommonBridges = bridge
            }
        }

        val methodsToBridge = irFunction.getMethodsToBridge(refToJavaGenerated, specialOverrideSignature)
        loop@for ((signature, method) in methodsToBridge) {
            when (method) {
                is BridgeTableElement.AlreadyDefined -> { /* Do nothing. */ }
                is BridgeTableElement.NeedsDefinition -> {
                    if (irFunction.modality == Modality.ABSTRACT && signature.sameCallAs(ourSignature)) continue@loop

                    val defaultValueGenerator = if (signature == specialOverrideSignature) specialOverrideValueGenerator else null
                    val isSpecial = (defaultValueGenerator != null) ||
                            (signature.name != ourMethodName &&
                                    signature.argumentTypes.contentEquals(ourSignature.argumentTypes) &&
                                    signature.returnType == ourSignature.returnType)
                    val bridge = createBridgeHeader(
                        irClass,
                        irFunction,
                        ourSignature,
                        method.function,
                        signature,
                        isSpecial = isSpecial,
                        isSynthetic = !isSpecial
                    )

                    bridge.createBridgeBody(targetForCommonBridges, defaultValueGenerator, isSpecial)
                    irClass.declarations.add(bridge)
                }
            }
        }

    }

    // Cache signatures
    inner class InheritancePathItem(val function: IrSimpleFunction, val signature: Method = function.getJvmSignature())

    private fun IrSimpleFunction.getMethodsToBridge(
        refToJavaGenerated: Boolean,
        specialOverrideSignature: Method?
    ): Map<Method, BridgeTableElement> {
        val initialName = getJvmName()
        val res = mutableMapOf<Method, BridgeTableElement>()

        fun register(signature: Method, irFunction: IrSimpleFunction, inheritancePath: List<InheritancePathItem>) {
            if (res[signature] === BridgeTableElement.AlreadyDefined) return
            if (irFunction.origin === IrDeclarationOrigin.FAKE_OVERRIDE) return

            if (!irFunction.parentAsClass.isInterface &&
                irFunction.modality === Modality.FINAL
            ) {
                // Never redefine final methods.
                res[signature] = BridgeTableElement.AlreadyDefined
            } else if (inheritancePath.any {
                    !it.function.parentAsClass.isInterface &&
                            it.function !== this@getMethodsToBridge &&
                            ((it.function.origin != IrDeclarationOrigin.FAKE_OVERRIDE && it.signature != signature) ||
                                    it.signature.name != signature.name)
                }) {
                // There is already an item in `inheritancePath` where a bridge will be created.
                res[signature] = BridgeTableElement.AlreadyDefined
            } else if (!refToJavaGenerated &&
                !irFunction.parentAsClass.isInterface &&
                inheritancePath.all { it.function.origin === IrDeclarationOrigin.FAKE_OVERRIDE }
            ) {
                // Method defined here, no need to redefine
                res[signature] = BridgeTableElement.AlreadyDefined
            } else if (
                irFunction.comesFromJava() &&
                signature == specialOverrideSignature &&
                inheritancePath.last { !it.function.comesFromJava() && !it.function.parentAsClass.isInterface }.let {
                    it.function != this@getMethodsToBridge && it.signature != signature
                }
            ) {
                // Method will be defined in the previous inheritance frame.
                res[signature] = BridgeTableElement.AlreadyDefined
            } else {
                res[signature] = when (val oldValue = res[signature]) {
                    is BridgeTableElement.NeedsDefinition -> {
                        // Of all functions that implement the signature, we need to select the most general one, i.e., with argument types
                        // that will cause the least surprise from the type mapper when copied.
                        val oldFunction = oldValue.function
                        if (oldFunction.hasMoreGeneralJvmArgumentTypesThan(irFunction))
                            oldValue
                        else
                            BridgeTableElement.NeedsDefinition(irFunction)

                    }
                    else -> BridgeTableElement.NeedsDefinition(irFunction)
                }
                BridgeTableElement.NeedsDefinition(irFunction)
            }
        }

        fun handle(irFunction: IrSimpleFunction, lastJvmName: String, inheritancePath: MutableList<InheritancePathItem>) {
            val signature = irFunction.getJvmSignature()
            register(signature, irFunction, inheritancePath)
            if (signature.name != lastJvmName) {
                val newName = Name.identifier(signature.name)
                inheritancePath.forEachIndexed { i, inheritancePathItem ->
                    val renamedItem = inheritancePathItem.function.copyRenamingTo(newName)
                    register(renamedItem.getJvmSignature(), renamedItem, inheritancePath.slice(0..i))
                }
            }

            inheritancePath.add(InheritancePathItem(irFunction, signature))
            irFunction.overriddenSymbols.forEach {
                handle(it.owner, signature.name, inheritancePath)
            }
            inheritancePath.removeAt(inheritancePath.size - 1)
        }

        res[getJvmSignature()] = BridgeTableElement.AlreadyDefined
        handle(this, getJvmName(), mutableListOf())
        return res
    }

    private fun IrSimpleFunction.copyRenamingTo(newName: Name): IrSimpleFunction =
        WrappedSimpleFunctionDescriptor(descriptor.annotations).let { newDescriptor ->
            IrFunctionImpl(
                startOffset, endOffset, origin,
                IrSimpleFunctionSymbolImpl(newDescriptor),
                newName,
                visibility, modality, returnType,
                isInline, isExternal, isTailrec, isSuspend
            ).apply {
                newDescriptor.bind(this)
                parent = this@copyRenamingTo.parent
                dispatchReceiverParameter = this@copyRenamingTo.dispatchReceiverParameter?.copyTo(this)
                extensionReceiverParameter = this@copyRenamingTo.extensionReceiverParameter?.copyTo(this)
                valueParameters.addAll(this@copyRenamingTo.valueParameters.map { it.copyTo(this) })
            }
        }

    private fun createBridgeHeader(
        irClass: IrClass,
        target: IrSimpleFunction,
        targetSignature: Method,
        interfaceFunction: IrSimpleFunction,
        interfaceSignature: Method,
        isSpecial: Boolean,
        isSynthetic: Boolean
    ): IrSimpleFunction {
        val modality = if (isSpecial) Modality.FINAL else Modality.OPEN
        val origin = if (isSynthetic) IrDeclarationOrigin.BRIDGE else IrDeclarationOrigin.BRIDGE_SPECIAL

        val visibility = if (interfaceFunction.visibility === Visibilities.INTERNAL) Visibilities.PUBLIC else interfaceFunction.visibility
        val descriptor = WrappedSimpleFunctionDescriptor()
        return IrFunctionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            origin,
            IrSimpleFunctionSymbolImpl(descriptor),
            Name.identifier(interfaceFunction.getJvmName()),
            visibility,
            modality,
            returnType = interfaceFunction.returnType.eraseTypeParameters(),
            isInline = false,
            isExternal = false,
            isTailrec = false,
            isSuspend = interfaceFunction.isSuspend
        ).apply {
            descriptor.bind(this)
            parent = irClass
            dispatchReceiverParameter = target.dispatchReceiverParameter?.copyTo(this)
            extensionReceiverParameter = interfaceFunction.extensionReceiverParameter?.copyWithTypeErasure(this)
            interfaceFunction.valueParameters.mapIndexed { i, param ->
                valueParameters.add(i, param.copyWithTypeErasure(this))
            }
        }
    }

    private fun IrSimpleFunction.createBridgeBody(
        target: IrSimpleFunction,
        defaultValueGenerator: ((IrSimpleFunction) -> IrExpression)?,
        isSpecial: Boolean,
        invokeStatically: Boolean = false
    ) {
        val maybeOrphanedTarget = if (isSpecial)
            target.orphanedCopy()
        else
            target

        context.createIrBuilder(symbol).run {
            body = irBlockBody {
                if (defaultValueGenerator != null) {
                    valueParameters.forEach {
                        +irIfThen(
                            context.irBuiltIns.unitType,
                            irNot(irIs(irGet(it), maybeOrphanedTarget.valueParameters[it.index].type)),
                            irReturn(defaultValueGenerator(this@createBridgeBody))
                        )
                    }
                }
                +irReturn(
                    irImplicitCast(
                        IrCallImpl(
                            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                            maybeOrphanedTarget.returnType,
                            maybeOrphanedTarget.symbol, maybeOrphanedTarget.descriptor,
                            origin = IrStatementOrigin.BRIDGE_DELEGATION,
                            superQualifierSymbol = if (invokeStatically) maybeOrphanedTarget.parentAsClass.symbol else null
                        ).apply {
                            dispatchReceiver = irImplicitCast(irGet(dispatchReceiverParameter!!), dispatchReceiverParameter!!.type)
                            extensionReceiverParameter?.let {
                                extensionReceiver = irImplicitCast(irGet(it), extensionReceiverParameter!!.type)
                            }
                            valueParameters.forEach {
                                putValueArgument(it.index, irImplicitCast(irGet(it), maybeOrphanedTarget.valueParameters[it.index].type))
                            }
                        },
                        returnType
                    )
                )
            }
        }
    }

    /* A hacky way to make sure the code generator calls the right function, and not some standard interface it implements. */
    private fun IrSimpleFunction.orphanedCopy() =
        if (overriddenSymbols.size == 0)
            this
        else
            WrappedSimpleFunctionDescriptor(descriptor.annotations).let { wrappedDescriptor ->
                val newOrigin = if (origin == IrDeclarationOrigin.FAKE_OVERRIDE) IrDeclarationOrigin.DEFINED else origin
                IrFunctionImpl(
                    startOffset, endOffset, newOrigin,
                    IrSimpleFunctionSymbolImpl(wrappedDescriptor),
                    Name.identifier(getJvmName()),
                    visibility, modality, returnType,
                    isInline, isExternal, isTailrec, isSuspend
                ).apply {
                    wrappedDescriptor.bind(this)
                    parent = this@orphanedCopy.parent
                    copyTypeParametersFrom(this@orphanedCopy)
                    this@orphanedCopy.dispatchReceiverParameter?.let { dispatchReceiverParameter = it.copyTo(this) }
                    this@orphanedCopy.extensionReceiverParameter?.let { extensionReceiverParameter = it.copyTo(this) }
                    this@orphanedCopy.valueParameters.forEachIndexed { index, param ->
                        valueParameters.add(index, param.copyTo(this))
                    }
                    /* Do NOT copy overriddenSymbols */
                }
            }

    private fun IrValueParameter.copyWithTypeErasure(target: IrSimpleFunction): IrValueParameter {
        val descriptor = WrappedValueParameterDescriptor(this.descriptor.annotations)
        return IrValueParameterImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            IrDeclarationOrigin.BRIDGE,
            IrValueParameterSymbolImpl(descriptor),
            name,
            index,
            type.eraseTypeParameters(),
            varargElementType?.eraseTypeParameters(),
            isCrossinline,
            isNoinline
        ).apply {
            descriptor.bind(this)
            parent = target
        }
    }

    /* Perform type erasure as much as is significant for JVM signature generation. */
    private fun IrType.eraseTypeParameters() = when (this) {
        is IrErrorType -> this
        is IrSimpleType -> {
            val owner = classifier.owner
            when (owner) {
                is IrClass -> this
                is IrTypeParameter -> {
                    val upperBound = owner.upperBoundClass()
                    IrSimpleTypeImpl(
                        upperBound.symbol,
                        hasQuestionMark,
                        List(upperBound.typeParameters.size) { IrStarProjectionImpl },    // Should not affect JVM signature, but may result in an invalid type object
                        owner.annotations
                    )
                }
                else -> error("Unknown IrSimpleType classifier kind: $owner")
            }
        }
        else -> error("Unknown IrType kind: $this")
    }

    private fun IrTypeParameter.upperBoundClass(): IrClass {
        val simpleSuperClassifiers = superTypes.asSequence().filterIsInstance<IrSimpleType>().map { it.classifier }
        return simpleSuperClassifiers
            .filterIsInstance<IrClassSymbol>()
            .let {
                it.firstOrNull { !it.owner.isInterface } ?: it.firstOrNull()
            }?.owner ?: simpleSuperClassifiers.filterIsInstance<IrTypeParameterSymbol>()
            .map { it.owner.upperBoundClass() }.firstOrNull() ?: context.irBuiltIns.anyClass.owner
    }

    private data class SpecialMethodDescription(val fqClass: String, val arity: Int)

    private fun IrSimpleFunction.fqName() =
        "${getPackageFragment()?.fqName?.asString()}.${parentAsClass.name.asString()}.${name.asString()}"

    private fun IrSimpleFunction.toDescription() = SpecialMethodDescription(fqName(), valueParameters.size)

    private fun constFalse(bridge: IrSimpleFunction) =
        IrConstImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.booleanType, IrConstKind.Boolean, false)

    private fun constNull(bridge: IrSimpleFunction) =
        IrConstImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.anyNType, IrConstKind.Null, null)

    private fun constMinusOne(bridge: IrSimpleFunction) =
        IrConstImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.intType, IrConstKind.Int, -1)

    private fun getSecondArg(bridge: IrSimpleFunction) =
        IrGetValueImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, bridge.valueParameters[1].symbol)

    private val specialMethodWithDefaultsMap = mapOf<SpecialMethodDescription, (IrSimpleFunction) -> IrExpression>(
        SpecialMethodDescription("kotlin.collections.Collection.contains", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.MutableCollection.remove", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.Map.containsKey", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.Map.containsValue", 1) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.MutableMap.remove", 2) to ::constFalse,
        SpecialMethodDescription("kotlin.collections.Map.getOrDefault", 1) to ::getSecondArg,
        SpecialMethodDescription("kotlin.collections.Map.get", 1) to ::constNull,
        SpecialMethodDescription("kotlin.collections.MutableMap.remove", 1) to ::constNull,
        SpecialMethodDescription("kotlin.collections.List.indexOf", 1) to ::constMinusOne,
        SpecialMethodDescription("kotlin.collections.List.lastIndexOf", 1) to ::constMinusOne
    )

    private fun findSpecialWithOverride(irFunction: IrSimpleFunction): Pair<Method, (IrSimpleFunction) -> IrExpression>? {
        irFunction.allOverridden().forEach { overridden ->
            val description = overridden.toDescription()
            specialMethodWithDefaultsMap[description]?.let {
                return Pair(overridden.getJvmSignature(), it)
            }
        }
        return null
//        val alreadyVisited = mutableSetOf<IrSimpleFunction>()
//        fun search(irFunction: IrSimpleFunction): Pair<Method, (IrSimpleFunction) -> IrExpression>? {
//            if (irFunction in alreadyVisited) return null
//            alreadyVisited.add(irFunction)
//            return irFunction.overriddenSymbols.asSequence().mapNotNull { search(it.owner) }.firstOrNull()
//        }
//        return search(irFunction)
    }

    private val specialGettersMap = mapOf(
        "name" to "java.lang.Enum",
        "ordinal" to "java.lang.Enum",
        "size" to "kotlin.collections.Collection",
        "length" to "kotlin.collection.CharSequence",
        "keySet" to "kotlin.collections.Map",
        "values" to "kotlin.collections.Map",
        "entrySet" to "kotlin.collections.Map"
//        SpecialMethodDescription("java.lang.Enum.name", 0),
//        SpecialMethodDescription("java.lang.Enum.ordinal", 0),
//        SpecialMethodDescription("kotlin.collections.Collection.size", 0),
//        SpecialMethodDescription("kotlin.collections.Map.size", 0),
//        SpecialMethodDescription("kotlin.collections.CharSequence.length", 0),
//        SpecialMethodDescription("kotlin.collections.Map.keySet", 0),
//        SpecialMethodDescription("kotlin.collections.Map.values", 0),
//        SpecialMethodDescription("kotlin.collections.Map.entrySet", 0)
    )

    private fun isSpecialGetter(irFunction: IrSimpleFunction): Boolean {
        if (irFunction.valueParameters.isNotEmpty()) return false
        specialGettersMap[irFunction.getJvmName()]?.let { classFqName ->
            return irFunction.parentAsClass.isSubclassOf { it.fqName == classFqName}
        } ?: return false
    }


    private val BUILTIN_FQ_NAME_PREFIXES = listOf("kotlin.", "java.lang.")

    private fun IrSimpleFunction.directlyOverridesBuiltin(): Boolean =
        allOverridden().any {
            it.getJvmSignature() == getJvmSignature() &&
            it.getPackageFragment()?.fqName?.asString()?.let { fqName ->
                BUILTIN_FQ_NAME_PREFIXES.any { prefix -> fqName.startsWith(prefix) }
            } == true
        }

    private fun IrFunction.getJvmName() = getJvmSignature().name
    private fun IrFunction.getJvmSignature() = typeMapper.mapAsmMethod(descriptor)

    private fun IrSimpleFunction.hasMoreGeneralJvmArgumentTypesThan(other: IrSimpleFunction): Boolean {
        assert(valueParameters.size == other.valueParameters.size)
        return valueParameters.zip(other.valueParameters).all { (ours, others) ->
            val ourType = ours.type.eraseTypeParameters().withHasQuestionMark(true)
            val othersType = others.type.eraseTypeParameters().withHasQuestionMark(true)
            othersType == ourType || othersType.isSubtypeOf(ourType)
        }
    }
}

val IrClass.fqName get() = "${getPackageFragment()?.fqName?.asString()}.${name.asString()}"

fun IrClass.isSubclassOf(pred: (IrClass) -> Boolean): Boolean {
    val alreadyVisited = mutableSetOf<IrClass>()
    var checkType: ((IrType) -> Boolean)? = null // get mutual recursion between local functions

    fun checkClass(irClass: IrClass) = when {
        irClass in alreadyVisited -> false
        pred(irClass) -> true
        else -> {
            alreadyVisited.add(irClass)
            irClass.superTypes.any { checkType!!(it) }
        }
    }

    checkType = fun(irType: IrType): Boolean =
        irType is IrSimpleType && when (val owner = irType.classifier.owner) {
            is IrClass -> checkClass(owner)
            is IrTypeParameter -> owner.superTypes.any { checkType!!(it) }
            else -> false
        }

    return checkClass(this)
}

fun IrSimpleFunction.allOverridden(): Sequence<IrSimpleFunction> {
    val visited = mutableSetOf<IrSimpleFunction>()

    fun IrSimpleFunction.search(): Sequence<IrSimpleFunction> {
        if (this in visited) return emptySequence()
        return sequence {
            yield(this@search)
            visited.add(this@search)
            overriddenSymbols.forEach { yieldAll(it.owner.search()) }
        }
    }

    return search()
}

fun IrSimpleFunction.overriddenInClasses(): List<IrSimpleFunction> {
    val result = mutableListOf<IrSimpleFunction>()
    var current = this
    while (true) {
        result.add(current)
        current = current.overriddenSymbols.firstOrNull { !it.owner.parentAsClass.isInterface }?.owner ?: return result
    }
}

val ORIGINS_FROM_JAVA = setOf(IrDeclarationOrigin.IR_EXTERNAL_JAVA_DECLARATION_STUB, IrDeclarationOrigin.IR_EXTERNAL_DECLARATION_STUB)
fun IrDeclaration.comesFromJava() = parentAsClass.origin in ORIGINS_FROM_JAVA

// Method has the same name, same arguments as `other`. Return types may differ.
fun Method.sameCallAs(other: Method) =
    name == other.name &&
            argumentTypes?.contentEquals(other.argumentTypes) == true
