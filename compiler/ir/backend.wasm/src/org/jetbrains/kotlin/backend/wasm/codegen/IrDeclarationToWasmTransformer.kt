/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.wasm.codegen

import org.jetbrains.kotlin.backend.wasm.ast.*
import org.jetbrains.kotlin.backend.wasm.utils.getWasmImportAnnotation
import org.jetbrains.kotlin.backend.wasm.utils.getWasmInstructionAnnotation
import org.jetbrains.kotlin.backend.wasm.utils.hasExcludedFromCodegenAnnotation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isAnnotationClass
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

class IrDeclarationToWasmTransformer : BaseIrElementToWasmNodeTransformer<WasmModuleField?, WasmContext> {
    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: WasmContext): WasmModuleField? {
        // TODO: Exclude before codegen
        if (declaration.hasExcludedFromCodegenAnnotation())
            return null

        if (declaration.getWasmInstructionAnnotation() != null)
            return null

        if (declaration.isFakeOverride)
            return null

        if (declaration.isInline)
            return null

        // Collect local variables
        val localNames = wasmNameTable<IrValueDeclaration>()

        val wasmName = data.getGlobalName(declaration)

        val irParameters = declaration.run {
            listOfNotNull(dispatchReceiverParameter, extensionReceiverParameter) + valueParameters
        }

        val wasmParameters = irParameters.map { parameter ->
            val name = localNames.declareFreshName(parameter, parameter.name.asString())
            WasmParameter(name, data.transformType(parameter.type))
        }

        val wasmReturnType = when {
            declaration.returnType.isUnit() -> null
            else -> data.transformType(declaration.returnType)
        }

        val importedName = declaration.getWasmImportAnnotation()
        if (importedName != null) {
            data.imports.add(
                WasmFunction(
                    name = wasmName,
                    parameters = wasmParameters,
                    returnType = wasmReturnType,
                    locals = emptyList(),
                    instructions = emptyList(),
                    importName = importedName
                )
            )
            return null
        }

        val body = declaration.body ?:
            error("Function ${declaration.fqNameWhenAvailable} without a body")

        data.localNames = localNames.names
        val locals = mutableListOf<WasmLocal>()
        body.acceptChildrenVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitVariable(declaration: IrVariable) {
                val name = localNames.declareFreshName(declaration, declaration.name.asString())
                locals += WasmLocal(name, data.transformType(declaration.type))
                super.visitVariable(declaration)
            }
        })

        return WasmFunction(
            name = wasmName,
            parameters = wasmParameters,
            returnType = wasmReturnType,
            locals = locals,
            instructions = bodyToWasm(body, data),
            importName = null
        )
    }

    override fun visitConstructor(declaration: IrConstructor, data: WasmContext): WasmModuleField? {
        TODO()
    }

    override fun visitClass(declaration: IrClass, data: WasmContext): WasmModuleField? {
        if (declaration.isAnnotationClass) return null
        if (declaration.hasExcludedFromCodegenAnnotation()) return null

        val wasmMembers = declaration.declarations.mapNotNull { member ->
            when (member) {
                is IrSimpleFunction -> {
                    if (member.origin == IrDeclarationOrigin.BRIDGE) {
                        null
                    } else {
                        this.visitSimpleFunction(member, data)
                    }
                }
                else -> null
            }
        }

        return WasmModuleFieldList(wasmMembers)
    }

    override fun visitField(declaration: IrField, data: WasmContext): WasmModuleField {
        return WasmGlobal(
            name = data.getGlobalName(declaration),
            type = data.transformType(declaration.type),
            isMutable = true,
            init = declaration.initializer?.let {
                expressionToWasmInstruction(it.expression, data)
            }
        )
    }
}

fun WasmContext.transformType(irType: IrType): WasmValueType =
    when {
        irType.isBoolean() -> WasmI32
        irType.isByte() -> WasmI32
        irType.isShort() -> WasmI32
        irType.isInt() -> WasmI32
        irType.isLong() -> WasmI64
        irType.isChar() -> WasmI32
        irType.isFloat() -> WasmF32
        irType.isDouble() -> WasmF64
        irType.isString() -> WasmAnyRef
        irType.isAny() || irType.isNullableAny() -> WasmAnyRef
        else ->
            TODO("Unsupported type: ${irType.render()}")
    }