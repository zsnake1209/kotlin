/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.codegen

import org.jetbrains.kotlin.backend.common.ir.isElseBranch
import org.jetbrains.kotlin.backend.wasm.ast.*
import org.jetbrains.kotlin.backend.wasm.utils.getWasmInstructionAnnotation
import org.jetbrains.kotlin.ir.backend.js.utils.realOverrideTarget
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.isUnit
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable

class IrElementToWasmExpressionTransformer : BaseIrElementToWasmNodeTransformer<WasmInstruction, WasmContext> {
    override fun visitVararg(expression: IrVararg, data: WasmContext): WasmInstruction {
        TODO("Support arrays")
    }

    override fun visitExpressionBody(body: IrExpressionBody, data: WasmContext): WasmInstruction =
        body.expression.accept(this, data)

    override fun visitFunctionReference(expression: IrFunctionReference, data: WasmContext): WasmInstruction {
        TODO("?")
    }

    override fun <T> visitConst(expression: IrConst<T>, data: WasmContext): WasmInstruction {
        return when (val kind = expression.kind) {
            is IrConstKind.Null -> TODO()
            is IrConstKind.String -> {
                val value = kind.valueOf(expression)
                val index = data.stringLiterals.size
                data.stringLiterals.add(value)
                val funName = data.getGlobalName(data.backendContext.wasmSymbols.stringGetLiteral.owner)
                val operand = WasmI32Const(index)
                WasmCall(funName, listOf(operand))
            }
            is IrConstKind.Boolean -> WasmI32Const(if (kind.valueOf(expression)) 1 else 0)
            is IrConstKind.Byte -> WasmI32Const(kind.valueOf(expression).toInt())
            is IrConstKind.Short -> WasmI32Const(kind.valueOf(expression).toInt())
            is IrConstKind.Int -> WasmI32Const(kind.valueOf(expression))
            is IrConstKind.Long -> WasmI64Const(kind.valueOf(expression))
            is IrConstKind.Char -> WasmI32Const(kind.valueOf(expression).toInt())
            is IrConstKind.Float -> WasmF32Const(kind.valueOf(expression))
            is IrConstKind.Double -> WasmF64Const(kind.valueOf(expression))
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: WasmContext): WasmInstruction {
        TODO("Implement kotlin.String")
    }

    override fun visitGetField(expression: IrGetField, data: WasmContext): WasmInstruction {
        val fieldName = data.getGlobalName(expression.symbol.owner)
        if (expression.receiver != null)
            TODO("Support member fields")

        return WasmGetGlobal(fieldName)
    }

    override fun visitGetValue(expression: IrGetValue, data: WasmContext): WasmInstruction =
        WasmGetLocal(data.getLocalName(expression.symbol.owner))

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: WasmContext): WasmInstruction {
        TODO("IrGetObjectValue")
    }

    override fun visitSetField(expression: IrSetField, data: WasmContext): WasmInstruction {
        val fieldName = data.getGlobalName(expression.symbol.owner)
        if (expression.receiver != null)
            TODO("Support member fields")

        val value = expression.value.accept(this, data)
        return WasmSetGlobal(fieldName, value)
    }

    override fun visitSetVariable(expression: IrSetVariable, data: WasmContext): WasmInstruction {
        val fieldName = data.getLocalName(expression.symbol.owner)
        val value = expression.value.accept(this, data)
        return WasmSetLocal(fieldName, value)
    }

    override fun visitConstructorCall(expression: IrConstructorCall, data: WasmContext): WasmInstruction {
        TODO("IrConstructorCall")
    }

    override fun visitCall(expression: IrCall, data: WasmContext): WasmInstruction {
        val function = expression.symbol.owner.realOverrideTarget
        require(function is IrSimpleFunction) { "Only IrSimpleFunction could be called via IrCall" }
        val valueArgs = (0 until expression.valueArgumentsCount).mapNotNull { expression.getValueArgument(it) }
        val irArguments = listOfNotNull(expression.dispatchReceiver, expression.extensionReceiver) + valueArgs
        val wasmArguments = irArguments.map { expressionToWasmInstruction(it, data) }

        val wasmInstruction = function.getWasmInstructionAnnotation()
        if (wasmInstruction != null) {
            if (wasmInstruction == "nop") {
                return wasmArguments.single()
            }
            return WasmSimpleInstruction(wasmInstruction, wasmArguments)
        }

        val name = data.getGlobalName(function)
        return WasmCall(name, wasmArguments)
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: WasmContext): WasmInstruction {
        val wasmArgument = expressionToWasmInstruction(expression.argument, data)
        when (expression.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> return wasmArgument
        }
        TODO("IrTypeOperatorCall:\n ${expression.dump()}")
    }

    override fun visitGetEnumValue(expression: IrGetEnumValue, data: WasmContext): WasmInstruction {
        TODO("IrGetEnumValue")
    }

    override fun visitBlockBody(body: IrBlockBody, data: WasmContext): WasmInstruction {
        TODO()
    }

    override fun visitContainerExpression(expression: IrContainerExpression, data: WasmContext): WasmInstruction {
        val expressions = expression.statements.map { it.accept(this, data) }

        if (!expression.type.isUnit())
            return WasmBlock(expressions + listOf(WasmDrop(emptyList())))

        return WasmBlock(expressions)
    }

    override fun visitExpression(expression: IrExpression, data: WasmContext): WasmInstruction {
        return expressionToWasmInstruction(expression, data)
    }

    override fun visitBreak(jump: IrBreak, data: WasmContext): WasmInstruction {
        TODO()
    }

    override fun visitContinue(jump: IrContinue, data: WasmContext): WasmInstruction {
        TODO()
    }

    override fun visitReturn(expression: IrReturn, data: WasmContext): WasmInstruction {
        if (expression.value.type.isUnit()) return WasmReturn(emptyList())

        return WasmReturn(listOf(expressionToWasmInstruction(expression.value, data)))
    }

    override fun visitThrow(expression: IrThrow, data: WasmContext): WasmInstruction {
        TODO("IrThrow")
    }

    override fun visitVariable(declaration: IrVariable, data: WasmContext): WasmInstruction {
        val init = declaration.initializer ?: return WasmNop()
        val varName = data.getLocalName(declaration)
        return WasmSetLocal(varName, expressionToWasmInstruction(init, data))
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: WasmContext): WasmInstruction {
        TODO("IrDelegatingConstructorCall")
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: WasmContext): WasmInstruction {
        TODO("IrInstanceInitializerCall")
    }

    override fun visitTry(aTry: IrTry, data: WasmContext): WasmInstruction {
        TODO("IrTry")
    }

    override fun visitWhen(expression: IrWhen, data: WasmContext): WasmInstruction {
        return expression.branches.foldRight<IrBranch, WasmInstruction?>(null) { br: IrBranch, inst: WasmInstruction? ->
            val body = expressionToWasmInstruction(br.result, data)
            if (isElseBranch(br)) body
            else {
                val condition = expressionToWasmInstruction(br.condition, data)
                WasmIf(condition, WasmThen(body), inst?.let{ WasmElse(inst) })
            }
        }!!
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: WasmContext): WasmInstruction {
        TODO()
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: WasmContext): WasmInstruction {
        TODO()
    }

    override fun visitSyntheticBody(body: IrSyntheticBody, data: WasmContext): WasmInstruction {
        TODO("IrSyntheticBody")
    }

    override fun visitDynamicMemberExpression(expression: IrDynamicMemberExpression, data: WasmContext): WasmInstruction =
        error("Dynamic operators are not supported for WASM target")

    override fun visitDynamicOperatorExpression(expression: IrDynamicOperatorExpression, data: WasmContext): WasmInstruction =
        error("Dynamic operators are not supported for WASM target")
}
