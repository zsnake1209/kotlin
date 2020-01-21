/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization

import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.EmptyPackageFragmentDescriptor
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrClassImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrFileImpl
import org.jetbrains.kotlin.ir.declarations.impl.IrModuleFragmentImpl
import org.jetbrains.kotlin.ir.descriptors.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrErrorExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrLoopBase
import org.jetbrains.kotlin.ir.symbols.*
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.impl.IrErrorTypeImpl
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.protobuf.ExtensionRegistryLite.newInstance
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.backend.common.serialization.proto.IrDeclaration as ProtoDeclaration
import org.jetbrains.kotlin.backend.common.serialization.proto.IrFile as ProtoFile
import org.jetbrains.kotlin.backend.common.serialization.proto.IrSymbolData as ProtoSymbolData
import org.jetbrains.kotlin.backend.common.serialization.proto.IrSymbolKind as ProtoSymbolKind
import org.jetbrains.kotlin.backend.common.serialization.proto.IrType as ProtoType
import org.jetbrains.kotlin.backend.common.serialization.proto.IrStatement as ProtoStatement
import org.jetbrains.kotlin.backend.common.serialization.proto.IrExpression as ProtoExpression
import org.jetbrains.kotlin.backend.common.serialization.proto.IrConstructorCall as ProtoConstructorCall
import org.jetbrains.kotlin.backend.common.serialization.proto.Actual as ProtoActual
import org.jetbrains.kotlin.backend.common.serialization.proto.IdSignature as ProtoIdSignature

abstract class KotlinIrLinker(
    val logger: LoggingContext,
    val builtIns: IrBuiltIns,
    val symbolTable: SymbolTable,
    private val exportedDependencies: List<ModuleDescriptor>,
    private val forwardModuleDescriptor: ModuleDescriptor?,
    mangler: KotlinMangler
) : IrDeserializer {

    private val expectUniqIdToActualUniqId = mutableMapOf<IdSignature, IdSignature>()
    private val topLevelActualUniqItToDeserializer = mutableMapOf<IdSignature, IrModuleDeserializer>()
    private val expectSymbols = mutableMapOf<IdSignature, IrSymbol>()
    private val actualSymbols = mutableMapOf<IdSignature, IrSymbol>()

    sealed class DeserializationState<T> {
        val deserializedSymbols = mutableMapOf<IdSignature, IrSymbol>()

        operator fun contains(key: IdSignature) = key in deserializedSymbols
        operator fun get(key: IdSignature): IrSymbol = deserializedSymbols[key] ?: error("No deserialized symbol found for $key")

        abstract fun addIdSignature(key: IdSignature)
        abstract fun processPendingDeclarations(processor: (T) -> Unit)

        class ModuleDeserializationState(val module: IrModuleDeserializer) :
            DeserializationState<IrModuleDeserializer.IrDeserializerForFile>() {
            private val filesWithPendingTopLevels = mutableSetOf<IrModuleDeserializer.IrDeserializerForFile>()

            fun enqueueFile(fileDeserializer: IrModuleDeserializer.IrDeserializerForFile) {
                filesWithPendingTopLevels.add(fileDeserializer)
                module.enqueueModule()
            }

            override fun addIdSignature(key: IdSignature) {
                val fileDeserializer = module.moduleReversedFileIndex[key] ?: error("No file found for key $key")
                fileDeserializer.fileLocalDeserializationState.addIdSignature(key)

                enqueueFile(fileDeserializer)
            }

            override fun processPendingDeclarations(processor: (IrModuleDeserializer.IrDeserializerForFile) -> Unit) {
                while (filesWithPendingTopLevels.isNotEmpty()) {
                    val pendingDeserializer = filesWithPendingTopLevels.first()

                    processor(pendingDeserializer)

                    filesWithPendingTopLevels.remove(pendingDeserializer)
                }
            }
        }

        class SimpleDeserializationState : DeserializationState<IdSignature>() {
            private val reachableTopLevels = LinkedHashSet<IdSignature>()

            override fun addIdSignature(key: IdSignature) {
                reachableTopLevels.add(key)
            }

            override fun processPendingDeclarations(processor: (IdSignature) -> Unit) {
                while (reachableTopLevels.isNotEmpty()) {
                    val reachableKey = reachableTopLevels.first()

                    if (deserializedSymbols[reachableKey]?.isBound != true) {
                        processor(reachableKey)
                    }

                    reachableTopLevels.remove(reachableKey)
                }
            }
        }
    }

    protected val globalDeserializationState = DeserializationState.SimpleDeserializationState()
    private val modulesWithReachableTopLevels = mutableSetOf<IrModuleDeserializer>()

    //TODO: This is Native specific. Eliminate me.
    private val forwardDeclarations = mutableSetOf<IrSymbol>()
    val resolvedForwardDeclarations = mutableMapOf<IdSignature, IdSignature>()

    protected val deserializersForModules = mutableMapOf<ModuleDescriptor, IrModuleDeserializer>()

    private fun getForwardDeclararationModuleDeserializer() =
        deserializersForModules.entries.single { it.key.isForwardDeclarationModule }.value

    inner class IrModuleDeserializer(
        private val moduleDescriptor: ModuleDescriptor,
        private val strategy: DeserializationStrategy
    ) {

        val fileToDeserializerMap = mutableMapOf<IrFile, IrDeserializerForFile>()

        protected val moduleResolvedForwardDeclarations = mutableMapOf<IdSignature, IdSignature>()

        private val moduleDeserializationState = DeserializationState.ModuleDeserializationState(this)
        val moduleReversedFileIndex = mutableMapOf<IdSignature, IrDeserializerForFile>()
        private val moduleDependencies by lazy {
            moduleDescriptor.allDependencyModules.filter { it != moduleDescriptor }.map { deserializersForModules[it]!! }
        }

        // This is a heavy initializer
        val module = deserializeIrModuleHeader()

        inner class IrDeserializerForFile(
            private var annotations: List<ProtoConstructorCall>?,
            private val actuals: List<ProtoActual>,
            private val fileIndex: Int,
            onlyHeaders: Boolean
        ) : IrFileDeserializer(logger, builtIns, symbolTable) {

            private var fileLoops = mutableMapOf<Int, IrLoopBase>()

            private val symbolProtosCache = mutableMapOf<Int, ProtoSymbolData>()
            private val typeProtosCache = mutableMapOf<Int, ProtoType>()
            private val stringsCache = mutableMapOf<Int, String>()

            lateinit var file: IrFile

            private val deserializeBodies: Boolean = !onlyHeaders

            var reversedSignatureIndex = emptyMap<IdSignature, Int>()

            private val fileLocalResolvedForwardDeclarations = mutableMapOf<IdSignature, IdSignature>()

            val fileLocalDeserializationState = DeserializationState.SimpleDeserializationState()

            fun  deserializeDeclaration(idSig: IdSignature): IrDeclaration {
                return deserializeDeclaration(loadTopLevelDeclarationProto(idSig), file)
            }

            fun deserializeExpectActualMapping() {
                actuals.forEach {
                    val expectSymbol = loadSymbolProto(it.expectSymbol)
                    val actualSymbol = loadSymbolProto(it.actualSymbol)

                    val expect = deserializeIdSignature(expectSymbol.idSig)
                    val actual = deserializeIdSignature(actualSymbol.idSig)

                    assert(expectUniqIdToActualUniqId[expect] == null) {
                        "Expect uniqid $expect is already actualized by ${expectUniqIdToActualUniqId[expect]}, while we try to record $actual"
                    }
                    expectUniqIdToActualUniqId.put(expect, actual)
                    // Non-null only for topLevel declarations.
                    getModuleForTopLevelId(actual)?. let { topLevelActualUniqItToDeserializer.put(actual, it) }
                }
            }

            private fun resolveSignatureIndex(idSig: IdSignature): Int {
                return reversedSignatureIndex[idSig] ?:
                error("Not found Idx for $idSig")
            }

            private fun loadTopLevelDeclarationProto(idSig: IdSignature): ProtoDeclaration {
                val idSigIndex = resolveSignatureIndex(idSig)
                val stream = reader(moduleDescriptor, fileIndex, idSigIndex).codedInputStream
                return ProtoDeclaration.parseFrom(stream, newInstance())
            }

            private fun loadSymbolProto(index: Int): ProtoSymbolData {
                val stream = readSymbol(moduleDescriptor, fileIndex, index).codedInputStream
                return ProtoSymbolData.parseFrom(stream, newInstance())
            }

            private fun loadTypeProto(index: Int): ProtoType {
                val stream = readType(moduleDescriptor, fileIndex, index).codedInputStream
                return ProtoType.parseFrom(stream, newInstance())
            }

            private fun loadSignatureProto(index: Int): ProtoIdSignature {
                val stream = readSignature(moduleDescriptor, fileIndex, index).codedInputStream
                return ProtoIdSignature.parseFrom(stream, newInstance())
            }

            private fun loadStatementBodyProto(index: Int): ProtoStatement {
                val stream = readBody(moduleDescriptor, fileIndex, index).codedInputStream
                return ProtoStatement.parseFrom(stream, newInstance())
            }

            private fun loadExpressionBodyProto(index: Int): ProtoExpression {
                val stream = readBody(moduleDescriptor, fileIndex, index).codedInputStream
                return ProtoExpression.parseFrom(stream, newInstance())
            }

            private fun loadStringProto(index: Int): String {
                return String(readString(moduleDescriptor, fileIndex, index))
            }

            private fun referenceDeserializedSymbol(
                proto: org.jetbrains.kotlin.backend.common.serialization.proto.IrSymbolData,
                idSig: IdSignature,
                descriptor: DeclarationDescriptor?
            ): IrSymbol = symbolTable.run {
                when (proto.kind) {
                    ProtoSymbolKind.ANONYMOUS_INIT_SYMBOL ->
                        IrAnonymousInitializerSymbolImpl(WrappedClassDescriptor()).also { require(idSig.isLocal) }
                    ProtoSymbolKind.CLASS_SYMBOL ->
                        referenceClassFromLinker(descriptor as ClassDescriptor? ?: WrappedClassDescriptor(), idSig) // TODO: FunctionInterfaces
                    ProtoSymbolKind.CONSTRUCTOR_SYMBOL -> referenceConstructorFromLinker(WrappedClassConstructorDescriptor(), idSig)
                    ProtoSymbolKind.TYPE_PARAMETER_SYMBOL -> referenceTypeParameterFromLinker(WrappedTypeParameterDescriptor(), idSig)
                    ProtoSymbolKind.ENUM_ENTRY_SYMBOL -> referenceEnumEntryFromLinker(WrappedEnumEntryDescriptor(), idSig)
                    ProtoSymbolKind.STANDALONE_FIELD_SYMBOL -> referenceFieldFromLinker(WrappedPropertyDescriptor(), idSig)
                    ProtoSymbolKind.FIELD_SYMBOL -> referenceFieldFromLinker(WrappedPropertyDescriptor(), idSig)
                    ProtoSymbolKind.FUNCTION_SYMBOL ->
                        referenceSimpleFunctionFromLinker(descriptor as FunctionDescriptor? ?: WrappedSimpleFunctionDescriptor(), idSig) //TODO: FunctionInterfaces
                    ProtoSymbolKind.TYPEALIAS_SYMBOL -> referenceTypeAliasFromLinker(WrappedTypeAliasDescriptor(), idSig)
                    ProtoSymbolKind.PROPERTY_SYMBOL -> referencePropertyFromLinker(WrappedPropertyDescriptor(), idSig)
                    ProtoSymbolKind.VARIABLE_SYMBOL -> IrVariableSymbolImpl(WrappedVariableDescriptor())
                    ProtoSymbolKind.VALUE_PARAMETER_SYMBOL -> IrValueParameterSymbolImpl(WrappedValueParameterDescriptor())
                    ProtoSymbolKind.RECEIVER_PARAMETER_SYMBOL -> IrValueParameterSymbolImpl(WrappedReceiverParameterDescriptor())
                    ProtoSymbolKind.LOCAL_DELEGATED_PROPERTY_SYMBOL ->
                        IrLocalDelegatedPropertySymbolImpl(WrappedVariableDescriptorWithAccessor())
                    else -> TODO("Unexpected classifier symbol kind: ${proto.kind}")
                }
            }

            private fun loadSymbolData(index: Int): ProtoSymbolData {
                return symbolProtosCache.getOrPut(index) {
                    loadSymbolProto(index)
                }
            }

            private fun loadTypeData(index: Int): ProtoType {
                return typeProtosCache.getOrPut(index) {
                    loadTypeProto(index)
                }
            }

            private fun loadString(index: Int): String {
                return stringsCache.getOrPut(index) {
                    loadStringProto(index)
                }
            }

            private fun isGlobalIdSignature(isSignature: IdSignature): Boolean {
                return isSignature in globalDeserializationState || isSpecialFunctionDescriptor(isSignature)
            }

            private fun getModuleForTopLevelId(isSignature: IdSignature): IrModuleDeserializer? {
                if (isSignature in moduleReversedFileIndex) return this@IrModuleDeserializer
                return moduleDependencies.firstOrNull { isSignature in it.moduleReversedFileIndex }
            }

            private fun getStateForID(isSignature: IdSignature): DeserializationState<*> {
                if (isSignature.isLocal) return fileLocalDeserializationState
                if (isGlobalIdSignature(isSignature)) return globalDeserializationState
                return getModuleForTopLevelId(isSignature)?.moduleDeserializationState ?: handleNoModuleDeserializerFound(isSignature)
            }

            private fun findDeserializationState(proto: ProtoSymbolData): DeserializationState<*> {
                val idSignature = deserializeIdSignature(proto.idSig)
                val topLevelSignature = idSignature.topLevelSignature()

                val topLevelDeserializationState = getStateForID(topLevelSignature)

                if (topLevelSignature !in topLevelDeserializationState) {
                    topLevelDeserializationState.addIdSignature(topLevelSignature)
                }

                // If topLevel declaration is module-public and current declaration in not (e.g. value parameter)
                // they should be proccesed via different tables
                val deserializationState =
                    if (topLevelSignature.isLocal xor idSignature.isLocal) getStateForID(idSignature) else topLevelDeserializationState

                return deserializationState
            }

            private fun referenceIrSymbolData(symbol: IrSymbol, proto: ProtoSymbolData) {
                val signature = deserializeIdSignature(proto.idSig)
                val deserializationState = findDeserializationState(proto)

                deserializationState.deserializedSymbols.putIfAbsent(signature, symbol)
            }

            private fun isSpecialFunctionDescriptor(idSig: IdSignature): Boolean {
                if (idSig !is IdSignature.PublicSignature) return false
                if (idSig.packageFqn !in functionalPackages) return false

                if (idSig.classFqn.isRoot) return false

                val fqnParts = idSig.classFqn.pathSegments()

                val className = fqnParts.first()

                return functionPattern.matcher(className.asString()).find()
            }

            private fun resolveSpecialDescriptor(idSig: IdSignature): DeclarationDescriptor? {
                if (isSpecialFunctionDescriptor(idSig)) {
                    val publicSig = idSig as IdSignature.PublicSignature
                    val fqnParts = publicSig.classFqn.pathSegments()
                    val className = fqnParts.first()

                    val classDescriptor = builtIns.builtIns.getBuiltInClassByFqName(idSig.packageFqn.child(className))
                    return when (fqnParts.size) {
                        1 -> classDescriptor
                        2 -> {
                            val memberName = fqnParts[1]!!
                            val memberDescriptors =
                                classDescriptor.unsubstitutedMemberScope.getContributedDescriptors(DescriptorKindFilter.CALLABLES)
                                    .filter { d -> d.name == memberName }

                            val memberDescriptor = memberDescriptors.single()
                            if (idSig.id != null && memberDescriptor is PropertyDescriptor) TODO("... return accessor")
                            memberDescriptor
                        }
                        else -> error("No member found for signature $idSig")
                    }
                }

                return null
            }

            private fun deserializeIrSymbolData(proto: ProtoSymbolData): IrSymbol {
                val idSignature = deserializeIdSignature(proto.idSig)
                val deserializationState = findDeserializationState(proto)

                val symbol = deserializationState.deserializedSymbols.getOrPut(idSignature) {
                    val descriptor = resolveSpecialDescriptor(idSignature)

                    resolvedForwardDeclarations[idSignature]?.let {
                        val fdState = getStateForID(it)
                        if (it !in fdState) fdState.addIdSignature(it)
                    }

                    val symbol = referenceDeserializedSymbol(proto, idSignature, descriptor).let {
                        if (expectUniqIdToActualUniqId[idSignature] != null) wrapInDelegatedSymbol(it) else it
                    }

                    if (idSignature in expectUniqIdToActualUniqId.keys) expectSymbols.put(idSignature, symbol)
                    if (idSignature in expectUniqIdToActualUniqId.values) actualSymbols.put(idSignature, symbol)

                    symbol
                }
                if (symbol.descriptor is ClassDescriptor &&
                    symbol.descriptor !is WrappedDeclarationDescriptor<*> &&
                    symbol.descriptor.module.isForwardDeclarationModule
                ) {
                    forwardDeclarations.add(symbol)
                }

                return symbol
            }

            override fun deserializeIrSymbolToDeclare(index: Int): Pair<IrSymbol, IdSignature> {
                val symbolData = loadSymbolProto(index)
                return Pair(deserializeIrSymbolData(symbolData), deserializeIdSignature(symbolData.idSig))
            }

            override fun deserializeIrSymbol(index: Int): IrSymbol {
                val symbolData = loadSymbolProto(index)
                return deserializeIrSymbolData(symbolData)
            }

            override fun deserializeSymbolId(index: Int): IdSignature {
                return deserializeIdSignature(loadSymbolData(index).idSig)
            }

            override fun deserializeIrType(index: Int): IrType {
                val typeData = loadTypeProto(index)
                return deserializeIrTypeData(typeData)
            }

            override fun deserializeIdSignature(index: Int): IdSignature {
                val sigData = loadSignatureProto(index)
                return deserializeSignatureData(sigData)
            }

            override fun deserializeString(index: Int): String =
                loadStringProto(index)

            override fun deserializeLoopHeader(loopIndex: Int, loopBuilder: () -> IrLoopBase) =
                fileLoops.getOrPut(loopIndex, loopBuilder)

            override fun deserializeExpressionBody(index: Int): IrExpression {
                return if (deserializeBodies) {
                    val bodyData = loadExpressionBodyProto(index)
                    deserializeExpression(bodyData)
                } else {
                    val errorType = IrErrorTypeImpl(null, emptyList(), Variance.INVARIANT)
                    IrErrorExpressionImpl(-1, -1, errorType, "Expression body is not deserialized yet")
                }
            }

            override fun deserializeStatementBody(index: Int): IrElement {
                if (deserializeBodies) {
                    val bodyData = loadStatementBodyProto(index)
                    return deserializeStatement(bodyData)
                } else {
                    val errorType = IrErrorTypeImpl(null, emptyList(), Variance.INVARIANT)
                    return IrBlockBodyImpl(-1, -1, listOf(IrErrorExpressionImpl(-1, -1, errorType, "Statement body is not deserialized yet")))
                }
            }

            override fun referenceIrSymbol(symbol: IrSymbol, index: Int) {
                val symbolData = loadSymbolProto(index)
                referenceIrSymbolData(symbol, symbolData)
            }

            fun deserializeFileImplicitDataIfFirstUse() {
                annotations?.let {
                    file.annotations.addAll(deserializeAnnotations(it))
                    annotations = null
                }
            }

            fun deserializeAllFileReachableTopLevel() {
                fileLocalDeserializationState.processPendingDeclarations {
                    val declaration = deserializeDeclaration(it)
                    file.declarations.add(declaration)
                }
            }
        }

        private fun deserializeIrFile(fileProto: ProtoFile, fileIndex: Int): IrFile {

            val fileName = fileProto.fileEntry.name

            val fileEntry = NaiveSourceBasedFileEntryImpl(fileName, fileProto.fileEntry.lineStartOffsetsList.toIntArray())

            val fileDeserializer =
                IrDeserializerForFile(fileProto.annotationList, fileProto.actualsList, fileIndex, !strategy.needBodies).apply {

                    // Explicitly exported declarations (e.g. top-level initializers) must be deserialized before all other declarations.
                    // Thus we schedule their deserialization in deserializer's constructor.
                    fileProto.explicitlyExportedToCompilerList.forEach {
                        val sig = deserializeIdSignature(it)
                        assert(!sig.isPackageSignature())
                        fileLocalDeserializationState.addIdSignature(sig.topLevelSignature())
                    }
                }

            val fqName = fileDeserializer.deserializeFqName(fileProto.fqNameList)

            val packageFragmentDescriptor = EmptyPackageFragmentDescriptor(moduleDescriptor, fqName)

            val symbol = IrFileSymbolImpl(packageFragmentDescriptor)
            val file = IrFileImpl(fileEntry, symbol, fqName)

            fileDeserializer.file = file
            fileToDeserializerMap[file] = fileDeserializer

            val fileSignatureIndex = fileProto.declarationIdList.map { fileDeserializer.deserializeIdSignature(it) to it }

            fileSignatureIndex.forEach {
                moduleReversedFileIndex.getOrPut(it.first) { fileDeserializer }
            }

            fileDeserializer.reversedSignatureIndex = fileSignatureIndex.toMap()

            if (strategy.theWholeWorld) {
                for (id in fileSignatureIndex) {
                    moduleDeserializationState.addIdSignature(id.first)
                }
                moduleDeserializationState.enqueueFile(fileDeserializer)
            } else if (strategy.explicitlyExported) {
                moduleDeserializationState.enqueueFile(fileDeserializer)
            }

            return file
        }

        private fun deserializeIrModuleHeader(): IrModuleFragment {
            val fileCount = readFileCount(moduleDescriptor)

            val files = mutableListOf<IrFile>()

            for (i in 0 until fileCount) {
                files.add(deserializeIrFile(ProtoFile.parseFrom(readFile(moduleDescriptor, i), newInstance()), i))
            }

            return IrModuleFragmentImpl(moduleDescriptor, builtIns, files)
        }

        fun deserializeAllModuleReachableTopLevels() {
            moduleDeserializationState.processPendingDeclarations { fileDeserializer ->
                fileDeserializer.deserializeFileImplicitDataIfFirstUse()
                fileDeserializer.deserializeAllFileReachableTopLevel()
            }
        }

        fun enqueueModule() {
            modulesWithReachableTopLevels.add(this)
        }

        fun addModuleReachableTopLevel(idSig: IdSignature) {
            moduleDeserializationState.addIdSignature(idSig)
        }
    }

    private fun loadKnownBuiltinSymbols(mangler: KotlinMangler) {
        val globalDeserializedSymbols = globalDeserializationState.deserializedSymbols
        builtIns.knownBuiltins.forEach {
            val currentIndex = with(mangler) { it.mangle.hashMangle }
            val signature = IdSignature.BuiltInSignature(it.mangle, currentIndex)
            globalDeserializedSymbols[signature] = it.symbol
            assert(symbolTable.referenceBuiltInOperator(it.symbol.descriptor as SimpleFunctionDescriptor, it.mangle).owner === it)
        }
    }

    init {
        loadKnownBuiltinSymbols(mangler)
    }

    private val ByteArray.codedInputStream: org.jetbrains.kotlin.protobuf.CodedInputStream
        get() {
            val codedInputStream = org.jetbrains.kotlin.protobuf.CodedInputStream.newInstance(this)
            codedInputStream.setRecursionLimit(65535) // The default 64 is blatantly not enough for IR.
            return codedInputStream
        }

    protected abstract fun reader(moduleDescriptor: ModuleDescriptor, fileIndex: Int, idSigIndex: Int): ByteArray
    protected abstract fun readSymbol(moduleDescriptor: ModuleDescriptor, fileIndex: Int, symbolIndex: Int): ByteArray
    protected abstract fun readType(moduleDescriptor: ModuleDescriptor, fileIndex: Int, typeIndex: Int): ByteArray
    protected abstract fun readSignature(moduleDescriptor: ModuleDescriptor, fileIndex: Int, signatureIndex: Int): ByteArray
    protected abstract fun readString(moduleDescriptor: ModuleDescriptor, fileIndex: Int, stringIndex: Int): ByteArray
    protected abstract fun readBody(moduleDescriptor: ModuleDescriptor, fileIndex: Int, bodyIndex: Int): ByteArray
    protected abstract fun readFile(moduleDescriptor: ModuleDescriptor, fileIndex: Int): ByteArray
    protected abstract fun readFileCount(moduleDescriptor: ModuleDescriptor): Int

    protected abstract fun checkAccessibility(declarationDescriptor: DeclarationDescriptor): Boolean
    protected open fun handleNoModuleDeserializerFound(isSignature: IdSignature): DeserializationState<*> {
        error("Deserializer for declaration $isSignature is not found")
    }

    /**
     * Check that descriptor shouldn't be processed by some backend-specific logic.
     * For example, it is the case for Native interop libraries where there is no IR in libraries.
     */
    protected open fun DeclarationDescriptor.shouldBeDeserialized(): Boolean = true

    private fun deserializeAllReachableTopLevels() {
        do {
            val moduleDeserializer = modulesWithReachableTopLevels.first()
            modulesWithReachableTopLevels.remove(moduleDeserializer)

            moduleDeserializer.deserializeAllModuleReachableTopLevels()
        } while (modulesWithReachableTopLevels.isNotEmpty())
    }

    private fun findDeserializedDeclarationForDescriptor(symbol: IrSymbol): DeclarationDescriptor? {
        require(symbol.isPublicApi)

        val descriptor = symbol.descriptor

        // This is Native specific. Try to eliminate.
//        if (topLevelDescriptor.module.isForwardDeclarationModule) return null
//        if (!topLevelDescriptor.shouldBeDeserialized()) return null

        if (descriptor is FunctionClassDescriptor || descriptor.containingDeclaration is FunctionClassDescriptor) {
            return null
        }

        val topLevelSignature = symbol.signature.topLevelSignature()
        val moduleOfOrigin = descriptor.module

        val moduleDeserializer = deserializersForModules[moduleOfOrigin] ?: error("No module deserializer found for $moduleOfOrigin")

        moduleDeserializer.addModuleReachableTopLevel(topLevelSignature)

        deserializeAllReachableTopLevels()
        return descriptor
    }

    override fun getDeclaration(symbol: IrSymbol): IrDeclaration? {

        if (!symbol.isPublicApi) return null

        if (!symbol.isBound) {
            findDeserializedDeclarationForDescriptor(symbol) ?: return null
        }

        // TODO: we do have serializations for those, but let's just create a stub for now.
        if (!symbol.isBound && (symbol.descriptor.isExpectMember || symbol.descriptor.containingDeclaration?.isExpectMember ?: false))
            return null

        assert(symbol.isBound) {
            "getDeclaration: symbol ${symbol} is unbound, descriptor = ${symbol.descriptor}, signature = ${symbol.signature}"
        }

        return symbol.owner as IrDeclaration
    }

    // TODO: This is Native specific. Eliminate me.
    override fun declareForwardDeclarations() {
        if (forwardModuleDescriptor == null) return

        val packageFragments = forwardDeclarations.map { it.descriptor.findPackage() }.distinct()

        // We don't bother making a real IR module here, as we have no need in it any later.
        // All we need is just to declare forward declarations in the symbol table
        // In case you need a full fledged module, turn the forEach into a map and collect
        // produced files into an IrModuleFragment.

        packageFragments.forEach { packageFragment ->
            val symbol = IrFileSymbolImpl(packageFragment)
            val file = IrFileImpl(NaiveSourceBasedFileEntryImpl("forward declarations pseudo-file"), symbol)
            val symbols = forwardDeclarations
                .filter { !it.isBound }
                .filter { it.descriptor.findPackage() == packageFragment }
            val declarations = symbols.map {

                val classDescriptor = it.descriptor as ClassDescriptor
                val declaration = symbolTable.declareClass(
                    UNDEFINED_OFFSET, UNDEFINED_OFFSET, irrelevantOrigin,
                    classDescriptor,
                    classDescriptor.modality
                ) { symbol: IrClassSymbol -> IrClassImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, irrelevantOrigin, symbol) }
                    .also {
                        it.parent = file
                    }
                declaration

            }
            file.declarations.addAll(declarations)
        }
    }

    fun initializeExpectActualLinker() {
        deserializersForModules.values.forEach {
            it.fileToDeserializerMap.values.forEach {
                it.deserializeExpectActualMapping()
            }
        }
    }

    // The issue here is that an expect can not trigger its actual deserialization by reachability
    // because the expect can not see the actual higher in the module dependency dag.
    // So we force deserialization of actuals for all deserialized expect symbols here.
    fun finalizeExpectActualLinker() {
        expectUniqIdToActualUniqId.filter{ topLevelActualUniqItToDeserializer[it.value] != null}.forEach {
            val expectSymbol = expectSymbols[it.key]
            val actualSymbol = actualSymbols[it.value]
            if (expectSymbol != null && (actualSymbol == null || !actualSymbol.isBound)) {
                topLevelActualUniqItToDeserializer[it.value]!!.addModuleReachableTopLevel(it.value)
                deserializeAllReachableTopLevels()
            }
        }

        // Now after all actuals have been deserialized, retarget delegating symbols from expects to actuals.
        expectUniqIdToActualUniqId.forEach {
            val expectSymbol = expectSymbols[it.key]
            val actualSymbol = actualSymbols[it.value]
            if (expectSymbol != null && actualSymbol != null) {
                when (expectSymbol) {
                    is IrDelegatingClassSymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrClassSymbol
                    is IrDelegatingEnumEntrySymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrEnumEntrySymbol
                    is IrDelegatingSimpleFunctionSymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrSimpleFunctionSymbol
                    is IrDelegatingConstructorSymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrConstructorSymbol
                    is IrDelegatingPropertySymbolImpl ->
                        expectSymbol.delegate = actualSymbol as IrPropertySymbol
                    else -> error("Unexpected expect symbol kind during actualization: $expectSymbol")
                }
            }
        }
    }

    fun deserializeIrModuleHeader(
        moduleDescriptor: ModuleDescriptor,
        deserializationStrategy: DeserializationStrategy = DeserializationStrategy.ONLY_REFERENCED
    ): IrModuleFragment {
        val deserializerForModule = deserializersForModules.getOrPut(moduleDescriptor) {
            IrModuleDeserializer(moduleDescriptor, deserializationStrategy)
        }
        // The IrModule and its IrFiles have been created during module initialization.
        return deserializerForModule.module
    }

    fun deserializeIrModuleHeader(moduleDescriptor: ModuleDescriptor): IrModuleFragment? {
        // TODO: consider skip deserializing explicitly exported declarations for libraries.
        // Now it's not valid because of all dependencies that must be computed.
        val deserializationStrategy =
            if (exportedDependencies.contains(moduleDescriptor)) {
                DeserializationStrategy.ALL
            } else {
                DeserializationStrategy.EXPLICITLY_EXPORTED
            }
        return deserializeIrModuleHeader(moduleDescriptor, deserializationStrategy)
    }

    fun deserializeFullModule(moduleDescriptor: ModuleDescriptor): IrModuleFragment =
        deserializeIrModuleHeader(moduleDescriptor, DeserializationStrategy.ALL)

    fun deserializeOnlyHeaderModule(moduleDescriptor: ModuleDescriptor): IrModuleFragment =
        deserializeIrModuleHeader(moduleDescriptor, DeserializationStrategy.ONLY_DECLARATION_HEADERS)
}

enum class DeserializationStrategy(val needBodies: Boolean, val explicitlyExported: Boolean, val theWholeWorld: Boolean) {
    ONLY_REFERENCED(true, false, false),
    ALL(true, true, true),
    EXPLICITLY_EXPORTED(true, true, false),
    ONLY_DECLARATION_HEADERS(false, false, false)
}
