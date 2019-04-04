/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.coroutines

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.descriptors.*
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyParameterDeclarationsFrom
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.isSuspend
import org.jetbrains.kotlin.backend.common.lower.SymbolWithIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irBlockBody
import org.jetbrains.kotlin.backend.common.serialization.fqNameSafe
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.name.Name

abstract class AbstractSuspendFunctionsLowering(val symbolTable: SymbolTable) : FileLoweringPass {

    private object STATEMENT_ORIGIN_COROUTINE_IMPL : IrStatementOriginImpl("COROUTINE_IMPL")
    private object DECLARATION_ORIGIN_COROUTINE_IMPL : IrDeclarationOriginImpl("COROUTINE_IMPL")

    protected abstract val context: CommonBackendContext
    private val builtCoroutines = mutableMapOf<IrFunction, BuiltCoroutine>()
    private val suspendLambdas = mutableMapOf<IrFunction, IrFunctionReference>()

    override fun lower(irFile: IrFile) {
        markSuspendLambdas(irFile)
        buildCoroutines(irFile)
        transformCallableReferencesToSuspendLambdas(irFile)
    }

    private fun buildCoroutines(irFile: IrFile) {
        irFile.transformDeclarationsFlat(::tryTransformSuspendFunction)
        irFile.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                declaration.acceptChildrenVoid(this)
                declaration.transformDeclarationsFlat(::tryTransformSuspendFunction)
            }
        })
    }

    private fun tryTransformSuspendFunction(element: IrElement) =
        if (element is IrSimpleFunction && element.isSuspend && element.modality != Modality.ABSTRACT)
            transformSuspendFunction(element, suspendLambdas[element])
        else null

    private fun markSuspendLambdas(irElement: IrElement) {
        irElement.acceptChildrenVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitFunctionReference(expression: IrFunctionReference) {
                expression.acceptChildrenVoid(this)

                if (expression.isSuspend) {
                    suspendLambdas[expression.symbol.owner] = expression
                }
            }
        })
    }

    private fun transformCallableReferencesToSuspendLambdas(irElement: IrElement) {
        irElement.transformChildrenVoid(object : IrElementTransformerVoid() {

            override fun visitFunctionReference(expression: IrFunctionReference): IrExpression {
                expression.transformChildrenVoid(this)

                if (!expression.isSuspend)
                    return expression
                val coroutine = builtCoroutines[expression.symbol.owner]
                    ?: throw Error("Non-local callable reference to suspend lambda: $expression")
                val constructorParameters = coroutine.coroutineConstructor.valueParameters
                val expressionArguments = expression.getArguments().map { it.second }
                assert(constructorParameters.size == expressionArguments.size) {
                    "Inconsistency between callable reference to suspend lambda and the corresponding coroutine"
                }
                val irBuilder = context.createIrBuilder(expression.symbol, expression.startOffset, expression.endOffset)
                irBuilder.run {
                    return irCall(coroutine.coroutineConstructor.symbol).apply {
                        expressionArguments.forEachIndexed { index, argument ->
                            putValueArgument(index, argument)
                        }
                    }
                }
            }
        })
    }

    private sealed class SuspendFunctionKind {
        object NO_SUSPEND_CALLS : SuspendFunctionKind()
        class DELEGATING(val delegatingCall: IrCall) : SuspendFunctionKind()
        object NEEDS_STATE_MACHINE : SuspendFunctionKind()
    }

    private fun transformSuspendFunction(irFunction: IrSimpleFunction, functionReference: IrFunctionReference?): List<IrDeclaration>? {
        val suspendFunctionKind = getSuspendFunctionKind(irFunction)
        return when (suspendFunctionKind) {
            is SuspendFunctionKind.NO_SUSPEND_CALLS -> {
                null                                                            // No suspend function calls - just an ordinary function.
            }

            is SuspendFunctionKind.DELEGATING -> {                              // Calls another suspend function at the end.
                removeReturnIfSuspendedCallAndSimplifyDelegatingCall(irFunction, suspendFunctionKind.delegatingCall)
                null                                                            // No need in state machine.
            }

            is SuspendFunctionKind.NEEDS_STATE_MACHINE -> {
                val coroutine = buildCoroutine(irFunction, functionReference)   // Coroutine implementation.
                if (irFunction in suspendLambdas)             // Suspend lambdas are called through factory method <create>,
                    listOf(coroutine)                                           // thus we can eliminate original body.
                else
                    listOf<IrDeclaration>(coroutine, irFunction)
            }
        }
    }

    private fun getSuspendFunctionKind(irFunction: IrSimpleFunction): SuspendFunctionKind {
        if (irFunction in suspendLambdas)
            return SuspendFunctionKind.NEEDS_STATE_MACHINE            // Suspend lambdas always need coroutine implementation.

        val body = irFunction.body ?: return SuspendFunctionKind.NO_SUSPEND_CALLS

        var numberOfSuspendCalls = 0
        body.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitCall(expression: IrCall) {
                expression.acceptChildrenVoid(this)
                if (expression.isSuspend)
                    ++numberOfSuspendCalls
            }
        })
        // It is important to optimize the case where there is only one suspend call and it is the last statement
        // because we don't need to build a fat coroutine class in that case.
        // This happens a lot in practise because of suspend functions with default arguments.
        // TODO: use TailRecursionCallsCollector.
        val lastStatement = (body as IrBlockBody).statements.lastOrNull()
        val lastCall = when (lastStatement) {
            is IrCall -> lastStatement
            is IrReturn -> {
                var value: IrElement = lastStatement
                /*
                 * Check if matches this pattern:
                 * block/return {
                 *     block/return {
                 *         .. suspendCall()
                 *     }
                 * }
                 */
                loop@ while (true) {
                    value = when {
                        value is IrBlock && value.statements.size == 1 -> value.statements.first()
                        value is IrReturn -> value.value
                        else -> break@loop
                    }
                }
                value as? IrCall
            }
            else -> null
        }

        val suspendCallAtEnd = lastCall != null && lastCall.isSuspend    // Suspend call.
        return when {
            numberOfSuspendCalls == 0 -> SuspendFunctionKind.NO_SUSPEND_CALLS
            numberOfSuspendCalls == 1
                    && suspendCallAtEnd -> SuspendFunctionKind.DELEGATING(lastCall!!)
            else -> SuspendFunctionKind.NEEDS_STATE_MACHINE
        }
    }

    private val symbols get() = context.ir.symbols
    private val unit get() = context.irBuiltIns.unitClass
    private val getContinuationSymbol get() = context.ir.symbols.continuationGetter
    private val continuationClassSymbol get() = getContinuationSymbol.owner.returnType.classifierOrFail as IrClassSymbol

    private fun removeReturnIfSuspendedCallAndSimplifyDelegatingCall(irFunction: IrFunction, delegatingCall: IrCall) {
        val returnValue =
            if (delegatingCall.isReturnIfSuspendedCall())
                delegatingCall.getValueArgument(0)!!
            else delegatingCall
        context.createIrBuilder(irFunction.symbol).run {
            val statements = (irFunction.body as IrBlockBody).statements
            val lastStatement = statements.last()
            assert(lastStatement == delegatingCall || lastStatement is IrReturn) { "Unexpected statement $lastStatement" }
            statements[statements.lastIndex] = irReturn(returnValue)
        }
    }

    private fun buildCoroutine(irFunction: IrSimpleFunction, functionReference: IrFunctionReference?): IrClass {
        val coroutine = CoroutineBuilder(irFunction, functionReference).build()
        builtCoroutines[irFunction] = coroutine

        if (functionReference == null) {
            val resultSetter = context.ir.symbols.coroutineImpl.getPropertySetter("result")!!
            val exceptionSetter = context.ir.symbols.coroutineImpl.getPropertySetter("exception")!!
            // It is not a lambda - replace original function with a call to constructor of the built coroutine.
            val irBuilder = context.createIrBuilder(irFunction.symbol, irFunction.startOffset, irFunction.endOffset)
            irFunction.body = irBuilder.irBlockBody(irFunction) {
                val dispatchReceiverCall = irCall(coroutine.coroutineConstructor.symbol).apply {
                    val functionParameters = irFunction.explicitParameters
                    functionParameters.forEachIndexed { index, argument ->
                        putValueArgument(index, irGet(argument))
                    }
                    putValueArgument(
                        functionParameters.size,
                        irCall(getContinuationSymbol, getContinuationSymbol.owner.returnType, listOf(irFunction.returnType))
                    )
                }

                val dispatchReceiverVar = createTmpVariable(dispatchReceiverCall, irType = dispatchReceiverCall.type)
                +irCall(resultSetter).apply {
                    dispatchReceiver = irGet(dispatchReceiverVar)
                    putValueArgument(0, irGetObject(unit))
                }
                +irCall(exceptionSetter).apply {
                    dispatchReceiver = irGet(dispatchReceiverVar)
                    putValueArgument(0, irNull())
                }
                +irReturn(irCall(coroutine.doResumeFunction.symbol).apply {
                    dispatchReceiver = irGet(dispatchReceiverVar)
                })
            }
        }

        return coroutine.coroutineClass
    }

    private class BuiltCoroutine(
        val coroutineClass: IrClass,
        val coroutineConstructor: IrConstructor,
        val doResumeFunction: IrFunction
    )

    private var coroutineId = 0

    private inner class CoroutineBuilder(val irFunction: IrFunction, val functionReference: IrFunctionReference?) {

        private val functionParameters = irFunction.explicitParameters
        private val boundFunctionParameters = functionReference?.getArgumentsWithIr()?.map { it.first }
        private val unboundFunctionParameters = boundFunctionParameters?.let { functionParameters - it }

        private lateinit var coroutineClass: IrClass
        private lateinit var coroutineClassThis: IrValueParameter
        private lateinit var argumentToPropertiesMap: Map<IrValueParameter, IrField>

        private val coroutineImplSymbol = symbols.coroutineImpl
        private val coroutineImplConstructorSymbol = coroutineImplSymbol.constructors.single()
        private val create1Function = coroutineImplSymbol.owner.simpleFunctions()
            .single { it.name.asString() == "create" && it.valueParameters.size == 1 }

        private val create1CompletionParameter = create1Function.valueParameters[0]

        private val coroutineConstructors = mutableListOf<IrConstructor>()


        fun build(): BuiltCoroutine {
            val superTypes = mutableListOf(coroutineImplSymbol.owner.defaultType)
            var suspendFunctionClass: IrClass? = null
            var functionClass: IrClass? = null
            val suspendFunctionClassTypeArguments: List<IrType>?
            val functionClassTypeArguments: List<IrType>?
            if (unboundFunctionParameters != null) {
                // Suspend lambda inherits SuspendFunction.
                val numberOfParameters = unboundFunctionParameters.size
                suspendFunctionClass = context.ir.symbols.suspendFunctionN(numberOfParameters).owner
                val unboundParameterTypes = unboundFunctionParameters.map { it.type }
                suspendFunctionClassTypeArguments = unboundParameterTypes + irFunction.returnType
                superTypes += suspendFunctionClass.typeWith(suspendFunctionClassTypeArguments)

                functionClass = context.ir.symbols.functionN(numberOfParameters + 1).owner
                val continuationType = continuationClassSymbol.typeWith(irFunction.returnType)
                functionClassTypeArguments = unboundParameterTypes + continuationType + context.irBuiltIns.anyNType
                superTypes += functionClass.typeWith(functionClassTypeArguments)

            }

            val coroutineClassDescriptor = WrappedClassDescriptor()
            val coroutineClassSymbol = symbolTable.referenceClass(coroutineClassDescriptor)

            coroutineClass = IrClassImpl(
                irFunction.startOffset,
                irFunction.endOffset,
                DECLARATION_ORIGIN_COROUTINE_IMPL,
                coroutineClassSymbol,
                "${irFunction.name}\$${coroutineId++}".synthesizedName,
                ClassKind.CLASS,
                irFunction.visibility,
                Modality.FINAL,
                isCompanion = false,
                isInner = false,
                isData = false,
                isExternal = false,
                isInline = false
            )

            coroutineClassDescriptor.bind(coroutineClass)

            coroutineClass.parent = irFunction.parent
            coroutineClass.superTypes += superTypes
            val thisType = IrSimpleTypeImpl(coroutineClassSymbol, false, emptyList(), emptyList())
            coroutineClassThis = buildValueParameter(Name.special("<this>"), -1, thisType, IrDeclarationOrigin.INSTANCE_RECEIVER)
            coroutineClass.thisReceiver = coroutineClassThis
            coroutineClassThis.parent = coroutineClass

            val overriddenMap = mutableMapOf<IrSimpleFunction, IrSimpleFunctionSymbol>()
            val constructors = mutableSetOf<IrConstructor>()
            val coroutineConstructorBuilder = createConstructorBuilder()
            coroutineConstructorBuilder.initialize()
            constructors.add(coroutineConstructorBuilder.ir)

            val doResumeFunction = coroutineImplSymbol.owner.simpleFunctions().single { it.name.asString() == "doResume" }
            val doResumeMethodBuilder = createDoResumeMethodBuilder(doResumeFunction, coroutineClass)
            doResumeMethodBuilder.initialize()
            overriddenMap += doResumeFunction to doResumeMethodBuilder.symbol

            var coroutineFactoryConstructorBuilder: SymbolWithIrBuilder<IrConstructorSymbol, IrConstructor>? = null
            var createMethodBuilder: SymbolWithIrBuilder<IrSimpleFunctionSymbol, IrSimpleFunction>? = null
            var invokeMethodBuilder: SymbolWithIrBuilder<IrSimpleFunctionSymbol, IrSimpleFunction>? = null
            if (functionReference != null) {
                // Suspend lambda - create factory methods.
                coroutineFactoryConstructorBuilder = createFactoryConstructorBuilder(boundFunctionParameters!!).also { it.initialize() }
                constructors.add(coroutineFactoryConstructorBuilder.ir)

                val createFunctionDeclaration = coroutineImplSymbol.owner.simpleFunctions()
                    .asSequence()
                    .filter { it.name == Name.identifier("create") }
                    .toList()
                    .atMostOne { it.valueParameters.size == unboundFunctionParameters!!.size + 1 }

                createMethodBuilder = createCreateMethodBuilder(
                    unboundArgs                    = unboundFunctionParameters!!,
                    superFunctionDeclaration       = createFunctionDeclaration,
                    coroutineConstructor           = coroutineConstructorBuilder.ir,
                    coroutineClass                 = coroutineClass)
                createMethodBuilder.initialize()

                if (createFunctionDeclaration != null)
                    overriddenMap += createFunctionDeclaration to createMethodBuilder.symbol

                val invokeFunctionDeclaration = functionClass!!.simpleFunctions()
                    .atMostOne { it.name == Name.identifier("invoke") }!!
                val suspendInvokeFunctionDeclaration = suspendFunctionClass!!.simpleFunctions()
                    .atMostOne { it.name == Name.identifier("invoke") }!!
                invokeMethodBuilder = createInvokeMethodBuilder(
                    suspendFunctionInvokeFunctionDeclaration = suspendInvokeFunctionDeclaration,
                    functionInvokeFunctionDeclaration = invokeFunctionDeclaration,
                    createFunction = createMethodBuilder.ir,
                    doResumeFunction = doResumeMethodBuilder.ir,
                    coroutineClass = coroutineClass
                )
            }

            coroutineClass.addChild(coroutineConstructorBuilder.ir)
            coroutineConstructors += coroutineConstructorBuilder.ir

            coroutineFactoryConstructorBuilder?.let {
                it.initialize()
                coroutineClass.addChild(it.ir)
                coroutineConstructors += it.ir
            }

            createMethodBuilder?.let {
                coroutineClass.addChild(it.ir)
            }

            invokeMethodBuilder?.let {
                it.initialize()
                coroutineClass.addChild(it.ir)
            }

            coroutineClass.addChild(doResumeMethodBuilder.ir)

            coroutineClass.setSuperSymbolsAndAddFakeOverrides(superTypes)

            initializeStateMachine(coroutineConstructors, coroutineClassThis)

            return BuiltCoroutine(
                coroutineClass = coroutineClass,
                coroutineConstructor = coroutineFactoryConstructorBuilder?.ir ?: coroutineConstructorBuilder.ir,
                doResumeFunction = doResumeMethodBuilder.ir
            )
        }

        fun IrClass.setSuperSymbolsAndAddFakeOverrides(superTypes: List<IrType>) {

            fun IrDeclaration.toList() = when (this) {
                is IrSimpleFunction -> listOf(this)
//                is IrProperty -> listOfNotNull(getter, setter)
                else -> emptyList()
            }

            val overriddenMembers = declarations.flatMap { it.toList() }.flatMap { it.overriddenSymbols.map(IrSimpleFunctionSymbol::owner) }

            val unoverriddenSuperMembers = superTypes.map { it.getClass()!! }.flatMap { irClass ->
                irClass.declarations.flatMap { it.toList() }.filter { it !in overriddenMembers }
            }

            fun createFakeOverride(irFunction: IrSimpleFunction): IrSimpleFunction {
                val descriptor = WrappedSimpleFunctionDescriptor()
                return IrFunctionImpl(
                    UNDEFINED_OFFSET,
                    UNDEFINED_OFFSET,
                    IrDeclarationOrigin.FAKE_OVERRIDE,
                    symbolTable.referenceSimpleFunction(descriptor),
                    irFunction.name,
                    irFunction.visibility,
                    Modality.FINAL,
                    irFunction.returnType,
                    irFunction.isInline,
                    irFunction.isExternal,
                    irFunction.isTailrec,
                    irFunction.isSuspend
                ).also {
                    descriptor.bind(it)
                    it.parent = this
                    it.overriddenSymbols += irFunction.symbol
                    it.copyParameterDeclarationsFrom(irFunction)
                }
            }

            for (sm in unoverriddenSuperMembers) {
                val fakeOverride = createFakeOverride(sm)
                declarations += fakeOverride
            }
        }

        private fun createConstructorBuilder() = object : SymbolWithIrBuilder<IrConstructorSymbol, IrConstructor>() {

            private val descriptor = WrappedClassConstructorDescriptor()

            override fun buildSymbol(): IrConstructorSymbol = symbolTable.referenceConstructor(descriptor)

            override fun doInitialize() {}

            override fun buildIr(): IrConstructor {
                // Save all arguments to fields.
                argumentToPropertiesMap = functionParameters.associate {
                    it to coroutineClass.addField(it.name, it.type, false)
                }

                val completion = coroutineImplConstructorSymbol.owner.valueParameters[0]

                val declaration = IrConstructorImpl(
                    irFunction.startOffset,
                    irFunction.endOffset,
                    DECLARATION_ORIGIN_COROUTINE_IMPL,
                    symbol,
                    Name.special("<init>"),
                    irFunction.visibility,
                    coroutineClass.defaultType,
                    isInline = false,
                    isExternal = false,
                    isPrimary = false
                )

                descriptor.bind(declaration)
                declaration.parent = coroutineClass

                declaration.valueParameters += functionParameters.mapIndexed { index: Int, parameter: IrValueParameter ->
                    buildValueParameter(parameter.name, index, parameter.type, parameter.origin).also { p ->
                        p.parent = declaration
                    }
                }
                declaration.valueParameters += buildValueParameter(
                    completion.name,
                    functionParameters.size,
                    completion.type,
                    completion.origin
                ).also {
                    it.parent = declaration
                }

                val irBuilder = context.createIrBuilder(symbol, irFunction.startOffset, irFunction.endOffset)

                declaration.body = irBuilder.irBlockBody {
                    val completionParameter = declaration.valueParameters.last()
                    +IrDelegatingConstructorCallImpl(
                        irFunction.startOffset, irFunction.endOffset,
                        context.irBuiltIns.unitType,
                        coroutineImplConstructorSymbol, coroutineImplConstructorSymbol.descriptor,
                        coroutineImplConstructorSymbol.owner.typeParameters.size,
                        coroutineImplConstructorSymbol.owner.valueParameters.size
                    ).apply {
                        putValueArgument(0, irGet(completionParameter))
                    }
                    +IrInstanceInitializerCallImpl(
                        irFunction.startOffset,
                        irFunction.endOffset,
                        coroutineClass.symbol,
                        context.irBuiltIns.unitType
                    )
                    functionParameters.forEachIndexed { index, parameter ->
                        +irSetField(
                            irGet(coroutineClassThis),
                            argumentToPropertiesMap.getValue(parameter),
                            irGet(declaration.valueParameters[index])
                        )
                    }
                }

                return declaration
            }
        }

        private fun createFactoryConstructorBuilder(boundParams: List<IrValueParameter>) =
            object : SymbolWithIrBuilder<IrConstructorSymbol, IrConstructor>() {

                private val descriptor = WrappedClassConstructorDescriptor()

                override fun buildSymbol() = symbolTable.referenceConstructor(descriptor)

                override fun doInitialize() {}

                override fun buildIr(): IrConstructor {

                    val declaration = IrConstructorImpl(
                        irFunction.startOffset,
                        irFunction.endOffset,
                        DECLARATION_ORIGIN_COROUTINE_IMPL,
                        symbol,
                        Name.special("<init>"),
                        irFunction.visibility,
                        coroutineClass.defaultType,
                        isInline = false,
                        isExternal = false,
                        isPrimary = false
                    )

                    descriptor.bind(declaration)
                    declaration.parent = coroutineClass

                    boundParams.mapIndexedTo(declaration.valueParameters) { i, p ->
                        buildValueParameter(p.name, i, p.type, p.origin).also { it.parent = declaration }
                    }

                    val irBuilder = context.createIrBuilder(symbol, irFunction.startOffset, irFunction.endOffset)
                    declaration.body = irBuilder.irBlockBody {
                        +IrDelegatingConstructorCallImpl(
                            irFunction.startOffset, irFunction.endOffset, context.irBuiltIns.unitType,
                            coroutineImplConstructorSymbol, coroutineImplConstructorSymbol.descriptor,
                            coroutineImplConstructorSymbol.owner.typeParameters.size,
                            coroutineImplConstructorSymbol.owner.valueParameters.size
                        ).apply {
                            putValueArgument(0, irNull()) // Completion.
                        }
                        +IrInstanceInitializerCallImpl(
                            irFunction.startOffset, irFunction.endOffset, coroutineClass.symbol,
                            context.irBuiltIns.unitType
                        )
                        // Save all arguments to fields.
                        boundParams.forEachIndexed { index, parameter ->
                            +irSetField(
                                irGet(coroutineClassThis), argumentToPropertiesMap.getValue(parameter),
                                irGet(declaration.valueParameters[index])
                            )
                        }
                    }

                    return declaration
                }
            }

        private fun createCreateMethodBuilder(
            unboundArgs: List<IrValueParameter>,
            superFunctionDeclaration: IrSimpleFunction?,
            coroutineConstructor: IrConstructor,
            coroutineClass: IrClass
        ) = object : SymbolWithIrBuilder<IrSimpleFunctionSymbol, IrSimpleFunction>() {

            private val descriptor = WrappedSimpleFunctionDescriptor()

            override fun buildSymbol() = symbolTable.referenceSimpleFunction(descriptor)

            override fun doInitialize() {}

            override fun buildIr(): IrSimpleFunction {
                val declaration = IrFunctionImpl(
                    irFunction.startOffset,
                    irFunction.endOffset,
                    DECLARATION_ORIGIN_COROUTINE_IMPL,
                    symbol,
                    Name.identifier("create"),
                    Visibilities.PROTECTED,
                    Modality.FINAL,
                    coroutineClass.defaultType,
                    isInline = false,
                    isExternal = false,
                    isTailrec = false,
                    isSuspend = false
                )

                descriptor.bind(declaration)
                declaration.parent = coroutineClass
                declaration.returnType = coroutineClass.defaultType
                declaration.dispatchReceiverParameter = coroutineClassThis.copyTo(declaration)

                unboundArgs.mapIndexedTo(declaration.valueParameters) { i, p ->
                    buildValueParameter(p.name, i, p.type, p.origin).also { it.parent = declaration }
                }

                declaration.valueParameters += buildValueParameter(
                    create1CompletionParameter.name,
                    unboundArgs.size,
                    create1CompletionParameter.type,
                    create1CompletionParameter.origin
                ).also { it.parent = declaration }


                if (superFunctionDeclaration != null) {
                    declaration.overriddenSymbols += superFunctionDeclaration.overriddenSymbols
                    declaration.overriddenSymbols += superFunctionDeclaration.symbol
                }

                val thisReceiver = declaration.dispatchReceiverParameter!!
                val irBuilder = context.createIrBuilder(symbol, irFunction.startOffset, irFunction.endOffset)
                declaration.body = irBuilder.irBlockBody(irFunction.startOffset, irFunction.endOffset) {
                    +irReturn(
                        irCall(coroutineConstructor).apply {
                            var unboundIndex = 0
                            val unboundArgsSet = unboundArgs.toSet()
                            functionParameters.map {
                                if (unboundArgsSet.contains(it))
                                    irGet(declaration.valueParameters[unboundIndex++])
                                else
                                    irGetField(irGet(thisReceiver), argumentToPropertiesMap.getValue(it))
                            }.forEachIndexed { index, argument ->
                                putValueArgument(index, argument)
                            }
                            putValueArgument(functionParameters.size, irGet(declaration.valueParameters[unboundIndex]))
                            assert(unboundIndex == declaration.valueParameters.size - 1) { "Not all arguments of <create> are used" }
                        })
                }

                return declaration
            }
        }

        private fun createInvokeMethodBuilder(
            suspendFunctionInvokeFunctionDeclaration: IrSimpleFunction,
            functionInvokeFunctionDeclaration: IrSimpleFunction,
            createFunction: IrFunction,
            doResumeFunction: IrFunction,
            coroutineClass: IrClass
        ) = object : SymbolWithIrBuilder<IrSimpleFunctionSymbol, IrSimpleFunction>() {

            private val descriptor = WrappedSimpleFunctionDescriptor()

            override fun buildSymbol() = symbolTable.referenceSimpleFunction(descriptor)

            override fun doInitialize() {}

            override fun buildIr(): IrSimpleFunction {

                val declaration = IrFunctionImpl(
                    irFunction.startOffset,
                    irFunction.endOffset,
                    DECLARATION_ORIGIN_COROUTINE_IMPL,
                    symbol,
                    Name.identifier("invoke"),
                    Visibilities.PROTECTED,
                    Modality.FINAL,
                    irFunction.returnType,
                    isInline = false,
                    isExternal = false,
                    isTailrec = false,
                    isSuspend = true
                )

                descriptor.bind(declaration)
                declaration.parent = coroutineClass
                declaration.returnType = irFunction.returnType
                declaration.dispatchReceiverParameter = coroutineClassThis.copyTo(declaration)

                declaration.overriddenSymbols += suspendFunctionInvokeFunctionDeclaration.symbol
                declaration.overriddenSymbols += functionInvokeFunctionDeclaration.symbol

                createFunction.valueParameters.dropLast(1).mapTo(declaration.valueParameters) { p ->
                    buildValueParameter(p.name, p.index, p.type, p.origin).also { it.parent = declaration }
                }

                val resultSetter = context.ir.symbols.coroutineImpl.getPropertySetter("result")!!
                val exceptionSetter = context.ir.symbols.coroutineImpl.getPropertySetter("exception")!!

                val thisReceiver = declaration.dispatchReceiverParameter!!
                val irBuilder = context.createIrBuilder(symbol, irFunction.startOffset, irFunction.endOffset)
                declaration.body = irBuilder.irBlockBody(irFunction.startOffset, irFunction.endOffset) {
                    val dispatchReceiverCall = irCall(createFunction).apply {
                        dispatchReceiver = irGet(thisReceiver)
                        declaration.valueParameters.forEachIndexed { index, parameter ->
                            putValueArgument(index, irGet(parameter))
                        }
                        putValueArgument(
                            declaration.valueParameters.size,
                            irCall(getContinuationSymbol, getContinuationSymbol.owner.returnType, listOf(declaration.returnType))
                        )
                    }
                    val dispatchReceiverVar = createTmpVariable(dispatchReceiverCall, irType = dispatchReceiverCall.type)
                    +irCall(resultSetter).apply {
                        dispatchReceiver = irGet(dispatchReceiverVar)
                        putValueArgument(0, irGetObject(unit))
                    }
                    +irCall(exceptionSetter).apply {
                        dispatchReceiver = irGet(dispatchReceiverVar)
                        putValueArgument(0, irNull())
                    }
                    +irReturn(irCall(doResumeFunction).apply {
                        dispatchReceiver = irGet(dispatchReceiverVar)
                    })
                }

                return declaration
            }
        }


        private fun createDoResumeMethodBuilder(doResumeFunction: IrSimpleFunction, coroutineClass: IrClass) =
            object : SymbolWithIrBuilder<IrSimpleFunctionSymbol, IrSimpleFunction>() {

                private val descriptor = WrappedSimpleFunctionDescriptor()

                override fun buildSymbol() = symbolTable.referenceSimpleFunction(descriptor)

                override fun doInitialize() {}

                override fun buildIr(): IrSimpleFunction {
                    val originalBody = irFunction.body!!
                    val function = IrFunctionImpl(
                        irFunction.startOffset, irFunction.endOffset, DECLARATION_ORIGIN_COROUTINE_IMPL, symbol,
                        doResumeFunction.name,
                        doResumeFunction.visibility,
                        Modality.FINAL,
                        context.irBuiltIns.anyNType,
                        doResumeFunction.isInline,
                        doResumeFunction.isExternal,
                        doResumeFunction.isTailrec,
                        doResumeFunction.isSuspend
                    )

                    descriptor.bind(function)
                    function.overriddenSymbols += doResumeFunction.symbol
                    function.parent = coroutineClass
                    function.returnType = context.irBuiltIns.anyNType
                    function.dispatchReceiverParameter = coroutineClassThis.copyTo(function)
                    doResumeFunction.valueParameters.mapTo(function.valueParameters) {
                        buildValueParameter(it.name, it.index, it.type, it.origin).also { p -> p.parent = function }
                    }

                    val body =
                        (originalBody as IrBlockBody).run {
                            IrBlockImpl(
                                irFunction.startOffset,
                                irFunction.endOffset,
                                context.irBuiltIns.unitType,
                                STATEMENT_ORIGIN_COROUTINE_IMPL,
                                statements
                            )
                        }

                    buildStateMachine(body, function, irFunction, argumentToPropertiesMap)

                    return function
                }
            }
    }


    fun IrClass.addField(name: Name, type: IrType, isMutable: Boolean): IrField {
        val descriptor = WrappedFieldDescriptor()
        val symbol = IrFieldSymbolImpl(descriptor)
        return IrFieldImpl(
            startOffset,
            endOffset,
            DECLARATION_ORIGIN_COROUTINE_IMPL,
            symbol,
            name,
            type,
            Visibilities.PRIVATE,
            !isMutable,
            isExternal = false,
            isStatic = false
        ).also {
            descriptor.bind(it)
            it.parent = this
            addChild(it)
        }
    }

    // TODO: replace it with IrBuilder some day
    private fun buildValueParameter(
        name: Name,
        index: Int,
        type: IrType,
        origin: IrDeclarationOrigin = DECLARATION_ORIGIN_COROUTINE_IMPL
    ): IrValueParameter {
        val descriptor = WrappedValueParameterDescriptor()
        return IrValueParameterImpl(
            UNDEFINED_OFFSET,
            UNDEFINED_OFFSET,
            origin,
            IrValueParameterSymbolImpl(descriptor),
            name,
            index,
            type,
            null,
            isCrossinline = false,
            isNoinline = false
        ).also {
            descriptor.bind(it)
        }
    }


    fun IrClass.simpleFunctions(): List<IrSimpleFunction> = this.declarations.flatMap {
        when (it) {
            is IrSimpleFunction -> listOf(it)
            is IrProperty -> listOfNotNull(it.getter, it.setter)
            else -> emptyList()
        }
    }

    protected abstract fun buildStateMachine(
        body: IrBlock,
        doResumeFunction: IrFunction,
        transformingFunction: IrFunction,
        argumentToPropertiesMap: Map<IrValueParameter, IrField>
    )

    protected abstract fun initializeStateMachine(coroutineConstructors: List<IrConstructor>, coroutineClassThis: IrValueDeclaration)

    private fun IrCall.isReturnIfSuspendedCall() =
        symbol.owner.run { fqNameSafe == context.internalPackageFqn.child(Name.identifier("returnIfSuspended")) }
}
