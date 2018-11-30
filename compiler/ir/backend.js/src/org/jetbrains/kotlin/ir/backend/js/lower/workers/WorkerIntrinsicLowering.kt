/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.workers

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.descriptors.WrappedClassConstructorDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.WrappedClassDescriptor
import org.jetbrains.kotlin.backend.common.descriptors.synthesizedName
import org.jetbrains.kotlin.backend.common.lower.SymbolWithIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.backend.js.ir.addChild
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrConstructorImpl
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrConstructorSymbolImpl
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

class WorkerIntrinsicLowering(val context: JsIrBackendContext) : FileLoweringPass {
    private object WORKER_IMPL_ORIGIN : IrDeclarationOriginImpl("WORKER_IMPL")

    private var counter = 0

    private lateinit var caller: IrFunction

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                val call = super.visitCall(expression)
                if (call is IrCall && expression.descriptor.isWorker()) {
                    val param = call.getValueArgument(0) as? IrBlock ?: error("worker intrinsic accepts only block, but got $call")
                    // TODO: Move param to separate file
                    val wrapper = createWorkerWrapper(param)
                    context.implicitDeclarationFile.declarations.add(wrapper)
                    val constructor = wrapper.constructors.first()
                    return IrCallImpl(
                        startOffset = call.startOffset,
                        endOffset = call.endOffset,
                        type = wrapper.defaultType,
                        symbol = constructor.symbol
                    )
                } else {
                    return call
                }
            }

            override fun visitFunction(declaration: IrFunction): IrStatement {
                caller = declaration
                return super.visitFunction(declaration)
            }
        })
    }

    private fun createWorkerWrapper(block: IrBlock): IrClass {
        val classDescriptor = WrappedClassDescriptor()
        val wrapperClass = IrClassImpl(
            startOffset = block.startOffset,
            endOffset = block.endOffset,
            origin = WORKER_IMPL_ORIGIN,
            symbol = IrClassSymbolImpl(classDescriptor),
            name = "WorkerImpl\$${counter++}".synthesizedName,
            kind = ClassKind.CLASS,
            visibility = Visibilities.PUBLIC,
            modality = Modality.FINAL,
            isCompanion = false,
            isInner = false,
            isData = false,
            isExternal = false,
            isInline = false
        )
        classDescriptor.bind(wrapperClass)
        wrapperClass.parent = caller.parent
        wrapperClass.superTypes += mutableListOf(context.ir.symbols.workerInterface.owner.defaultType)
        val thisType = IrSimpleTypeImpl(wrapperClass.symbol, false, emptyList(), emptyList())
        val classThis = JsIrBuilder.buildValueParameter(Name.special("<this>"), -1, thisType, IrDeclarationOrigin.INSTANCE_RECEIVER)
        wrapperClass.thisReceiver = classThis
        classThis.parent = wrapperClass
        val constructorBuilder = createConstructorBuilder(wrapperClass)
        constructorBuilder.initialize()
        wrapperClass.addChild(constructorBuilder.ir)
        return wrapperClass
    }

    private fun createConstructorBuilder(clazz: IrClass) = object : SymbolWithIrBuilder<IrConstructorSymbol, IrConstructor>() {
        private val descriptor = WrappedClassConstructorDescriptor()

        override fun buildSymbol(): IrConstructorSymbol = IrConstructorSymbolImpl(descriptor)

        override fun doInitialize() {}

        override fun buildIr(): IrConstructor {
            val declaration = IrConstructorImpl(
                startOffset = clazz.startOffset,
                endOffset = clazz.endOffset,
                origin = WORKER_IMPL_ORIGIN,
                symbol = symbol,
                name = Name.special("<init>"),
                visibility = Visibilities.PUBLIC,
                returnType = clazz.defaultType,
                isInline = false,
                isExternal = false,
                isPrimary = false
            )
            descriptor.bind(declaration)
            declaration.parent = clazz
            declaration.body = context.createIrBuilder(symbol, declaration.startOffset, declaration.endOffset).irBlockBody(
                startOffset = UNDEFINED_OFFSET, endOffset = UNDEFINED_OFFSET
            ) {}
            return declaration
        }
    }
}

private fun FunctionDescriptor.isWorker(): Boolean = isTopLevelInPackage("worker", "kotlin.worker")
