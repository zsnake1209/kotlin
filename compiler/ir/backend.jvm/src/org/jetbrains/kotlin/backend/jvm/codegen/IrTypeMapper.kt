/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.codegen

import org.jetbrains.kotlin.codegen.signature.JvmSignatureWriter
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.toKotlinType
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode

class IrTypeMapper(val kotlinTypeMapper: KotlinTypeMapper) {

    val classBuilderMode get() = kotlinTypeMapper.classBuilderMode

    fun mapType(irType: IrType) = kotlinTypeMapper.mapType(irType.toKotlinType())

    fun mapType(irClass: IrClass) = kotlinTypeMapper.mapType(irClass.descriptor)

    fun mapType(irField: IrField) = kotlinTypeMapper.mapType(irField.descriptor)

    fun mapType(irType: IrType, sw: JvmSignatureWriter, mode: TypeMappingMode) =
        kotlinTypeMapper.mapType(irType.toKotlinType(), sw, mode)

    fun mapFieldSignature(irType: IrType, irFrield: IrField) =
        kotlinTypeMapper.mapFieldSignature(irType.toKotlinType(), irFrield.descriptor)

    fun writeFormalTypeParameters(irParameters: List<IrTypeParameter>, sw: JvmSignatureWriter) =
        kotlinTypeMapper.writeFormalTypeParameters(irParameters.map { it.descriptor }, sw)

    fun mapSupertype(irType: IrType, sw: JvmSignatureWriter) = kotlinTypeMapper.mapSupertype(irType.toKotlinType(), sw)

    fun mapClass(irClass: IrClass) = kotlinTypeMapper.mapClass(irClass.descriptor)

    fun classInternalName(irClass: IrClass) = kotlinTypeMapper.classInternalName(irClass.descriptor)
}