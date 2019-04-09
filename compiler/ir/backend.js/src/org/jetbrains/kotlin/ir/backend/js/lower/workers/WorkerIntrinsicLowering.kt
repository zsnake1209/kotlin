/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.workers

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.*
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.lower.SymbolWithIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.ir.simpleFunctions
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrConstructorSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.cast

class WorkerIntrinsicLowering(val context: JsIrBackendContext) : FileLoweringPass {
    private val workerClass = context.ir.symbols.cast<JsIrBackendContext.JsSymbols>().workerClass
    private val jsCodeSymbol = context.symbolTable.referenceSimpleFunction(context.getJsInternalFunction("js"))

    override fun lower(irFile: IrFile) {
        val workerCalls = irFile.filterCalls { it.descriptor.isWorker() }
        if (workerCalls.isEmpty()) return
        val workerLambdas = workerCalls
            .map { it.getValueArgument(0) as? IrBlock ?: error("worker intrinsic accepts only block, but got $it") }
            .map { it.statements.filterIsInstance<IrFunctionReference>().single().symbol.owner }
        var counter = 0
        val callToName = workerCalls.zip(workerLambdas.map { "_Worker_${counter++}.js" }).toMap()

        // WebWorker(js("new Worker(\"_Worker_box$lambda-0\"))
        val transformed = irFile.transformCalls(predicate = { it in workerCalls }) { call ->
            val constructor = workerClass.constructors.first()
            newCallWithReusedOffsets(
                call,
                symbol = constructor,
                arguments = listOf(
                    newCallWithUndefinedOffsets(
                        symbol = jsCodeSymbol,
                        arguments = listOf(string("new Worker(\"${callToName[call]}\")")))
                )
            )
        }
        println(irFile.dump())
        println(workerCalls.zip(transformed).joinToString { (call, lambda) -> "${call.dump()} ->\n ${lambda.dump()}" })
    }

    private fun string(s: String) = JsIrBuilder.buildString(context.irBuiltIns.stringType, s)
}

fun IrFile.filterCalls(predicate: (IrCall) -> Boolean): List<IrCall> {
    val res = arrayListOf<IrCall>()
    acceptVoid(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitCall(expression: IrCall) {
            if (predicate(expression)) {
                res += expression
            }
            super.visitCall(expression)
        }
    })
    return res
}

fun IrFile.transformCalls(predicate: (IrCall) -> Boolean, transformer: (IrCall) -> IrCall): List<IrCall> {
    val res = arrayListOf<IrCall>()
    transformChildrenVoid(object : IrElementTransformerVoid() {
        override fun visitCall(expression: IrCall): IrExpression {
            val visited = super.visitCall(expression) as IrCall
            if (!predicate(visited)) return visited
            return transformer(visited).also { res += it }
        }
    })
    return res
}

fun newCallWithReusedOffsets(
    call: IrCall,
    symbol: IrFunctionSymbol,
    arguments: List<IrExpression?>
) = IrCallImpl(call.startOffset, call.endOffset, symbol.owner.returnType, symbol).apply {
    for ((index, expression) in arguments.withIndex()) {
        putValueArgument(index, expression)
    }
}

fun newCallWithUndefinedOffsets(
    symbol: IrFunctionSymbol,
    arguments: List<IrExpression?>
) = IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, symbol.owner.returnType, symbol).apply {
    for ((index, expression) in arguments.withIndex()) {
        putValueArgument(index, expression)
    }
}

private fun FunctionDescriptor.isWorker(): Boolean = isTopLevelInPackage("worker", "kotlin.worker")
