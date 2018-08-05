/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.lower.DECLARATION_ORIGIN_FUNCTION_FOR_DEFAULT_PARAMETER
import org.jetbrains.kotlin.backend.common.lower.InitializersLowering.Companion.clinitName
import org.jetbrains.kotlin.backend.common.lower.VariableRemapper
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.backend.jvm.descriptors.DefaultImplsClassDescriptorImpl
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationsImpl
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFunctionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrSimpleFunctionSymbolImpl
import org.jetbrains.kotlin.ir.types.toIrType
import org.jetbrains.kotlin.ir.util.createParameterDeclarations
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.source.PsiSourceElement
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.org.objectweb.asm.Opcodes

class InterfaceLowering(val context: JvmBackendContext) : IrElementTransformerVoid(), ClassLoweringPass {

    override fun lower(irClass: IrClass) {
        if (!irClass.isInterface) return

        val defaultImplsIrClass = getDefaultImplsClass(irClass.descriptor, context)
        irClass.declarations.add(defaultImplsIrClass)

        val members = defaultImplsIrClass.declarations

        irClass.declarations.filterIsInstance<IrFunction>().forEach { oldFunction ->
            val descriptor = oldFunction.descriptor
            if (oldFunction.origin == DECLARATION_ORIGIN_FUNCTION_FOR_DEFAULT_PARAMETER) {
                members.add(oldFunction) //just copy $default to DefaultImpls
            } else if (descriptor.modality != Modality.ABSTRACT && oldFunction.origin != IrDeclarationOrigin.FAKE_OVERRIDE) {
                val newFunction =
                    getDefaultImplFunction(defaultImplsIrClass.descriptor, descriptor, irClass.descriptor, context)
                newFunction.transferBody(oldFunction)
                members.add(newFunction)
                oldFunction.body = null
                //TODO reset modality to abstract
            }
        }


        irClass.transformChildrenVoid(this)

        //REMOVE private methods
        val privateToRemove = irClass.declarations.filterIsInstance<IrFunction>().mapNotNull {
            val visibility = AsmUtil.getVisibilityAccessFlag(it.descriptor)
            if (visibility == Opcodes.ACC_PRIVATE && it.descriptor.name != clinitName) {
                it
            } else null
        }

        val defaultBodies = irClass.declarations.filterIsInstance<IrFunction>().filter {
            it.origin == DECLARATION_ORIGIN_FUNCTION_FOR_DEFAULT_PARAMETER
        }
        irClass.declarations.removeAll(privateToRemove)
        irClass.declarations.removeAll(defaultBodies)
    }

    companion object {

        fun getDefaultImplsClass(interfaceDescriptor: ClassDescriptor, context: CommonBackendContext): IrClass {
            return context.ir.interfaceDefaultImplsClassCache.getOrPut(interfaceDescriptor) {
                val defaultImplsDescriptor = DefaultImplsClassDescriptorImpl(
                    Name.identifier(JvmAbi.DEFAULT_IMPLS_CLASS_NAME), interfaceDescriptor, interfaceDescriptor.source
                )

                val psi = (interfaceDescriptor.source as? PsiSourceElement)?.psi
                IrClassImpl(
                    psi?.startOffset ?: UNDEFINED_OFFSET,
                    psi?.endOffset ?: UNDEFINED_OFFSET,
                    JvmLoweredDeclarationOrigin.DEFAULT_IMPLS,
                    defaultImplsDescriptor
                )
            }
        }

        fun getDefaultImplFunction(
            defaultImplsDescriptor: ClassDescriptor,
            descriptor: FunctionDescriptor,
            interfaceDescriptor: ClassDescriptor,
            context: JvmBackendContext
        ): IrSimpleFunction {
            return context.ir.interfaceDefaultImplsFunCache.getOrPut(descriptor) {
                val name = Name.identifier(context.state.typeMapper.mapAsmMethod(descriptor).name)
                val funDescriptor =
                    createStaticFunctionDescriptorWithReceivers(defaultImplsDescriptor, name, descriptor, interfaceDescriptor.defaultType)
                val psi = (descriptor.source as? PsiSourceElement)?.psi
                IrFunctionImpl(
                    psi?.startOffset ?: UNDEFINED_OFFSET,
                    psi?.endOffset ?: UNDEFINED_OFFSET,
                    JvmLoweredDeclarationOrigin.JVM_MODIFIED_BY_LOWERING,  // true origin not always accessible
                    IrSimpleFunctionSymbolImpl(funDescriptor),
                    descriptor.visibility
                ).apply {
                    returnType = descriptor.returnType!!.toIrType()!!
                    createParameterDeclarations()
                }
            }
        }
    }
}


internal fun createStaticFunctionDescriptorWithReceivers(
    owner: ClassOrPackageFragmentDescriptor,
    name: Name,
    descriptor: FunctionDescriptor,
    dispatchReceiverType: KotlinType
): SimpleFunctionDescriptorImpl {
    val newFunction = SimpleFunctionDescriptorImpl.create(
        owner,
        AnnotationsImpl(emptyList()),
        name,
        CallableMemberDescriptor.Kind.DECLARATION, descriptor.source
    )
    var offset = 0
    val dispatchReceiver =
        ValueParameterDescriptorImpl.createWithDestructuringDeclarations(
            newFunction, null, offset++, AnnotationsImpl(emptyList()), Name.identifier("this"),
            dispatchReceiverType, false, false, false, null, descriptor.source, null
        )
    val extensionReceiver =
        descriptor.extensionReceiverParameter?.let { extensionReceiver ->
            ValueParameterDescriptorImpl.createWithDestructuringDeclarations(
                newFunction, null, offset++, AnnotationsImpl(emptyList()), Name.identifier("receiver"),
                extensionReceiver.value.type, false, false, false, null, extensionReceiver.source, null
            )
        }

    val valueParameters = listOfNotNull(dispatchReceiver, extensionReceiver) +
            descriptor.valueParameters.map { it.copy(newFunction, it.name, it.index + offset) }

    newFunction.initialize(
        null, null, emptyList()/*TODO: type parameters*/,
        valueParameters, descriptor.returnType, Modality.FINAL, descriptor.visibility
    )
    return newFunction
}

internal fun IrFunction.transferBody(oldFunction: IrFunction) {
    body = oldFunction.body
    val mapping: Map<ValueDescriptor, IrValueParameter> =
        (
                listOfNotNull(oldFunction.descriptor.dispatchReceiverParameter!!, oldFunction.descriptor.extensionReceiverParameter) +
                        oldFunction.descriptor.valueParameters
                ).zip(valueParameters).toMap()

    body?.transform(VariableRemapper(mapping), null)
}

internal fun FunctionDescriptor.createFunctionAndMapVariables(
    oldFunction: IrFunction,
    visibility: Visibility
) =
    IrFunctionImpl(
        oldFunction.startOffset, oldFunction.endOffset, oldFunction.origin, IrSimpleFunctionSymbolImpl(this),
        visibility = visibility
    ).apply {
        body = oldFunction.body
        createParameterDeclarations()
        val mapping: Map<ValueDescriptor, IrValueParameter> =
            (
                    listOfNotNull(oldFunction.descriptor.dispatchReceiverParameter!!, oldFunction.descriptor.extensionReceiverParameter) +
                            oldFunction.descriptor.valueParameters
                    ).zip(valueParameters).toMap()

        body?.transform(VariableRemapper(mapping), null)
    }
