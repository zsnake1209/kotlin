/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.serialization.compiler.backend.jvm

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper.Companion.mapUnderlyingTypeOfInlineClassType
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.resolve.substitutedUnderlyingType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlinx.serialization.compiler.resolve.CallingConventions
import org.jetbrains.kotlinx.serialization.compiler.resolve.SerialEntityNames
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter

class SerializerForInlineClassesCodegen(
    codegen: ImplementationBodyCodegen,
    serializableClass: ClassDescriptor
) : SerializerCodegenImpl(codegen, serializableClass) {
    override fun generateSave(function: FunctionDescriptor) = codegen.generateMethod(function) { _, _ ->
        // fun serialize(output: Encoder, obj : UnboxedT)
        val property = serializableProperties.first()
        val outputVar = 1
        val objVar = 2
        val exitLabel = Label()
        // output.encodeInline(descriptor)?.
        load(outputVar, encoderType)
        stackSerialClassDesc(null)
        invokeinterface(encoderType.internalName, CallingConventions.encodeInline, "(${descType.descriptor})${encoderType.descriptor}")
        dup()
        store(outputVar, encoderType)
        ifnull(exitLabel)
        load(outputVar, encoderType)
        // encodePrimitive(value)
        val sti = getSerialTypeInfo(property, property.type, codegen.typeMapper)
        val jvmType = sti.type
        val useSerializer =
            stackValueSerializerInstanceFromSerializer(codegen, sti, this@SerializerForInlineClassesCodegen, sti.kotlinType, sti.serializer)
        load(objVar, jvmType)
        invokeinterface(
            encoderType.internalName,
            CallingConventions.encode + sti.elementMethodPrefix + (if (useSerializer) "SerializableValue" else ""),
            "(" +
                    (if (useSerializer) kSerialSaverType.descriptor else "") +
                    (if (sti.unit) "" else sti.type.descriptor) + ")V"
        )
        // return
        visitLabel(exitLabel)
        areturn(Type.VOID_TYPE)
    }

    override fun generateLoad(function: FunctionDescriptor) = codegen.generateMethod(function) { sig, _ ->
        // fun deserialize(decoder: Decoder): UnboxedT
        val property = serializableProperties.first()
        val inputVar = 1
        // decoder.decodeInline(desc)
        load(inputVar, decoderType)
        stackSerialClassDesc(null)
        invokeinterface(decoderType.internalName, CallingConventions.decodeInline, "(${descType.descriptor})${decoderType.descriptor}")
        // todo: check for null, return default value if so
        val sti = getSerialTypeInfo(property, property.type, codegen.typeMapper)
        val jvmType = sti.type
        val useSerializer =
            stackValueSerializerInstanceFromSerializer(codegen, sti, this@SerializerForInlineClassesCodegen, sti.kotlinType, sti.serializer)
        // todo: update
        invokeinterface(
            decoderType.internalName,
            CallingConventions.decode + sti.elementMethodPrefix + (if (useSerializer) "SerializableValue" else ""),
            "(" +
                    (if (useSerializer) kSerialLoaderType.descriptor else "") +
                    ")" + (if (sti.unit) "" else sti.type.descriptor)
        )

        callInlineClassConstructorAndBoxIfNeeded(codegen.typeMapper, serializableDescriptor.defaultType)

        areturn(sig.returnType)
    }

    override fun ExpressionCodegen.instantiateNewDescriptor(isStatic: Boolean) = with(v) {
        // todo
        val property = serializableProperties.first()
        val inlineDescType = descriptorForInlineClassesType
        anew(inlineDescType)
        dup()
        aconst(serialName)
        findAndStackValueSerializerInstanceFromSerializer(
            codegen,
            property,
            this@SerializerForInlineClassesCodegen
        )
        invokeinterface(kSerializerType.internalName, SerialEntityNames.SERIAL_DESC_FIELD_GETTER, "()${descType.descriptor}")
        invokespecial(inlineDescType.internalName, "<init>", "(Ljava/lang/String;${descType.descriptor})V", false)
        checkcast(descImplType)
    }

    companion object {
        fun InstructionAdapter.callInlineClassConstructorAndBoxIfNeeded(typeMapper: KotlinTypeMapper, type: KotlinType) {
            val owner = typeMapper.mapTypeAsDeclaration(type)
            val underlyingType =
                mapUnderlyingTypeOfInlineClassType(type)
            if (underlyingType.sort == OBJECT || underlyingType.sort == ARRAY)
                checkcast(underlyingType)
            invokestatic(owner.internalName, "constructor-impl", "(${underlyingType.descriptor})${underlyingType.descriptor}", false)
            // call box-impl
            // BUT only if it is a primitive class
            // /shrug https://youtrack.jetbrains.com/issue/KT-30419#focus=streamItem-27-3828805.0-0
            if (KotlinBuiltIns.isPrimitiveType(type.substitutedUnderlyingType()!!))
                StackValue.boxInlineClass(type, this)
        }
    }
}