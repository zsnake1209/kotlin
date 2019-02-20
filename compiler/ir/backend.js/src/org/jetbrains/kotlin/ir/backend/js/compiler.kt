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
    var moduleDescriptor: ModuleDescriptorImpl?,
    val moduleType: ModuleType,
    val klibPath: String,
    val dependencies: List<CompiledModule>,
    val isBuiltIn: Boolean
)

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
        symbolTable,
        null
    )
    return deserializer.deserializeIrModule(moduleDescriptor, moduleFile.readBytes(), klibDirFile, deserializeDeclaration)
}

private fun deserializerRuntimeKlib(
    locationDir: String,
    moduleName: String,
    lookupTracker: LookupTracker,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings,
    deserializeDeclarations: Boolean
): JsKlib {
    val klibDirFile = File(locationDir)
    val md = loadKlibMetadata(
        moduleName,
        locationDir,
        true,
        lookupTracker,
        storageManager,
        metadataVersion,
        languageVersionSettings,
        null,
        emptyList()
    )

    val st = SymbolTable()
    val typeTranslator = TypeTranslator(st, languageVersionSettings).also {
        it.constantValueGenerator = ConstantValueGenerator(md, st)
    }

    val irBuiltIns = IrBuiltIns(md.builtIns, typeTranslator, st)

    val moduleFragment = deserializeRuntimeIr(md, klibDirFile, deserializeDeclarations, st, irBuiltIns)

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
//            error("not supported any more")
            compileIntoJsAgainstCachedDeps(files, project, configuration, dependencies, builtInsModule, moduleType)
        CompilationMode.TEST_AGAINST_KLIB -> compileIntoJsAgainstKlib(files, project, configuration, dependencies, moduleType, klibDirectory)
    }



}

private fun loadKlibMetadata(
    moduleName: String,
    klibPath: String,
    isBuiltIn: Boolean,
    lookupTracker: LookupTracker,
    storageManager: LockBasedStorageManager,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings,
    builtinsModule: ModuleDescriptorImpl?,
    dependencies: List<CompiledModule>
): ModuleDescriptorImpl {
    assert(isBuiltIn == (builtinsModule === null))

    val metadataFile = File(klibPath, metadataFileName(moduleName))

    val serializer = JsKlibMetadataSerializationUtil
    val parts = serializer.readModuleAsProto(metadataFile.readBytes())
    val builtIns = builtinsModule?.builtIns ?: object : KotlinBuiltIns(storageManager) {}
    val md = ModuleDescriptorImpl(Name.special("<$moduleName>"), storageManager, builtIns)
    if (isBuiltIn) builtIns.builtInsModule = md
    val currentModuleFragmentProvider = createJsKlibMetadataPackageFragmentProvider(
        storageManager, md, parts.header, parts.body, metadataVersion,
        CompilerDeserializationConfiguration(languageVersionSettings),
        lookupTracker
    )

    val packageFragmentProvider = if (isBuiltIn) {
        val functionFragmentProvider = functionInterfacePackageFragmentProvider(storageManager, md)
        CompositePackageFragmentProvider(listOf(functionFragmentProvider, currentModuleFragmentProvider))
    } else currentModuleFragmentProvider

    md.initialize(packageFragmentProvider)
    md.setDependencies(listOf(md) + dependencies.mapNotNull { it.moduleDescriptor })

    return md
}

typealias MetadataDFSHandler = DFS.NodeHandler<CompiledModule, List<CompiledModule>>

private class DependencyMetadataLoader(
    private val lookupTracker: LookupTracker,
    private val metadataVersion: JsKlibMetadataVersion,
    private val languageVersionSettings: LanguageVersionSettings,
    private val storageManager: LockBasedStorageManager
) : MetadataDFSHandler {
    private val sortedDependencies = mutableListOf<CompiledModule>()

    private var runtimeModule: ModuleDescriptorImpl? = null

    override fun beforeChildren(current: CompiledModule) = true

    override fun afterChildren(current: CompiledModule) {
        val md = current.moduleDescriptor ?: loadKlibMetadata(
            current.moduleName,
            current.klibPath,
            current.isBuiltIn,
            lookupTracker,
            storageManager,
            metadataVersion,
            languageVersionSettings,
            runtimeModule,
            current.dependencies
        ).also { current.moduleDescriptor = it }
        sortedDependencies += current
        if (current.isBuiltIn) runtimeModule = md
    }

    override fun result() = sortedDependencies
}

private fun compileIntoJsAgainstKlib(
    files: List<KtFile>,
    project: Project,
    configuration: CompilerConfiguration,
    dependencies: List<CompiledModule>,
    moduleType: ModuleType,
    klibDirectory: File
): CompiledModule {
    val metadataVersion = configuration.get(CommonConfigurationKeys.METADATA_VERSION)  as? JsKlibMetadataVersion
        ?: JsKlibMetadataVersion.INSTANCE
    val lookupTracker = LookupTracker.DO_NOTHING
    val languageSettings = configuration.languageVersionSettings
    val dfsHandler: MetadataDFSHandler = DependencyMetadataLoader(lookupTracker, metadataVersion, languageSettings, storageManager)
    val sortedDeps = DFS.dfs(dependencies, CompiledModule::dependencies, dfsHandler)
    val builtInModule = sortedDeps.firstOrNull()?.moduleDescriptor // null in case compiling builtInModule itself

    val analysisResult =
        TopDownAnalyzerFacadeForJS.analyzeFiles(
            files,
            project,
            configuration,
            sortedDeps.mapNotNull { it.moduleDescriptor },
            emptyList(),
            thisIsBuiltInsModule = builtInModule == null,
            customBuiltInsModule = builtInModule
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
        symbolTable,
        null
    )

    val deserializedModuleFragments = sortedDeps.map {
        val moduleFile = File(it.klibPath, moduleHeaderFileName)
        deserializer.deserializeIrModule(it.moduleDescriptor!!, moduleFile.readBytes(), File(it.klibPath), false)
    }

    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files, deserializer)
    val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String

    if (moduleType == ModuleType.SECONDARY) {
        deserializedModuleFragments.forEach {
            ExternalDependenciesGenerator(
                it.descriptor,
                symbolTable,
                psi2IrContext.irBuiltIns
            ).generateUnboundSymbolsAsDependencies(it)
            it.patchDeclarationParents()
        }
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
        return CompiledModule(
            moduleName,
            null,
            null,
            null,
            moduleType,
            klibDirectory.absolutePath,
            dependencies,
            moduleType == ModuleType.TEST_RUNTIME
        )
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

    val irFiles = deserializedModuleFragments.flatMap { it.files } + moduleFragment.files

    moduleFragment.files.clear()
    moduleFragment.files += irFiles

    moduleFragment.patchDeclarationParents()

    jsPhases.invokeToplevel(context.phaseConfig, context, moduleFragment)

    val jsProgram = moduleFragment.accept(IrModuleToJsTransformer(context), null)

    return CompiledModule(project.name, jsProgram.toString(), null, null, moduleType, "", emptyList(), moduleType == ModuleType.TEST_RUNTIME)
}

fun serializeModuleIntoKlib(
    moduleName: String,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings,
    symbolTable: SymbolTable,
    bindingContext: BindingContext,
    klibDirectory: File,
    dependencies: List<CompiledModule>,
    moduleFragment: IrModuleFragment
) {
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
            dependencies.mapNotNull { it.moduleDescriptor },
            emptyList(),
            thisIsBuiltInsModule = builtInsModule == null,
            customBuiltInsModule = builtInsModule?.moduleDescriptor
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
            return CompiledModule(
                moduleName,
                null,
                moduleFragment,
                moduleFragment.descriptor as ModuleDescriptorImpl,
                moduleType,
                "",
                dependencies,
                moduleType == ModuleType.TEST_RUNTIME
            )
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

    return CompiledModule(
        moduleName,
        jsProgram.toString(),
        context.moduleFragmentCopy,
        context.moduleFragmentCopy.descriptor as ModuleDescriptorImpl,
        moduleType,
        "",
        dependencies,
        moduleType == ModuleType.TEST_RUNTIME
    )
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
            customBuiltInsModule = builtInsModule?.moduleDescriptor
        )

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)

    val symbolTable = SymbolTable()

    val languageVersionSettings = configuration.languageVersionSettings
    val psi2IrTranslator = Psi2IrTranslator(languageVersionSettings)
    val psi2IrContext = psi2IrTranslator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext, symbolTable)

    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files)
    val metadataVersion = configuration.get(CommonConfigurationKeys.METADATA_VERSION)  as? JsKlibMetadataVersion
        ?: JsKlibMetadataVersion.INSTANCE
    val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String

    serializeModuleIntoKlib(
        moduleName,
        metadataVersion,
        languageVersionSettings,
        symbolTable,
        psi2IrContext.bindingContext,
        klibDirectory,
        dependencies,
        moduleFragment
    )


    val jsProgram = if (generateJsCode) {
        val lookupTracker = configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER, LookupTracker.DO_NOTHING)
        val (md, deserializedModuleFragment, st, irBuiltIns) = deserializerRuntimeKlib(
            klibDirectory.absolutePath,
            moduleName,
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

    return CompiledModule(moduleName, jsProgram?.toString(), null, null, moduleType, klibDirectory.absolutePath, emptyList(), moduleType == ModuleType.TEST_RUNTIME)
}
