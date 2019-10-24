/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.serialization

import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.backend.common.descriptors.*
import org.jetbrains.kotlin.backend.common.ir.createParameterDeclarations
import org.jetbrains.kotlin.backend.common.serialization.*
import org.jetbrains.kotlin.backend.jvm.serialization.proto.JvmIr
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.builders.declarations.buildClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrLoopBase
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrAnonymousInitializerSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrLocalDelegatedPropertySymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.load.java.JavaVisibilities
import org.jetbrains.kotlin.load.kotlin.JvmPackagePartSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedMemberDescriptor
import org.jetbrains.kotlin.backend.common.serialization.proto.DescriptorReference as ProtoDescriptorReference
import org.jetbrains.kotlin.backend.common.serialization.proto.IrSymbolData as ProtoSymbolData
import org.jetbrains.kotlin.backend.common.serialization.proto.IrSymbolKind as ProtoSymbolKind
import org.jetbrains.kotlin.backend.common.serialization.proto.Visibility as ProtoVisibility

class JvmIrDeserializer(
    private val logger: LoggingContext,
    private val builtIns: IrBuiltIns,
    private val symbolTable: SymbolTable,
    private val stubGenerator: DeclarationStubGenerator
) : IrDeserializer {

    private val knownToplevelFqNames = mutableMapOf<UniqId, FqName>()

    private val deserializedSymbols = mutableMapOf<UniqId, IrSymbol>()

    private val externalReferences = mutableMapOf<UniqId, Pair<JvmIr.JvmExternalPackage, FileDeserializer>>()

    override fun getDeclaration(symbol: IrSymbol): IrDeclaration? {
        assert(!symbol.isBound)
        val descriptor =
            symbol.descriptor as? DeserializedMemberDescriptor ?: symbol.descriptor as? DeserializedClassDescriptor
            ?: return null

        val toplevelDescriptor = descriptor.findTopLevelDescriptor()
        val packageFragment =
            symbolTable.findOrDeclareExternalPackageFragment(toplevelDescriptor.containingDeclaration as PackageFragmentDescriptor)

        if (toplevelDescriptor is ClassDescriptor) {
            val classHeader =
                (toplevelDescriptor.source as? KotlinJvmBinarySourceElement)?.binaryClass?.classHeader ?: return null
            if (classHeader.serializedIr == null || classHeader.serializedIr!!.isEmpty()) return null

            val irProto = JvmIr.JvmIrClass.parseFrom(classHeader.serializedIr)
            val deserializer = FileDeserializer(toplevelDescriptor.module, irProto.auxTables)
            consumeUniqIdTable(irProto.auxTables.uniqIdTable, deserializer)
            consumeExternalRefsTable(irProto.auxTables.externalRefs, deserializer)
            deserializer.deserializeIrClass(irProto.irClass, parent = packageFragment)
            assert(symbol.isBound)
            return symbol.owner as IrDeclaration
        } else {
            val jvmPackagePartSource =
                (toplevelDescriptor as DeserializedMemberDescriptor).containerSource as? JvmPackagePartSource ?: return null
            val classHeader = jvmPackagePartSource.knownJvmBinaryClass?.classHeader ?: return null
            if (classHeader.serializedIr == null || classHeader.serializedIr!!.isEmpty()) return null

            val irProto = JvmIr.JvmIrFile.parseFrom(classHeader.serializedIr)

            val deserializer = FileDeserializer(toplevelDescriptor.module, irProto.auxTables)
            val facadeClass = buildFacadeClass(deserializer, irProto).also {
                it.parent = packageFragment
                packageFragment.declarations.add(it)
            }

            consumeUniqIdTable(irProto.auxTables.uniqIdTable, deserializer)
            consumeExternalRefsTable(irProto.auxTables.externalRefs, deserializer)

            for (declaration in irProto.declarationContainer.declarationList) {
                val member = deserializer.deserializeDeclaration(declaration, parent = facadeClass)
                facadeClass.declarations.add(member)
            }
            assert(symbol.isBound)
            return symbol.owner as IrDeclaration
        }
    }

    private fun consumeUniqIdTable(table: JvmIr.UniqIdTable, fileDeserializer: FileDeserializer) {
        for (entry in table.infosList) {
            val id = UniqId(entry.id)
            val toplevelFqName = fileDeserializer.deserializeFqName(entry.toplevelFqNameList)
            val oldFqName = knownToplevelFqNames[id]
            assert(oldFqName == null || oldFqName == toplevelFqName) { "FqName table clash: $oldFqName vs $toplevelFqName" }
            knownToplevelFqNames[id] = toplevelFqName
        }
    }

    private fun buildFacadeClass(fileDeserializer: IrFileDeserializer, proto: JvmIr.JvmIrFile): IrClass = buildClass {
        origin = IrDeclarationOrigin.FILE_CLASS
        name = fileDeserializer.deserializeFqName(proto.facadeFqNameList).shortName()
    }.apply {
        createParameterDeclarations()
        // TODO: annotations
    }


    /* External references are deserialized lazily, as the last resource for when there is no descriptor available for a given symbol ref */
    private fun consumeExternalRefsTable(table: JvmIr.ExternalRefs, deserializer: FileDeserializer) {
        for (reference in table.referenceList) {
            externalReferences[UniqId(reference.id)] = Pair(table.packageList[reference.index], deserializer)
        }
    }

    override fun declareForwardDeclarations() {}

    inner class FileDeserializer(
        val moduleDescriptor: ModuleDescriptor,
        private val auxTables: JvmIr.AuxTables
    ) : IrFileDeserializer(logger, builtIns, symbolTable) {

        private val uniqIdAware = JvmDescriptorUniqIdAware(symbolTable, stubGenerator)

        private val descriptorReferenceDeserializer = JvmDescriptorReferenceDeserializer(moduleDescriptor, uniqIdAware)

        private var moduleLoops = mutableMapOf<Int, IrLoopBase>()

        private val localSymbols = mutableMapOf<UniqId, IrSymbol>()

        private fun referenceDeserializedSymbol(proto: ProtoSymbolData, descriptor: DeclarationDescriptor?) = when (proto.kind) {
            ProtoSymbolKind.ANONYMOUS_INIT_SYMBOL ->
                IrAnonymousInitializerSymbolImpl(
                    descriptor as ClassDescriptor?
                        ?: WrappedClassDescriptor()
                )
            ProtoSymbolKind.CLASS_SYMBOL ->
                (descriptor as? ClassDescriptor)?.let { symbolTable.referenceClass(it) }
                    ?: symbolTable.referenceClass(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.CONSTRUCTOR_SYMBOL ->
                (descriptor as? ClassConstructorDescriptor)?.let { symbolTable.referenceConstructor(it) }
                    ?: symbolTable.referenceConstructor(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.TYPE_PARAMETER_SYMBOL ->
                (descriptor as? TypeParameterDescriptor)?.let { symbolTable.referenceTypeParameter(it) }
                    ?: symbolTable.referenceTypeParameter(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.ENUM_ENTRY_SYMBOL ->
                (descriptor as? ClassDescriptor)?.let { symbolTable.referenceEnumEntry(it) }
                    ?: symbolTable.referenceEnumEntry(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.STANDALONE_FIELD_SYMBOL ->
                symbolTable.referenceField(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.FIELD_SYMBOL ->
                (descriptor as? PropertyDescriptor)?.let { symbolTable.referenceField(it) }
                    ?: symbolTable.referenceField(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.FUNCTION_SYMBOL ->
                (descriptor as? FunctionDescriptor)?.let { symbolTable.referenceSimpleFunction(it) }
                    ?: symbolTable.referenceSimpleFunction(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.VARIABLE_SYMBOL ->
                IrVariableSymbolImpl(
                    descriptor as? VariableDescriptor ?: WrappedVariableDescriptor()
                )
            ProtoSymbolKind.VALUE_PARAMETER_SYMBOL ->
                IrValueParameterSymbolImpl(
                    descriptor as? ParameterDescriptor ?: WrappedValueParameterDescriptor()
                )
            ProtoSymbolKind.RECEIVER_PARAMETER_SYMBOL ->
                IrValueParameterSymbolImpl(
                    descriptor as ParameterDescriptor? ?: WrappedReceiverParameterDescriptor()
                )
            ProtoSymbolKind.PROPERTY_SYMBOL ->
                (descriptor as? PropertyDescriptor)?.let { symbolTable.referenceProperty(it) }
                    ?: symbolTable.referenceProperty(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.TYPEALIAS_SYMBOL ->
                (descriptor as? TypeAliasDescriptor)?.let { symbolTable.referenceTypeAlias(it) }
                    ?: symbolTable.referenceTypeAlias(UniqId(proto.uniqIdIndex))
            ProtoSymbolKind.LOCAL_DELEGATED_PROPERTY_SYMBOL ->
                IrLocalDelegatedPropertySymbolImpl(
                    descriptor as? VariableDescriptorWithAccessors ?: WrappedVariableDescriptorWithAccessor()
                )
            else -> TODO("Unexpected classifier symbol kind: ${proto.kind}")
        }

        override fun deserializeString(index: Int): String {
            return auxTables.stringTable.getString(index)
        }

        override fun deserializeIrType(index: Int): IrType {
            val typeData = auxTables.typeTable.getTypes(index)
            return deserializeIrTypeData(typeData)
        }

        override fun deserializeIrSymbol(index: Int): IrSymbol {
            val symbolData = auxTables.symbolTable.getSymbol(index)
            return deserializeIrSymbolData(symbolData)
        }

        private fun deserializeIrSymbolData(proto: ProtoSymbolData): IrSymbol {
            val key = UniqId(proto.uniqIdIndex)
            val table = if (key.isLocal) localSymbols else deserializedSymbols
            return table.getOrPut(key) {
                val descriptor = if (proto.hasDescriptorReference()) {
                    deserializeDescriptorReference(proto.descriptorReference)
                } else {
                    null
                }

                referenceDeserializedSymbol(proto, descriptor)
            }
        }

        override fun deserializeDescriptorReference(proto: ProtoDescriptorReference) =
            descriptorReferenceDeserializer.deserializeDescriptorReference(
                deserializeFqName(proto.packageFqNameList),
                deserializeFqName(proto.classFqNameList),
                deserializeString(proto.name),
                proto.flags,
                if (proto.hasUniqIdIndex()) proto.uniqIdIndex else null
            )

        override fun deserializeLoopHeader(loopIndex: Int, loopBuilder: () -> IrLoopBase) =
            moduleLoops.getOrPut(loopIndex, loopBuilder)

        override fun deserializeExpressionBody(index: Int): IrExpression {
            val bodyData = auxTables.statementsAndExpressionsTable.getStatementOrExpression(index)
            require(bodyData.hasExpression())
            return deserializeExpression(bodyData.expression)
        }

        override fun deserializeStatementBody(index: Int): IrElement {
            val bodyData = auxTables.statementsAndExpressionsTable.getStatementOrExpression(index)
            require(bodyData.hasStatement())
            return deserializeStatement(bodyData.statement)
        }

        override fun deserializeVisibility(value: ProtoVisibility): Visibility = when (deserializeString(value.name)) {
            "package" -> JavaVisibilities.PACKAGE_VISIBILITY
            "protected_static" -> JavaVisibilities.PROTECTED_STATIC_VISIBILITY
            "protected_and_package" -> JavaVisibilities.PROTECTED_AND_PACKAGE
            else -> super.deserializeVisibility(value)
        }
    }

    // External references are pulled from externalRefsTable as the last resort
    val externalReferenceProvider = object : IrProvider {
        override fun getDeclaration(symbol: IrSymbol): IrDeclaration? {
            assert(symbol.uniqId != UniqId.NONE)
            assert(!symbol.uniqId.isLocal)
            val (externalPackageProto, deserializer) = externalReferences[symbol.uniqId]
                ?: error("External reference absent from external references table: $symbol")
            val packageFragment = IrExternalPackageFragmentImpl(
                DescriptorlessExternalPackageFragmentSymbol(),
                deserializer.deserializeFqName(externalPackageProto.fqNameList)
            )
            for (memberProto in externalPackageProto.declarationContainer.declarationList) {
                val toplevel = deserializer.deserializeDeclaration(memberProto, packageFragment)
                packageFragment.declarations.add(toplevel)
            }
            return deserializedSymbols[symbol.uniqId]?.owner as? IrDeclaration
                ?: error("Symbol unbound even after deserializing external reference")
        }
    }
}