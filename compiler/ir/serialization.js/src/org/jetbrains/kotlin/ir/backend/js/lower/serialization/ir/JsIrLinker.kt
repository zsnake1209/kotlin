/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir

import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.backend.common.serialization.*
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.konan.kotlinLibrary
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.resolve.descriptorUtil.isPublishedApi

class JsIrLinker(
    currentModule: ModuleDescriptor,
    mangler: KotlinMangler,
    logger: LoggingContext,
    builtIns: IrBuiltIns,
    symbolTable: SymbolTable
) : KotlinIrLinker(logger, builtIns, symbolTable, emptyList(), null, PUBLIC_LOCAL_UNIQ_ID_EDGE),
    DescriptorUniqIdAware by DeserializedDescriptorUniqIdAware {

    override val descriptorReferenceDeserializer =
        JsDescriptorReferenceDeserializer(currentModule, mangler, builtIns)

    override fun reader(klib: KotlinLibrary, fileIndex: Int, uniqId: UniqId) = klib.irDeclaration(uniqId.index, fileIndex)

    override fun readSymbol(klib: KotlinLibrary, fileIndex: Int, symbolIndex: Int) = klib.symbol(symbolIndex, fileIndex)

    override fun readType(klib: KotlinLibrary, fileIndex: Int, typeIndex: Int) = klib.type(typeIndex, fileIndex)

    override fun readString(klib: KotlinLibrary, fileIndex: Int, stringIndex: Int) = klib.string(stringIndex, fileIndex)

    override fun readBody(klib: KotlinLibrary, fileIndex: Int, bodyIndex: Int) = klib.body(bodyIndex, fileIndex)

    override fun readFile(klib: KotlinLibrary, fileIndex: Int) = klib.file(fileIndex)

    override fun readFileCount(klib: KotlinLibrary) = klib.fileCount()

    override fun checkAccessibility(declarationDescriptor: DeclarationDescriptor): Boolean {
        require(declarationDescriptor is DeclarationDescriptorWithVisibility)
        return declarationDescriptor.isPublishedApi() || declarationDescriptor.visibility.let { it.isPublicAPI || it == Visibilities.INTERNAL }
    }

    override fun descriptorToKotlinLibrary(moduleDescriptor: ModuleDescriptor) = moduleDescriptor.kotlinLibrary
}