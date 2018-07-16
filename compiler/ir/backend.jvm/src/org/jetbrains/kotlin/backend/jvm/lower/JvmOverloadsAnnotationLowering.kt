/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ClassConstructorDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrConstructorImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrDelegatingConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrExpressionBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.impl.createFunctionSymbol
import org.jetbrains.kotlin.ir.util.createParameterDeclarations
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.resolve.calls.components.hasDefaultValue
import org.jetbrains.kotlin.resolve.jvm.annotations.findJvmOverloadsAnnotation


class JvmOverloadsAnnotationLowering(val context: JvmBackendContext) : ClassLoweringPass {

    override fun lower(irClass: IrClass) {
        val functions = irClass.declarations.filterIsInstance<IrFunction>().filter {
            it.descriptor.findJvmOverloadsAnnotation() != null
        }

        functions.forEach {
            generateWrappers(it, irClass)
        }
    }

    private fun generateWrappers(target: IrFunction, irClass: IrClass) {
        val numDefaultParameters = target.symbol.descriptor.valueParameters.count { it.hasDefaultValue() }
        for (i in 0 until numDefaultParameters) {
            val wrapper = generateWrapper(target, irClass, i)
            irClass.addMember(wrapper)
        }
    }

    private fun generateWrapper(target: IrFunction, irClass: IrClass, numDefaultParametersToExpect: Int): IrFunction {
        val wrapperSymbol = generateWrapperSymbol(target.symbol, irClass, numDefaultParametersToExpect)
        val wrapperIrFunction = when (wrapperSymbol) {
            is IrConstructorSymbol -> IrConstructorImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                JvmLoweredDeclarationOrigin.JVM_OVERLOADS_WRAPPER,
                wrapperSymbol
            )
            is IrSimpleFunctionSymbol -> IrFunctionImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                JvmLoweredDeclarationOrigin.JVM_OVERLOADS_WRAPPER,
                wrapperSymbol
            )
            else -> error("expected IrConstructorSymbol or IrSimpleFunctionSymbol")
        }

        wrapperIrFunction.returnType = target.returnType
        wrapperIrFunction.createParameterDeclarations()

        val call = if (target is IrConstructor)
            IrDelegatingConstructorCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, target.returnType, target.symbol, target.descriptor)
        else
            IrCallImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, target.returnType, target.symbol)
        call.dispatchReceiver = wrapperIrFunction.dispatchReceiverParameter?.let { dispatchReceiver ->
            IrGetValueImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                dispatchReceiver.symbol
            )
        }
        call.extensionReceiver = wrapperIrFunction.extensionReceiverParameter?.let { extensionReceiver ->
            IrGetValueImpl(
                UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                extensionReceiver.symbol
            )
        }

        var parametersCopied = 0
        var defaultParametersAlreadyCopied = 0
        target.valueParameters.mapIndexed { i, valueParameter ->
            if ((valueParameter.descriptor as ValueParameterDescriptor).hasDefaultValue()) {
                if (defaultParametersAlreadyCopied < numDefaultParametersToExpect) {
                    defaultParametersAlreadyCopied++
                    call.putValueArgument(
                        i,
                        IrGetValueImpl(
                            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                            wrapperIrFunction.valueParameters[parametersCopied++].symbol
                        )
                    )
                } else {
                    call.putValueArgument(i, null)
                }
            } else {
                call.putValueArgument(
                    i,
                    IrGetValueImpl(
                        UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                        wrapperIrFunction.valueParameters[parametersCopied++].symbol
                    )
                )
            }
        }

        wrapperIrFunction.body = IrExpressionBodyImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET, call
        )

        target.annotations.mapTo(wrapperIrFunction.annotations) { it.deepCopyWithSymbols() }

        return wrapperIrFunction
    }

    private fun generateWrapperSymbol(oldSymbol: IrFunctionSymbol, irClass: IrClass, numDefaultParametersToExpect: Int): IrFunctionSymbol {
        val oldDescriptor = oldSymbol.descriptor
        val newDescriptor = if (oldSymbol.descriptor is ClassConstructorDescriptor) {
            ClassConstructorDescriptorImpl.createSynthesized(
                irClass.descriptor,
                oldDescriptor.annotations,
                /* isPrimary = */ false,
                oldDescriptor.source
            ).apply {
                // Call the long version of `initialize()`, because otherwise default implementation inserts
                // an unwanted `dispatchReceiverParameter`
                initialize(
                    oldDescriptor.extensionReceiverParameter?.type,
                    oldDescriptor.dispatchReceiverParameter,
                    oldDescriptor.typeParameters,
                    generateNewValueParameters(oldDescriptor, numDefaultParametersToExpect),
                    oldDescriptor.returnType,
                    oldDescriptor.modality,
                    oldDescriptor.visibility
                )
                returnType = oldDescriptor.returnType!!
            }
        } else {
            val newParameters = generateNewValueParameters(oldDescriptor, numDefaultParametersToExpect)
            oldDescriptor.newCopyBuilder()
                .setValueParameters(newParameters)
                .setOriginal(null)
                .setKind(CallableMemberDescriptor.Kind.SYNTHESIZED)
                .build()!!
        }

        return createFunctionSymbol(newDescriptor)
    }

    private fun generateNewValueParameters(
        oldDescriptor: FunctionDescriptor,
        numDefaultParametersToExpect: Int
    ): List<ValueParameterDescriptor> {
        var parametersCopied = 0
        var defaultParametersAlreadyCopied = 0
        return oldDescriptor.valueParameters.mapNotNull { oldValueParameter ->
            if (oldValueParameter.hasDefaultValue()) {
                if (defaultParametersAlreadyCopied < numDefaultParametersToExpect) {
                    defaultParametersAlreadyCopied++
                    ValueParameterDescriptorImpl(
                        oldDescriptor,      // to be substituted with newDescriptor
                        null,
                        parametersCopied++,
                        oldValueParameter.annotations,
                        oldValueParameter.name,
                        oldValueParameter.type,
                        declaresDefaultValue = false,
                        isCrossinline = false,
                        isNoinline = false,
                        varargElementType = oldValueParameter.varargElementType,
                        source = oldValueParameter.source
                    )
                } else null
            } else {
                oldValueParameter.copy(oldDescriptor, oldValueParameter.name, parametersCopied++)
            }
        }
    }
}