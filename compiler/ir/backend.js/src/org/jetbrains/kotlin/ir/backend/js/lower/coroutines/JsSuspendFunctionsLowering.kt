/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.coroutines

import org.jetbrains.kotlin.backend.common.ir.isSuspend
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrFieldSymbol
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.util.explicitParameters
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.DFS

class JsSuspendFunctionsLowering(ctx: JsIrBackendContext) : AbstractSuspendFunctionsLowering(ctx) {

    private val coroutineImplExceptionPropertyGetter = context.coroutineImplExceptionPropertyGetter
    private val coroutineImplExceptionPropertySetter = context.coroutineImplExceptionPropertySetter
    private val coroutineImplExceptionStatePropertyGetter = context.coroutineImplExceptionStatePropertyGetter
    private val coroutineImplExceptionStatePropertySetter = context.coroutineImplExceptionStatePropertySetter
    private val coroutineImplLabelPropertySetter = context.coroutineImplLabelPropertySetter
    private val coroutineImplLabelPropertyGetter = context.coroutineImplLabelPropertyGetter
    private val coroutineImplResultSymbolGetter = context.coroutineImplResultSymbolGetter

    private var exceptionTrapId = -1

    override fun buildStateMachine(
        body: IrBlock,
        doResumeFunction: IrFunction,
        transformingFunction: IrFunction,
        argumentToPropertiesMap: Map<IrValueParameter, IrField>
    ) {

        val coroutineClass = doResumeFunction.parent as IrClass
        val suspendResult = JsIrBuilder.buildVar(
            context.irBuiltIns.anyNType,
            doResumeFunction,
            "suspendResult",
            true,
            initializer = JsIrBuilder.buildCall(coroutineImplResultSymbolGetter.symbol).apply {
                dispatchReceiver = JsIrBuilder.buildGetValue(doResumeFunction.dispatchReceiverParameter!!.symbol)
            }
        )

        val suspendState = JsIrBuilder.buildVar(coroutineImplLabelPropertyGetter.returnType, doResumeFunction, "suspendState", true)

        val unit = context.irBuiltIns.unitType

        val switch = IrWhenImpl(body.startOffset, body.endOffset, unit, COROUTINE_SWITCH)
        val rootTry = IrTryImpl(body.startOffset, body.endOffset, unit).apply { tryResult = switch }
        val rootLoop = IrDoWhileLoopImpl(
            body.startOffset,
            body.endOffset,
            unit,
            COROUTINE_ROOT_LOOP,
            rootTry,
            JsIrBuilder.buildBoolean(context.irBuiltIns.booleanType, true)
        )

        val suspendableNodes = mutableSetOf<IrElement>()
        val loweredBody =
            collectSuspendableNodes(body, suspendableNodes, context, doResumeFunction)
        val thisReceiver = (doResumeFunction.dispatchReceiverParameter as IrValueParameter).symbol

        val stateMachineBuilder = StateMachineBuilder(
            suspendableNodes,
            context,
            doResumeFunction.symbol,
            rootLoop,
            coroutineImplExceptionPropertyGetter,
            coroutineImplExceptionPropertySetter,
            coroutineImplExceptionStatePropertyGetter,
            coroutineImplExceptionStatePropertySetter,
            coroutineImplLabelPropertySetter,
            thisReceiver,
            suspendResult.symbol
        )

        loweredBody.acceptVoid(stateMachineBuilder)

        stateMachineBuilder.finalizeStateMachine()

        rootTry.catches += stateMachineBuilder.globalCatch

        val visited = mutableSetOf<SuspendState>()

        val sortedStates = DFS.topologicalOrder(listOf(stateMachineBuilder.entryState), { it.successors }, { visited.add(it) })
        sortedStates.withIndex().forEach { it.value.id = it.index }

        fun buildDispatch(target: SuspendState) = target.run {
            assert(id >= 0)
            JsIrBuilder.buildInt(context.irBuiltIns.intType, id)
        }

        val eqeqeqInt = context.irBuiltIns.eqeqeqSymbol

        for (state in sortedStates) {
            val condition = JsIrBuilder.buildCall(eqeqeqInt).apply {
                putValueArgument(0, JsIrBuilder.buildCall(coroutineImplLabelPropertyGetter.symbol).also {
                    it.dispatchReceiver = JsIrBuilder.buildGetValue(thisReceiver)
                })
                putValueArgument(1, JsIrBuilder.buildInt(context.irBuiltIns.intType, state.id))
            }

            switch.branches += IrBranchImpl(state.entryBlock.startOffset, state.entryBlock.endOffset, condition, state.entryBlock)
        }

        val irResultDeclaration = suspendResult

        rootLoop.transform(DispatchPointTransformer(::buildDispatch), null)

        exceptionTrapId = stateMachineBuilder.rootExceptionTrap.id

        val functionBody = IrBlockBodyImpl(doResumeFunction.startOffset, doResumeFunction.endOffset, listOf(irResultDeclaration, rootLoop))

        doResumeFunction.body = functionBody

        val liveLocals = computeLivenessAtSuspensionPoints(functionBody).values.flatten().toSet()

        val localToPropertyMap = mutableMapOf<IrValueSymbol, IrFieldSymbol>()
        var localCounter = 0
        // TODO: optimize by using the same property for different locals.
        liveLocals.forEach {
            if (it != suspendState && it != suspendResult) {
                localToPropertyMap.getOrPut(it.symbol) {
                    coroutineClass.addField(Name.identifier("${it.name}${localCounter++}"), it.type, (it as? IrVariable)?.isVar ?: false).symbol
                }
            }
        }
        transformingFunction.explicitParameters.forEach {
            localToPropertyMap.getOrPut(it.symbol) {
                argumentToPropertiesMap.getValue(it).symbol
            }
        }

        doResumeFunction.transform(LiveLocalsTransformer(localToPropertyMap, { JsIrBuilder.buildGetValue(thisReceiver) }, unit), null)
    }

    private fun computeLivenessAtSuspensionPoints(body: IrBody): Map<IrCall, List<IrValueDeclaration>> {
        // TODO: data flow analysis.
        // Just save all visible for now.
        val result = mutableMapOf<IrCall, List<IrValueDeclaration>>()
        body.acceptChildrenVoid(object : VariablesScopeTracker() {
            override fun visitCall(expression: IrCall) {
                if (!expression.isSuspend) return super.visitCall(expression)

                expression.acceptChildrenVoid(this)
                val visibleVariables = mutableListOf<IrValueDeclaration>()
                scopeStack.forEach { visibleVariables += it }
                result[expression] = visibleVariables
            }
        })

        return result
    }


    override fun initializeStateMachine(coroutineConstructors: List<IrConstructor>, coroutineClassThis: IrValueDeclaration) {
        for (it in coroutineConstructors) {
            (it.body as? IrBlockBody)?.run {
                val receiver = JsIrBuilder.buildGetValue(coroutineClassThis.symbol)
                val id = JsIrBuilder.buildInt(context.irBuiltIns.intType, exceptionTrapId)
                statements += JsIrBuilder.buildCall(coroutineImplExceptionStatePropertySetter.symbol).also { call ->
                    call.dispatchReceiver = receiver
                    call.putValueArgument(0, id)
                }
            }
        }
    }

    private open class VariablesScopeTracker : IrElementVisitorVoid {

        protected val scopeStack = mutableListOf<MutableSet<IrVariable>>(mutableSetOf())

        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitContainerExpression(expression: IrContainerExpression) {
            if (!expression.isTransparentScope)
                scopeStack.push(mutableSetOf())
            super.visitContainerExpression(expression)
            if (!expression.isTransparentScope)
                scopeStack.pop()
        }

        override fun visitCatch(aCatch: IrCatch) {
            scopeStack.push(mutableSetOf())
            super.visitCatch(aCatch)
            scopeStack.pop()
        }

        override fun visitVariable(declaration: IrVariable) {
            super.visitVariable(declaration)
            scopeStack.peek()!!.add(declaration)
        }
    }
}