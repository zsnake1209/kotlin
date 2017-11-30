/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.compat.codegen

import org.jetbrains.kotlin.android.compat.scope.CompatSyntheticFunctionDescriptor
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.load.java.sam.SamAdapterDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.synthetic.SamAdapterExtensionFunctionDescriptor
import org.jetbrains.kotlin.synthetic.SyntheticJavaPropertyDescriptor
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

object CompatExpressionCodegenExtension : ExpressionCodegenExtension {
    override fun applyProperty(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        val propertyDescriptor = resolvedCall.candidateDescriptor as? SyntheticJavaPropertyDescriptor ?: return null
        val getter = propertyDescriptor.getMethod as? CompatSyntheticFunctionDescriptor ?: return null
        val setter = propertyDescriptor.setMethod as? CompatSyntheticFunctionDescriptor

        return CompatProperty(
                propertyDescriptor,
                c.typeMapper.mapType(getter.returnType!!),
                c.typeMapper.mapToCallableMethod(getter.baseDescriptorForSynthetic, false),
                if (setter == null) null else c.typeMapper.mapToCallableMethod(setter.baseDescriptorForSynthetic, false),
                StackValue.receiver(resolvedCall, receiver, c.codegen, null),
                c
        )
    }

    override fun applyFunction(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        var candidateDescriptor = resolvedCall.candidateDescriptor
        if (candidateDescriptor is SamAdapterExtensionFunctionDescriptor) {
            candidateDescriptor = candidateDescriptor.baseDescriptorForSynthetic
        } else if (candidateDescriptor is SamAdapterDescriptor<*>) {
            candidateDescriptor = candidateDescriptor.baseDescriptorForSynthetic
        }
        val functionDescriptor = candidateDescriptor as? CompatSyntheticFunctionDescriptor ?: return null
        val isStatic = resolvedCall.dispatchReceiver == null
        val callable = c.typeMapper.mapToCallableMethod(functionDescriptor, false)
        val baseDescriptor = functionDescriptor.baseDescriptorForSynthetic
        val baseCallable = c.typeMapper.mapToCallableMethod(baseDescriptor, false)

        if (!isStatic) {
            putReceiverOnStack(resolvedCall, receiver, c)
        }
        val argGen = CallBasedArgumentGenerator(
                c.codegen,
                c.codegen.defaultCallGenerator,
                functionDescriptor.valueParameters,
                callable.valueParameterTypes
        )
        argGen.generate(
                resolvedCall.valueArgumentsByIndex ?: emptyList(),
                resolvedCall.valueArguments.values.toList(),
                null
        )
        val returnType = if (baseDescriptor.returnType == null || KotlinBuiltIns.isUnit(baseDescriptor.returnType!!)) Type.VOID_TYPE
        else baseDescriptor.returnType!!.asmType(c.typeMapper)
        return StackValue.functionCall(returnType) { v ->
            v.invokestatic(
                    baseCallable.owner.internalName,
                    baseCallable.getAsmMethod().name,
                    baseCallable.getAsmMethod().descriptor,
                    false
            )
        }
    }

    private fun putReceiverOnStack(resolvedCall: ResolvedCall<*>, receiver: StackValue, c: ExpressionCodegenExtension.Context) {
        val receiverDescriptor = resolvedCall.dispatchReceiver!!.type.constructor.declarationDescriptor!!
        StackValue.receiver(resolvedCall, receiver, c.codegen, null).put(c.typeMapper.mapType(receiverDescriptor), c.v)
    }

    private class CompatProperty(
            propertyDescriptor: SyntheticJavaPropertyDescriptor,
            type: Type,
            private val getter: CallableMethod,
            private val setter: CallableMethod?,
            receiver: StackValue,
            c: ExpressionCodegenExtension.Context
    ) : StackValue.Property(
            propertyDescriptor,
            null,
            getter,
            setter,
            false,
            null,
            type,
            receiver,
            c.codegen,
            null,
            false
    ) {
        override fun putReceiver(v: InstructionAdapter, isRead: Boolean) {
            receiver.put(receiver.type, v)
        }

        override fun putSelector(type: Type, v: InstructionAdapter) {
            getter.genInvokeInstruction(v)
            coerce(getter.returnType, type, v)
        }

        override fun store(rightSide: StackValue, v: InstructionAdapter, skipReceiver: Boolean) {
            if (setter != null) {
                putReceiver(v, false)
                rightSide.put(rightSide.type, v)
                storeSelector(rightSide.type, v)
            }
            else {
                super.store(rightSide, v, skipReceiver)
            }
        }

        override fun storeSelector(topOfStackType: Type, v: InstructionAdapter) {
            if (setter == null) error("Setter is null")
            coerce(topOfStackType, setter.parameterTypes.last(), v)

            setter.genInvokeInstruction(v)

            val returnType = setter.returnType
            if (returnType != Type.VOID_TYPE) {
                AsmUtil.pop(v, returnType)
            }
        }
    }
}