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

package org.jetbrains.kotlin.ir.backend.js.lower.serialization

import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.backend.common.descriptors.*
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.ir.SourceManager
import org.jetbrains.kotlin.ir.SourceRangeInfo
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedCallableMemberDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.serialization.js.JsSerializerProtocol
import java.io.File


class KonanIrModuleDeserializer(
    currentModule: ModuleDescriptor,
    logger: LoggingContext,
    builtIns: IrBuiltIns,
    symbolTable: SymbolTable,
    val libraryDir: File,
    val forwardModuleDescriptor: ModuleDescriptor?)
        : IrModuleDeserializer(logger, builtIns, symbolTable) {

    val deserializedSymbols = mutableMapOf<UniqIdKey, IrSymbol>()
    val reachableTopLevels = mutableSetOf<UniqIdKey>()
    val deserializedTopLevels = mutableSetOf<UniqIdKey>()
    val forwardDeclarations = mutableSetOf<IrSymbol>()

    var deserializedModuleDescriptor: ModuleDescriptor? = null
    val resolvedForwardDeclarations = mutableMapOf<UniqIdKey, UniqIdKey>()
    val descriptorReferenceDeserializer = DescriptorReferenceDeserializer(currentModule, resolvedForwardDeclarations)


    init {
        var currentIndex = 0L
        builtIns.knownBuiltins.forEach {
            deserializedSymbols.put(UniqIdKey(null, UniqId(currentIndex, isLocal = false)), it.symbol)
            assert(symbolTable.referenceSimpleFunction(it.descriptor) == it.symbol)
            currentIndex++
        }
    }

    private fun referenceDeserializedSymbol(proto: KonanIr.IrSymbol, descriptor: DeclarationDescriptor?): IrSymbol = when (proto.kind) {
        KonanIr.IrSymbolKind.ANONYMOUS_INIT_SYMBOL ->
            IrAnonymousInitializerSymbolImpl(
                descriptor as ClassDescriptor?
                    ?: WrappedClassDescriptor()
            )
        KonanIr.IrSymbolKind.CLASS_SYMBOL ->
            symbolTable.referenceClass(
                descriptor as ClassDescriptor?
                    ?: WrappedClassDescriptor()
            )
        KonanIr.IrSymbolKind.CONSTRUCTOR_SYMBOL ->
            symbolTable.referenceConstructor(
                descriptor as ClassConstructorDescriptor?
                    ?: WrappedClassConstructorDescriptor()
            )
        KonanIr.IrSymbolKind.TYPE_PARAMETER_SYMBOL ->
            symbolTable.referenceTypeParameter(
                descriptor as TypeParameterDescriptor?
                    ?: WrappedTypeParameterDescriptor()
            )
        KonanIr.IrSymbolKind.ENUM_ENTRY_SYMBOL ->
            symbolTable.referenceEnumEntry(
                descriptor as ClassDescriptor?
                    ?: WrappedEnumEntryDescriptor()
            )
        KonanIr.IrSymbolKind.STANDALONE_FIELD_SYMBOL ->
            IrFieldSymbolImpl(WrappedFieldDescriptor())

        KonanIr.IrSymbolKind.FIELD_SYMBOL ->
            symbolTable.referenceField(
                descriptor as PropertyDescriptor?
                    ?: WrappedPropertyDescriptor()
            )
        KonanIr.IrSymbolKind.FUNCTION_SYMBOL ->
            symbolTable.referenceSimpleFunction(
                descriptor as FunctionDescriptor?
                    ?: WrappedSimpleFunctionDescriptor()
            )
        KonanIr.IrSymbolKind.VARIABLE_SYMBOL ->
            IrVariableSymbolImpl(
                descriptor as VariableDescriptor?
                    ?: WrappedVariableDescriptor()
            )
        KonanIr.IrSymbolKind.VALUE_PARAMETER_SYMBOL ->
            IrValueParameterSymbolImpl(
                descriptor as ParameterDescriptor?
                    ?: WrappedValueParameterDescriptor()
            )
        KonanIr.IrSymbolKind.RECEIVER_PARAMETER_SYMBOL ->
            IrValueParameterSymbolImpl(
                descriptor as ParameterDescriptor? ?: WrappedReceiverParameterDescriptor()
            )
        else -> TODO("Unexpected classifier symbol kind: ${proto.kind}")
    }

    override fun deserializeIrSymbol(proto: KonanIr.IrSymbol): IrSymbol {
        val key = proto.uniqId.uniqIdKey(deserializedModuleDescriptor!!)
        val topLevelKey = proto.topLevelUniqId.uniqIdKey(deserializedModuleDescriptor!!)

        if (!deserializedTopLevels.contains(topLevelKey)) reachableTopLevels.add(topLevelKey)

        val symbol = deserializedSymbols.getOrPut(key) {
            val descriptor = if (proto.hasDescriptorReference()) {
                deserializeDescriptorReference(proto.descriptorReference)
            } else {
                null
            }

            resolvedForwardDeclarations[key]?.let {
                if (!deserializedTopLevels.contains(it)) reachableTopLevels.add(it) // Assuming forward declarations are always top levels.
            }

            referenceDeserializedSymbol(proto, descriptor)
        }

        if (symbol.descriptor is ClassDescriptor &&
            symbol.descriptor !is WrappedDeclarationDescriptor<*> &&
            symbol.descriptor.module.isForwardDeclarationModule
        ) {
            forwardDeclarations.add(symbol)
        }

        return symbol
    }

    override fun deserializeDescriptorReference(proto: KonanIr.DescriptorReference)
        = descriptorReferenceDeserializer.deserializeDescriptorReference(proto)

    private val ByteArray.codedInputStream: org.jetbrains.kotlin.protobuf.CodedInputStream
        get() {
            val codedInputStream = org.jetbrains.kotlin.protobuf.CodedInputStream.newInstance(this)
            codedInputStream.setRecursionLimit(65535) // The default 64 is blatantly not enough for IR.
            return codedInputStream
        }

    private val reversedFileIndex = mutableMapOf<UniqIdKey, IrFile>()

    private val UniqIdKey.moduleOfOrigin get() =
        this.moduleDescriptor ?: reversedFileIndex[this]?.packageFragmentDescriptor?.containingDeclaration

    private fun deserializeTopLevelDeclaration(uniqIdKey: UniqIdKey): IrDeclaration {
        val proto = loadTopLevelDeclarationProto(uniqIdKey)
        return deserializeDeclaration(proto, reversedFileIndex[uniqIdKey]!!)
    }

//    private fun reader(moduleDescriptor: ModuleDescriptor, uniqId: UniqId) = moduleToLibrary[moduleDescriptor]!!.irDeclaration(uniqId.index, uniqId.isLocal)

    private fun loadTopLevelDeclarationProto(uniqIdKey: UniqIdKey): KonanIr.IrDeclaration {
//        TODO()
        fun toFileName(id: UniqId) = "${id.index}${if (id.isLocal) "L" else "G"}.kjd"
//        val stream = reader(uniqIdKey.moduleOfOrigin!!, uniqIdKey.uniqId).codedInputStream
        val stream = File(libraryDir, toFileName(uniqIdKey.uniqId)).readBytes()
        return KonanIr.IrDeclaration.parseFrom(stream, JsSerializerProtocol.extensionRegistry)
    }

    private fun findDeserializedDeclarationForDescriptor(descriptor: DeclarationDescriptor): DeclarationDescriptor? {
        val topLevelDescriptor = descriptor.findTopLevelDescriptor()

        if (topLevelDescriptor.module.isForwardDeclarationModule) return null

        if (topLevelDescriptor !is DeserializedClassDescriptor && topLevelDescriptor !is DeserializedCallableMemberDescriptor) {
            return null
        }

        val descriptorUniqId = topLevelDescriptor.getUniqId()
            ?: error("could not get descriptor uniq id for $topLevelDescriptor")
        val uniqId = UniqId(descriptorUniqId.index, isLocal = false)
        val topLevelKey = UniqIdKey(topLevelDescriptor.module, uniqId)

        reachableTopLevels.add(topLevelKey)

        // TODO: This is a mess. Cleanup!
        do {
            val key = reachableTopLevels.first()

            if (deserializedSymbols[key]?.isBound == true) {
                reachableTopLevels.remove(key)
                continue
            }

            val previousModuleDescriptor = deserializedModuleDescriptor
            deserializedModuleDescriptor = key.moduleOfOrigin

            if (deserializedModuleDescriptor == null) {
                deserializedModuleDescriptor = previousModuleDescriptor
                reachableTopLevels.remove(key)
                deserializedTopLevels.add(key)
                continue
            }

            val reachable = deserializeTopLevelDeclaration(key)

            deserializedModuleDescriptor = previousModuleDescriptor

            reversedFileIndex[key]!!.declarations.add(reachable)
            reachableTopLevels.remove(key)
            deserializedTopLevels.add(key)
        } while (reachableTopLevels.isNotEmpty())

        return topLevelDescriptor
    }

    override fun findDeserializedDeclaration(symbol: IrSymbol): IrDeclaration? {

        if (!symbol.isBound) {
            val topLevelDesecriptor = findDeserializedDeclarationForDescriptor(symbol.descriptor)
            if (topLevelDesecriptor == null) return null
        }

        assert(symbol.isBound) {
            println("findDeserializedDeclaration: symbol ${symbol} is unbound, descriptor = ${symbol.descriptor}, hash = ${symbol.descriptor.hashCode()}")
        }

        return symbol.owner as IrDeclaration
    }

    override fun findDeserializedDeclaration(propertyDescriptor: PropertyDescriptor): IrProperty? {
        val topLevelDesecriptor = findDeserializedDeclarationForDescriptor(propertyDescriptor)
        if (topLevelDesecriptor == null) return null

        return symbolTable.propertyTable[propertyDescriptor]
            ?: error("findDeserializedDeclaration: property descriptor $propertyDescriptor} is not present in propertyTable after deserialization}")
    }

    override fun declareForwardDeclarations() {
        if (forwardModuleDescriptor == null) return

        val packageFragments = forwardDeclarations.map { it.descriptor.findPackage() }.distinct()

        val files = packageFragments.map { packageFragment ->
            val symbol = IrFileSymbolImpl(packageFragment)
            val file = IrFileImpl(NaiveSourceBasedFileEntryImpl("forward declarations pseudo-file"), symbol)
            val symbols = forwardDeclarations
                .filter { !it.isBound }
                .filter { it.descriptor.findPackage() == packageFragment }
            val declarations = symbols.map {

                val declaration = symbolTable.declareClass(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irrelevantOrigin,
                    it.descriptor as ClassDescriptor,
                    { symbol: IrClassSymbol -> IrClassImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irrelevantOrigin, symbol) }
                )
                declaration

            }
            file.declarations.addAll(declarations)
            file
        }
        IrModuleFragmentImpl(forwardModuleDescriptor, builtIns, files)
    }

    fun deserializeIrFile(fileProto: KonanIr.IrFile, moduleDescriptor: ModuleDescriptor, deserializeAllDeclarations: Boolean): IrFile {
        val fileEntry = NaiveSourceBasedFileEntryImpl(fileProto.fileEntry.name)

        // TODO: we need to store "" in protobuf, I suppose. Or better yet, reuse fqname storage from metadata.
        val fqName = if (fileProto.fqName == "<root>") FqName.ROOT else FqName(fileProto.fqName)

        val packageFragmentDescriptor = EmptyPackageFragmentDescriptor(moduleDescriptor, fqName)

        val symbol = IrFileSymbolImpl(packageFragmentDescriptor)
        val file = IrFileImpl(fileEntry, symbol, fqName)

        fileProto.declarationIdList.forEach {
            val uniqIdKey = it.uniqIdKey(moduleDescriptor)
            reversedFileIndex.put(uniqIdKey, file)

            if (deserializeAllDeclarations) {
                file.declarations.add(deserializeTopLevelDeclaration(uniqIdKey))
            }
        }

        return file
    }

    fun deserializeIrModule(proto: KonanIr.IrModule, moduleDescriptor: ModuleDescriptor, deserializeAllDeclarations: Boolean): IrModuleFragment {

        deserializedModuleDescriptor = moduleDescriptor

        val files = proto.fileList.map {
            deserializeIrFile(it, moduleDescriptor, deserializeAllDeclarations)

        }
        val module = IrModuleFragmentImpl(moduleDescriptor, builtIns, files)
        module.patchDeclarationParents(null)
        return module
    }

    fun deserializeIrModule(moduleDescriptor: ModuleDescriptor, byteArray: ByteArray, deserializeAllDeclarations: Boolean = false): IrModuleFragment {
        val proto = KonanIr.IrModule.parseFrom(byteArray.codedInputStream, JsSerializerProtocol.extensionRegistry)
        return deserializeIrModule(proto, moduleDescriptor, deserializeAllDeclarations)
    }
}

internal const val SYNTHETIC_OFFSET = -2

class NaiveSourceBasedFileEntryImpl(override val name: String) : SourceManager.FileEntry {

    private val lineStartOffsets: IntArray

    //-------------------------------------------------------------------------//

    init {
        val file = File(name)
        if (file.isFile) {
            // TODO: could be incorrect, if file is not in system's line terminator format.
            // Maybe use (0..document.lineCount - 1)
            //                .map { document.getLineStartOffset(it) }
            //                .toIntArray()
            // as in PSI.
            val separatorLength = System.lineSeparator().length
            val buffer = mutableListOf<Int>()
            var currentOffset = 0
            file.forEachLine { line ->
                buffer.add(currentOffset)
                currentOffset += line.length + separatorLength
            }
            buffer.add(currentOffset)
            lineStartOffsets = buffer.toIntArray()
        } else {
            lineStartOffsets = IntArray(0)
        }
    }

    //-------------------------------------------------------------------------//

    override fun getLineNumber(offset: Int): Int {
        assert(offset != UNDEFINED_OFFSET)
        if (offset == SYNTHETIC_OFFSET) return 0
        val index = lineStartOffsets.binarySearch(offset)
        return if (index >= 0) index else -index - 2
    }

    //-------------------------------------------------------------------------//

    override fun getColumnNumber(offset: Int): Int {
        assert(offset != UNDEFINED_OFFSET)
        if (offset == SYNTHETIC_OFFSET) return 0
        var lineNumber = getLineNumber(offset)
        return offset - lineStartOffsets[lineNumber]
    }

    //-------------------------------------------------------------------------//

    override val maxOffset: Int
        //get() = TODO("not implemented")
        get() = UNDEFINED_OFFSET

    override fun getSourceRangeInfo(beginOffset: Int, endOffset: Int): SourceRangeInfo {
        //TODO("not implemented")
        return SourceRangeInfo(name, beginOffset, -1, -1, endOffset, -1, -1)

    }
}