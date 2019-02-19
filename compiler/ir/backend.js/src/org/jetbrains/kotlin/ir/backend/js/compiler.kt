/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.backend.common.phaser.invokeToplevel
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.functions.functionInterfacePackageFragmentProvider
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.CompositePackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.backend.js.lower.inline.replaceUnboundSymbols
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.*
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.metadata.JsKlibMetadataModuleDescriptor
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.metadata.JsKlibMetadataSerializationUtil
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.metadata.JsKlibMetadataVersion
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.metadata.createJsKlibMetadataPackageFragmentProvider
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrModuleToJsTransformer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.CompilerDeserializationConfiguration
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.utils.DFS
import java.io.File

enum class ModuleType {
    TEST_RUNTIME,
    SECONDARY,
    MAIN
}

class CompiledModule(
    val moduleName: String,
    val generatedCode: String?,
    val moduleFragment: IrModuleFragment?,
    val moduleType: ModuleType,
    val klibPath: String,
    val dependencies: List<CompiledModule>,
    val isBuiltIn: Boolean
) {
    val descriptor
        get() = moduleFragment!!.descriptor as ModuleDescriptorImpl
}

enum class CompilationMode(val generateJS: Boolean) {
    KLIB(false),
    KLIB_WITH_JS(true),
    TEST_AGAINST_CACHE(true),
    TEST_AGAINST_KLIB(true),
}

private val runtimeKlibPath = "js/js.translator/testData/out/klibs/runtime/"

private val moduleHeaderFileName = "module.kji"
private val declarationsDirName = "ir/"
private val debugDataFileName = "debug.txt"
private fun metadataFileName(moduleName: String) = "$moduleName.${JsKlibMetadataSerializationUtil.CLASS_METADATA_FILE_EXTENSION}"

private val JS_IR_RUNTIME_MODULE_NAME = "JS_IR_RUNTIME"

data class JsKlib(
    val moduleDescriptor: ModuleDescriptor,
    val moduleIr: IrModuleFragment,
    val symbolTable: SymbolTable,
    val irBuiltIns: IrBuiltIns
)


private val logggg = object : LoggingContext {
    override var inVerbosePhase: Boolean
        get() = TODO("not implemented")
        set(_) {}

    override fun log(message: () -> String) {}
}

val storageManager = LockBasedStorageManager("JsConfig")


private fun deserializeRuntimeIr(
    moduleDescriptor: ModuleDescriptor,
    klibDirFile: File,
    deserializeDeclaration: Boolean,
    symbolTable: SymbolTable,
    irBuiltIns: IrBuiltIns
): IrModuleFragment {
    val moduleFile = File(klibDirFile, moduleHeaderFileName)
    val deserializer = IrKlibProtoBufModuleDeserializer(
        moduleDescriptor,
        logggg,
        irBuiltIns,
        klibDirFile,
        symbolTable,
        null
    )
    return deserializer.deserializeIrModule(moduleDescriptor, moduleFile.readBytes(), deserializeDeclaration)
}

private fun deserializerRuntimeKlib(
    locationDir: String,
    moduleName: String,
    lookupTracker: LookupTracker,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings,
    deserializeDeclaration: Boolean
): JsKlib {
    val klibDirFile = File(locationDir)
    val md = deserializeRuntimeMetadata(klibDirFile, moduleName, lookupTracker, metadataVersion, languageVersionSettings)

    val st = SymbolTable()
    val typeTranslator = TypeTranslator(st, languageVersionSettings).also {
        it.constantValueGenerator = ConstantValueGenerator(md, st)
    }

    val irBuiltIns = IrBuiltIns(md.builtIns, typeTranslator, st)

    val moduleFragment = deserializeRuntimeIr(md, klibDirFile, deserializeDeclaration, st, irBuiltIns)

    return JsKlib(md, moduleFragment, st, irBuiltIns)
}

fun compile(
    project: Project,
    files: List<KtFile>,
    configuration: CompilerConfiguration,
    export: List<FqName> = emptyList(),
    compileMode: CompilationMode = CompilationMode.TEST_AGAINST_CACHE,
    dependencies: List<CompiledModule> = emptyList(),
    builtInsModule: CompiledModule? = null,
    moduleType: ModuleType,
    klibDirectory: File
): CompiledModule {
    return when (compileMode) {
        CompilationMode.KLIB, CompilationMode.KLIB_WITH_JS ->
            compileIntoKlib(files, project, configuration, dependencies, builtInsModule, moduleType, compileMode.generateJS, klibDirectory)
        CompilationMode.TEST_AGAINST_CACHE ->
            compileIntoJsAgainstCachedDeps(files, project, configuration, dependencies, builtInsModule, moduleType)
        CompilationMode.TEST_AGAINST_KLIB -> compileIntoJsAgainstKlib(files, project, configuration, dependencies, moduleType, klibDirectory)
    }
}

//val storageManager = LockBasedStorageManager("JsConfig")


private fun deserializeRuntimeMetadata(
    klibDirFile: File,
    moduleName: String,
    lookupTracker: LookupTracker,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings
): ModuleDescriptor {
    val metadataFile = File(klibDirFile, metadataFileName(moduleName))
//    val storageManager = LockBasedStorageManager("JsConfig")
    val serializer = JsKlibMetadataSerializationUtil
    val parts = serializer.readModuleAsProto(metadataFile.readBytes())
    val builtIns = object : KotlinBuiltIns(storageManager) {}//analysisResult.moduleDescriptor.builtIns
    val md = ModuleDescriptorImpl(Name.special("<$moduleName>"), storageManager, builtIns)
    builtIns.builtInsModule = md
    val packageProviders = listOf(
        functionInterfacePackageFragmentProvider(storageManager, md),
        createJsKlibMetadataPackageFragmentProvider(
            storageManager, md, parts.header, parts.body, metadataVersion,
            CompilerDeserializationConfiguration(languageVersionSettings),
            lookupTracker
        )
    )

    md.initialize(CompositePackageFragmentProvider(packageProviders))
    md.setDependencies(listOf(md))
    return md
}

private fun loadKlibMetadata(
    klib: CompiledModule,
    klibMap: Map<CompiledModule, ModuleDescriptorImpl>,
    lookupTracker: LookupTracker,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings,
    builtinsModule: ModuleDescriptorImpl?
): ModuleDescriptorImpl {
    assert(klib.isBuiltIn == (builtinsModule === null))

    val moduleName = klib.moduleName
    val metadataFile = File(klib.klibPath, metadataFileName(moduleName))

    val serializer = JsKlibMetadataSerializationUtil
    val parts = serializer.readModuleAsProto(metadataFile.readBytes())
    val builtIns = builtinsModule?.builtIns ?: object : KotlinBuiltIns(storageManager) {}
    val md = ModuleDescriptorImpl(Name.special("<$moduleName>"), storageManager, builtIns)
    if (klib.isBuiltIn) builtIns.builtInsModule = md
    val currentModuleFragmentProvider = createJsKlibMetadataPackageFragmentProvider(
        storageManager, md, parts.header, parts.body, metadataVersion,
        CompilerDeserializationConfiguration(languageVersionSettings),
        lookupTracker
    )

    val packageFragmentProvider = if (klib.isBuiltIn) {
        val functionFragmentProvider = functionInterfacePackageFragmentProvider(storageManager, md)
        CompositePackageFragmentProvider(listOf(functionFragmentProvider, currentModuleFragmentProvider))
    } else currentModuleFragmentProvider

    md.initialize(packageFragmentProvider)
    md.setDependencies(listOf(md) + klib.dependencies.mapNotNull { klibMap[it] })

    return md
}

private fun compileIntoJsAgainstKlib(
    files: List<KtFile>,
    project: Project,
    configuration: CompilerConfiguration,
    dependencies: List<CompiledModule>,
    moduleType: ModuleType,
    klibDirectory: File
): CompiledModule {

    val runtimeKlib = dependencies.single { it.isBuiltIn }

    assert(runtimeKlib.dependencies.isEmpty())

    val klibModuleMap = mutableMapOf<CompiledModule, ModuleDescriptorImpl>()

    val runtimeKlibFile = File(runtimeKlib.klibPath)
    val metadataVersion = configuration.get(CommonConfigurationKeys.METADATA_VERSION)  as? JsKlibMetadataVersion
        ?: JsKlibMetadataVersion.INSTANCE
    val lookupTracker = LookupTracker.DO_NOTHING // configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER, LookupTracker.DO_NOTHING)
    val languageSettings = configuration.languageVersionSettings
//    val runtimeModule = loadKlibMetadata(runtimeKlib, klibModuleMap, lookupTracker, metadataVersion, languageSettings, null)
    var runtimeModule: ModuleDescriptorImpl? = null

//    klibModuleMap[runtimeKlib] = runtimeModule

//    val dependenciesWithoutRuntime = dependencies.filter { !it.isBuiltIn }
    val sortedDeps = DFS.dfs(
        dependencies,
        CompiledModule::dependencies,
        object : DFS.NodeHandler<CompiledModule, List<Pair<CompiledModule, ModuleDescriptorImpl>>> {

            private val sortedDependencies = mutableListOf<Pair<CompiledModule, ModuleDescriptorImpl>>()

            override fun beforeChildren(current: CompiledModule) = true

            override fun afterChildren(current: CompiledModule) {
                val md = loadKlibMetadata(current, klibModuleMap, lookupTracker, metadataVersion, languageSettings, runtimeModule)
                klibModuleMap[current] = md
                sortedDependencies += Pair(current, md)
                if (current.isBuiltIn) runtimeModule = md
            }

            override fun result() = sortedDependencies
        })

    val analysisResult =
        TopDownAnalyzerFacadeForJS.analyzeFiles(
            files,
            project,
            configuration,
            sortedDeps.map { it.second },
            emptyList(),
            thisIsBuiltInsModule = false,
            customBuiltInsModule = runtimeModule
        )

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)

    val symbolTable = SymbolTable()

    val psi2IrTranslator = Psi2IrTranslator(languageSettings)
    val psi2IrContext = psi2IrTranslator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext, symbolTable)

    val deserializer = IrKlibProtoBufModuleDeserializer(
        psi2IrContext.moduleDescriptor,
        logggg,
        psi2IrContext.irBuiltIns,
        runtimeKlibFile,
        symbolTable,
        null
    )

    val deserializedModuleFragments = sortedDeps.map {
        val moduleFile = File(it.first.klibPath, moduleHeaderFileName)
        deserializer.deserializeIrModule(it.second, moduleFile.readBytes(), false)
    }

//    val runtimeModuleFragment = deserializer.deserializeIrModule(runtimeModule, moduleFile.readBytes(), false)

    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files, deserializer)
    val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String

    if (moduleType == ModuleType.SECONDARY) {
        serializeModuleIntoKlib(
            moduleName,
            metadataVersion,
            languageSettings,
            symbolTable,
            psi2IrContext.bindingContext,
            klibDirectory,
            dependencies,
            moduleFragment
        )
        return CompiledModule(moduleName, null, null, moduleType, klibDirectory.absolutePath, dependencies, moduleType == ModuleType.TEST_RUNTIME)
    }


    val context = JsIrBackendContext(
        analysisResult.moduleDescriptor,
        psi2IrContext.irBuiltIns,
        psi2IrContext.symbolTable,
        moduleFragment,
        configuration,
        dependencies,
        moduleType
    )

    deserializedModuleFragments.forEach {
        ExternalDependenciesGenerator(
            it.descriptor,
            context.symbolTable,
            context.irBuiltIns,
            deserializer
        ).generateUnboundSymbolsAsDependencies(it)
    }

    val files = deserializedModuleFragments.flatMap { it.files } + moduleFragment.files

    moduleFragment.files.clear()
    moduleFragment.files += files

    moduleFragment.patchDeclarationParents()

    jsPhases.invokeToplevel(context.phaseConfig, context, moduleFragment)

    val jsProgram = moduleFragment.accept(IrModuleToJsTransformer(context), null)

    return CompiledModule(project.name, jsProgram.toString(), null, moduleType, "", emptyList(), moduleType == ModuleType.TEST_RUNTIME)
}

fun serializeModuleIntoKlib(moduleName: String, metadataVersion: JsKlibMetadataVersion, languageVersionSettings: LanguageVersionSettings, symbolTable: SymbolTable, bindingContext: BindingContext,
                            klibDirectory: File, dependencies: List<CompiledModule>,
                            moduleFragment: IrModuleFragment) {
    val declarationTable = DeclarationTable(moduleFragment.irBuiltins, DescriptorTable(), symbolTable)

    val serializedIr = IrModuleSerializer(logggg, declarationTable/*, onlyForInlines = false*/).serializedIrModule(moduleFragment)
    val serializer = JsKlibMetadataSerializationUtil

    val moduleDescription =
        JsKlibMetadataModuleDescriptor(moduleName, dependencies.map { it.moduleName }, moduleFragment.descriptor)
    val serializedData = serializer.serializeMetadata(
        bindingContext,
        moduleDescription,
        languageVersionSettings,
        metadataVersion
    ) { declarationDescriptor ->
        val index = declarationTable.descriptorTable.get(declarationDescriptor)
        index?.let { newDescriptorUniqId(it) }
    }

    val klibDir = klibDirectory.also {
        it.deleteRecursively()
        it.mkdirs()
    }

    val moduleFile = File(klibDir, moduleHeaderFileName)
    moduleFile.writeBytes(serializedIr.module)

    val irDeclarationDir = File(klibDir, declarationsDirName).also { it.mkdir() }

    for ((id, data) in serializedIr.declarations) {
        val file = File(irDeclarationDir, id.declarationFileName)
        file.writeBytes(data)
    }

    val debugFile = File(klibDir, debugDataFileName)

    for ((id, data) in serializedIr.debugIndex) {
        debugFile.appendText(id.toString())
        debugFile.appendText(" --- ")
        debugFile.appendText(data)
        debugFile.appendText("\n")
    }

    File(klibDir, "${moduleDescription.name}.${JsKlibMetadataSerializationUtil.CLASS_METADATA_FILE_EXTENSION}").also {
        it.writeBytes(serializedData.asByteArray())
    }
}

private fun compileIntoJsAgainstCachedDeps(
    files: List<KtFile>,
    project: Project,
    configuration: CompilerConfiguration,
    dependencies: List<CompiledModule>,
    builtInsModule: CompiledModule?,
    moduleType: ModuleType
): CompiledModule {
    val analysisResult =
        TopDownAnalyzerFacadeForJS.analyzeFiles(
            files,
            project,
            configuration,
            dependencies.map { it.descriptor },
            emptyList(),
            thisIsBuiltInsModule = builtInsModule == null,
            customBuiltInsModule = builtInsModule?.descriptor
        )

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)

    val irDependencies = dependencies.mapNotNull { it.moduleFragment }

    val symbolTable = SymbolTable()
    irDependencies.forEach { symbolTable.loadModule(it) }

    val psi2IrTranslator = Psi2IrTranslator(configuration.languageVersionSettings)
    val psi2IrContext = psi2IrTranslator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext, symbolTable)

    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files)

    // TODO: Split compilation into two steps: kt -> ir, ir -> js
    val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!
    when (moduleType) {
        ModuleType.MAIN -> {
            val moduleDependencies: List<CompiledModule> =
                DFS.topologicalOrder(dependencies, CompiledModule::dependencies)
                    .filter { it.moduleType == ModuleType.SECONDARY }

            val fileDependencies = moduleDependencies.flatMap { it.moduleFragment!!.files }

            moduleFragment.files.addAll(0, fileDependencies)
        }

        ModuleType.SECONDARY -> {
            return CompiledModule(moduleName, null, moduleFragment, moduleType, "", dependencies, moduleType == ModuleType.TEST_RUNTIME)
        }

        ModuleType.TEST_RUNTIME -> {
        }
    }

    val context = JsIrBackendContext(
        analysisResult.moduleDescriptor,
        psi2IrContext.irBuiltIns,
        psi2IrContext.symbolTable,
        moduleFragment,
        configuration,
        dependencies,
        moduleType
    )

    jsPhases.invokeToplevel(context.phaseConfig, context, moduleFragment)

    val jsProgram = moduleFragment.accept(IrModuleToJsTransformer(context), null)

    return CompiledModule(moduleName, jsProgram.toString(), context.moduleFragmentCopy, moduleType, "", dependencies, moduleType == ModuleType.TEST_RUNTIME)
}

private fun compileIntoKlib(
    files: List<KtFile>,
    project: Project,
    configuration: CompilerConfiguration,
    dependencies: List<CompiledModule>,
    builtInsModule: CompiledModule?,
    moduleType: ModuleType,
    generateJsCode: Boolean,
    klibDirectory: File
): CompiledModule {
    val analysisResult =
        TopDownAnalyzerFacadeForJS.analyzeFiles(
            files,
            project,
            configuration,
            emptyList(), //dependencies.map { it.descriptor },
            emptyList(),
            thisIsBuiltInsModule = builtInsModule == null,
            customBuiltInsModule = builtInsModule?.descriptor
        )

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)

    val irDependencies = dependencies.mapNotNull { it.moduleFragment }

    val symbolTable = SymbolTable()

    val psi2IrTranslator = Psi2IrTranslator(configuration.languageVersionSettings)
    val psi2IrContext = psi2IrTranslator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext, symbolTable)

    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files)

    val declarationTable = DeclarationTable(moduleFragment.irBuiltins, DescriptorTable(), psi2IrContext.symbolTable)

    val serializedIr = IrModuleSerializer(logggg, declarationTable/*, onlyForInlines = false*/).serializedIrModule(moduleFragment)
    val serializer = JsKlibMetadataSerializationUtil

    val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String
    val metadataVersion = configuration.get(CommonConfigurationKeys.METADATA_VERSION)  as? JsKlibMetadataVersion
        ?: JsKlibMetadataVersion.INSTANCE
    val moduleDescription =
        JsKlibMetadataModuleDescriptor(moduleName, irDependencies.map { it.name.asString() }, moduleFragment.descriptor)
    val serializedData = serializer.serializeMetadata(
        psi2IrContext.bindingContext,
        moduleDescription,
        configuration.get(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS)!!,
        metadataVersion
    ) { declarationDescriptor ->
        val index = declarationTable.descriptorTable.get(declarationDescriptor)
        index?.let { newDescriptorUniqId(it) }
    }

    val klibDir = klibDirectory.also {
        it.deleteRecursively()
        it.mkdirs()
    }

    val moduleFile = File(klibDir, moduleHeaderFileName)
    moduleFile.writeBytes(serializedIr.module)

    val irDeclarationDir = File(klibDir, declarationsDirName).also { it.mkdir() }

    for ((id, data) in serializedIr.declarations) {
        val file = File(irDeclarationDir, id.declarationFileName)
        file.writeBytes(data)
    }


    val debugFile = File(klibDir, debugDataFileName)

    for ((id, data) in serializedIr.debugIndex) {
        debugFile.appendText(id.toString())
        debugFile.appendText(" --- ")
        debugFile.appendText(data)
        debugFile.appendText("\n")
    }

    File(klibDir, "${moduleDescription.name}.${JsKlibMetadataSerializationUtil.CLASS_METADATA_FILE_EXTENSION}").also {
        it.writeBytes(serializedData.asByteArray())
    }

    val jsProgram = if (generateJsCode) {
        val lookupTracker = configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER, LookupTracker.DO_NOTHING)
        val (md, deserializedModuleFragment, st, irBuiltIns) = deserializerRuntimeKlib(
            klibDir.absolutePath,
            moduleDescription.name,
            lookupTracker,
            metadataVersion,
            configuration.languageVersionSettings,
            true
        )

        val context = JsIrBackendContext(md, irBuiltIns, st, deserializedModuleFragment, configuration, dependencies, moduleType)

        deserializedModuleFragment.replaceUnboundSymbols(context)

        jsPhases.invokeToplevel(context.phaseConfig, context, deserializedModuleFragment)

        deserializedModuleFragment.accept(IrModuleToJsTransformer(context), null)
    } else null

    return CompiledModule(moduleName, jsProgram?.toString(), null, moduleType, klibDir.absolutePath, emptyList(), moduleType == ModuleType.TEST_RUNTIME)
}
