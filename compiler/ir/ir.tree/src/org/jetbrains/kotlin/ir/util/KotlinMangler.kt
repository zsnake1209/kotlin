/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.util

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.name.Name

interface KotlinMangler {
    val String.hashMangle: Long
    val IrDeclaration.hashedMangle: Long
    fun IrDeclaration.isExported(): Boolean
    val IrFunction.functionName: String
    val IrDeclaration.mangleString: String

    val manglerName: String

    interface DescriptorMangler {

        fun String.hashMangle(): Long

        fun isExport(declarationDescriptor: DeclarationDescriptor): Boolean
        fun isExportEnumEntry(declarationDescriptor: ClassDescriptor): Boolean

        fun mangleDeclaration(descriptor: DeclarationDescriptor): String
        fun mangleEnumEntry(descriptor: ClassDescriptor): String
    }

    companion object {
        private val FUNCTION_PREFIX = "<BUILT-IN-FUNCTION>"
        fun functionClassSymbolName(name: Name) = "ktype:$FUNCTION_PREFIX$name"
    }
}