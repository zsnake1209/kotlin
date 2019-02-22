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

class CompiledModule(
    val moduleName: String,
    val generatedCode: String?,
    var moduleDescriptor: ModuleDescriptorImpl?,
    val klibPath: String,
    val dependencies: List<CompiledModule>,
    val isBuiltIn: Boolean
)

enum class CompilationMode(val generateJS: Boolean, val generateKlib: Boolean) {
    KLIB(false, true),
    KLIB_WITH_JS(true, true),
    JS_AGAINST_KLIB(true, false)
}

private val moduleHeaderFileName = "module.kji"
private val declarationsDirName = "ir/"
private val debugDataFileName = "debug.txt"
private fun metadataFileName(moduleName: String) = "$moduleName.${JsKlibMetadataSerializationUtil.CLASS_METADATA_FILE_EXTENSION}"

data class JsKlib(
    val moduleDescriptor: ModuleDescriptorImpl,
    val moduleIr: IrModuleFragment,
    val symbolTable: SymbolTable,
    val irBuiltIns: IrBuiltIns,
    val deserializer: IrKlibProtoBufModuleDeserializer
)


private val logggg = object : LoggingContext {
    override var inVerbosePhase: Boolean
        get() = TODO("not implemented")
        set(_) {}

    override fun log(message: () -> String) {}
}

val storageManager = LockBasedStorageManager("JsConfig")


private fun deserializeModuleFromKlib(
    locationDir: String,
    moduleName: String,
    lookupTracker: LookupTracker,
    storageManager: LockBasedStorageManager,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings,
    dependencies: List<CompiledModule>,
    builtinsModule: ModuleDescriptorImpl?
): JsKlib {
    val klibDirFile = File(locationDir)
    val md = loadKlibMetadata(
        moduleName,
        locationDir,
        builtinsModule == null,
        lookupTracker,
        storageManager,
        metadataVersion,
        languageVersionSettings,
        builtinsModule,
        dependencies
    )

    val st = SymbolTable()
    val typeTranslator = TypeTranslator(st, languageVersionSettings).also {
        it.constantValueGenerator = ConstantValueGenerator(md, st)
    }

    val irBuiltIns = IrBuiltIns(md.builtIns, typeTranslator, st)

    val moduleFile = File(klibDirFile, moduleHeaderFileName)
    val deserializer = IrKlibProtoBufModuleDeserializer(md, logggg, irBuiltIns, st, null)

    dependencies.forEach {
        val dependencyKlibDir = File(it.klibPath, moduleHeaderFileName)
        deserializer.deserializeIrModule(it.moduleDescriptor!!, dependencyKlibDir.readBytes(), File(it.klibPath), false)
    }

    val moduleFragment = deserializer.deserializeIrModule(md, moduleFile.readBytes(), klibDirFile, true)

    return JsKlib(md, moduleFragment, st, irBuiltIns, deserializer)
}

fun compile(
    project: Project,
    files: List<KtFile>,
    configuration: CompilerConfiguration,
    export: List<FqName> = emptyList(),
    compileMode: CompilationMode,
    dependencies: List<CompiledModule> = emptyList(),
    klibPath: String
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

    val moduleDescriptor = analysisResult.moduleDescriptor as ModuleDescriptorImpl
    val symbolTable = SymbolTable()

    val psi2IrTranslator = Psi2IrTranslator(languageSettings)
    val psi2IrContext = psi2IrTranslator.createGeneratorContext(moduleDescriptor, analysisResult.bindingContext, symbolTable)
    val irBuiltIns = psi2IrContext.irBuiltIns

    var deserializer = IrKlibProtoBufModuleDeserializer(moduleDescriptor, logggg, irBuiltIns, symbolTable, null)

    val deserializedModuleFragments = sortedDeps.map {
        val moduleFile = File(it.klibPath, moduleHeaderFileName)
        deserializer.deserializeIrModule(it.moduleDescriptor!!, moduleFile.readBytes(), File(it.klibPath), false)
    }

    var moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files, deserializer)
    val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String

    val context = if (compileMode.generateKlib) {
        deserializedModuleFragments.forEach {
            ExternalDependenciesGenerator(it.descriptor, symbolTable, irBuiltIns, null).generateUnboundSymbolsAsDependencies(it)
        }
        deserializedModuleFragments.forEach { it.patchDeclarationParents() }
        serializeModuleIntoKlib(
            moduleName,
            metadataVersion,
            languageSettings,
            symbolTable,
            psi2IrContext.bindingContext,
            klibPath,
            dependencies,
            moduleFragment
        )

        if (compileMode.generateJS) {
            deserializeModuleFromKlib(klibPath, moduleName, lookupTracker, storageManager, metadataVersion, languageSettings, sortedDeps, builtInModule).let {
                deserializer = it.deserializer
                moduleFragment = it.moduleIr

                JsIrBackendContext(it.moduleDescriptor, it.irBuiltIns, it.symbolTable, it.moduleIr, configuration, compileMode).also {
                    if (builtInModule == null) moduleFragment.replaceUnboundSymbols(it)
                }
            }
        } else {
            return CompiledModule(moduleName, null, null, klibPath, dependencies, builtInModule == null)
        }
    } else JsIrBackendContext(moduleDescriptor, irBuiltIns, symbolTable, moduleFragment, configuration, compileMode)

    val jsProgram = if (compileMode.generateJS) {
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

        moduleFragment.accept(IrModuleToJsTransformer(context), null)
    } else null

    return CompiledModule(moduleName, jsProgram?.toString(), null, klibPath, dependencies, builtInModule == null)

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

//private fun compileIntoJsAgainstKlib(
//    files: List<KtFile>,
//    project: Project,
//    configuration: CompilerConfiguration,
//    dependencies: List<CompiledModule>,
//    moduleType: ModuleType,
//    compileMode: CompilationMode,
//    klibPath: String
//): CompiledModule {
//    val metadataVersion = configuration.get(CommonConfigurationKeys.METADATA_VERSION)  as? JsKlibMetadataVersion
//        ?: JsKlibMetadataVersion.INSTANCE
//    val lookupTracker = LookupTracker.DO_NOTHING
//    val languageSettings = configuration.languageVersionSettings
//    val dfsHandler: MetadataDFSHandler = DependencyMetadataLoader(lookupTracker, metadataVersion, languageSettings, storageManager)
//    val sortedDeps = DFS.dfs(dependencies, CompiledModule::dependencies, dfsHandler)
//    val builtInModule = sortedDeps.firstOrNull()?.moduleDescriptor // null in case compiling builtInModule itself
//
//    val analysisResult =
//        TopDownAnalyzerFacadeForJS.analyzeFiles(
//            files,
//            project,
//            configuration,
//            sortedDeps.mapNotNull { it.moduleDescriptor },
//            emptyList(),
//            thisIsBuiltInsModule = builtInModule == null,
//            customBuiltInsModule = builtInModule
//        )
//
//    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
//
//    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)
//
//    val moduleDescriptor = analysisResult.moduleDescriptor as ModuleDescriptorImpl
//    val symbolTable = SymbolTable()
//
//    val psi2IrTranslator = Psi2IrTranslator(languageSettings)
//    val psi2IrContext = psi2IrTranslator.createGeneratorContext(moduleDescriptor, analysisResult.bindingContext, symbolTable)
//    val irBuiltIns = psi2IrContext.irBuiltIns
//
//    val deserializer = IrKlibProtoBufModuleDeserializer(
//        psi2IrContext.moduleDescriptor,
//        logggg,
//        irBuiltIns,
//        symbolTable,
//        null
//    )
//
//    val deserializedModuleFragments = sortedDeps.map {
//        val moduleFile = File(it.klibPath, moduleHeaderFileName)
//        deserializer.deserializeIrModule(it.moduleDescriptor!!, moduleFile.readBytes(), File(it.klibPath), false)
//    }
//
//
//    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files, deserializer)
//    val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String
//    val klibDirPath = if (compileMode.generateKlib) klibPath else ""
//
//    val context = if (compileMode.generateKlib) {
//        deserializedModuleFragments.forEach {
//            ExternalDependenciesGenerator(it.descriptor, symbolTable, irBuiltIns, null).generateUnboundSymbolsAsDependencies(it)
//        }
//        deserializedModuleFragments.forEach { it.patchDeclarationParents() }
//        serializeModuleIntoKlib(
//            moduleName,
//            metadataVersion,
//            languageSettings,
//            symbolTable,
//            psi2IrContext.bindingContext,
//            klibPath,
//            dependencies,
//            moduleFragment
//        )
//
//        if (compileMode.generateJS) {
//            deserializerRuntimeKlib(klibDirPath, moduleName, lookupTracker, metadataVersion, languageSettings, true).let {
//                JsIrBackendContext(it.moduleDescriptor, it.irBuiltIns, it.symbolTable, it.moduleIr, configuration, moduleType)
//            }
//        } else {
//            return CompiledModule(moduleName, null, null, null, moduleType, klibDirPath, dependencies, builtInModule == null)
//        }
//    } else JsIrBackendContext(moduleDescriptor, irBuiltIns, symbolTable, moduleFragment, configuration, moduleType)
//
//    val jsProgram = if (compileMode.generateJS) {
//        deserializedModuleFragments.forEach {
//            ExternalDependenciesGenerator(
//                it.descriptor,
//                context.symbolTable,
//                context.irBuiltIns,
//                deserializer
//            ).generateUnboundSymbolsAsDependencies(it)
//        }
//
//        val irFiles = deserializedModuleFragments.flatMap { it.files } + moduleFragment.files
//
//        moduleFragment.files.clear()
//        moduleFragment.files += irFiles
//
//        moduleFragment.patchDeclarationParents()
//
//        jsPhases.invokeToplevel(context.phaseConfig, context, moduleFragment)
//
//        moduleFragment.accept(IrModuleToJsTransformer(context), null)
//    } else null
//
//    val copyIr = if (compileMode.copyIr) context.moduleFragmentCopy else null
//
//    return CompiledModule(
//        moduleName,
//        jsProgram?.toString(),
//        copyIr,
//        if (compileMode.copyIr) context.module else null,
//        moduleType,
//        klibDirPath,
//        dependencies,
//        builtInModule == null
//    )
//}

//private fun deserializerRuntimeKlib(
//    locationDir: String,
//    moduleName: String,
//    lookupTracker: LookupTracker,
//    metadataVersion: JsKlibMetadataVersion,
//    languageVersionSettings: LanguageVersionSettings,
//    deserializeDeclarations: Boolean
//): JsKlib {
//    val klibDirFile = File(locationDir)
//    val md = loadKlibMetadata(
//        moduleName,
//        locationDir,
//        true,
//        lookupTracker,
//        storageManager,
//        metadataVersion,
//        languageVersionSettings,
//        null,
//        emptyList()
//    )
//
//    val st = SymbolTable()
//    val typeTranslator = TypeTranslator(st, languageVersionSettings).also {
//        it.constantValueGenerator = ConstantValueGenerator(md, st)
//    }
//
//    val irBuiltIns = IrBuiltIns(md.builtIns, typeTranslator, st)
//
//    val moduleFile = File(klibDirFile, moduleHeaderFileName)
//    val deserializer = IrKlibProtoBufModuleDeserializer(
//        md,
//        logggg,
//        irBuiltIns,
//        st,
//        null
//    )
//    val moduleFragment = deserializer.deserializeIrModule(md, moduleFile.readBytes(), klibDirFile, deserializeDeclarations)
//
//    return JsKlib(md, moduleFragment, st, irBuiltIns, deserializer)
//}


fun serializeModuleIntoKlib(
    moduleName: String,
    metadataVersion: JsKlibMetadataVersion,
    languageVersionSettings: LanguageVersionSettings,
    symbolTable: SymbolTable,
    bindingContext: BindingContext,
    klibPath: String,
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

    val klibDir = File(klibPath).also {
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

//private fun compileIntoJsAgainstCachedDeps(
//    files: List<KtFile>,
//    project: Project,
//    configuration: CompilerConfiguration,
//    dependencies: List<CompiledModule>,
//    builtInsModule: CompiledModule?,
//    moduleType: ModuleType
//): CompiledModule {
//    val analysisResult =
//        TopDownAnalyzerFacadeForJS.analyzeFiles(
//            files,
//            project,
//            configuration,
//            dependencies.mapNotNull { it.moduleDescriptor },
//            emptyList(),
//            thisIsBuiltInsModule = builtInsModule == null,
//            customBuiltInsModule = builtInsModule?.moduleDescriptor
//        )
//
//    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
//
//    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)
//
//    val irDependencies = dependencies.mapNotNull { it.moduleFragment }
//
//    val symbolTable = SymbolTable()
//    irDependencies.forEach { symbolTable.loadModule(it) }
//
//    val psi2IrTranslator = Psi2IrTranslator(configuration.languageVersionSettings)
//    val psi2IrContext = psi2IrTranslator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext, symbolTable)
//
//    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files)
//
//    // TODO: Split compilation into two steps: kt -> ir, ir -> js
//    val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!
//    when (moduleType) {
//        ModuleType.MAIN -> {
//            val moduleDependencies: List<CompiledModule> =
//                DFS.topologicalOrder(dependencies, CompiledModule::dependencies)
//                    .filter { it.moduleType == ModuleType.SECONDARY }
//
//            val fileDependencies = moduleDependencies.flatMap { it.moduleFragment!!.files }
//
//            moduleFragment.files.addAll(0, fileDependencies)
//        }
//
//        ModuleType.SECONDARY -> {
//            return CompiledModule(
//                moduleName,
//                null,
//                moduleFragment,
//                moduleFragment.descriptor as ModuleDescriptorImpl,
//                moduleType,
//                "",
//                dependencies,
//                moduleType == ModuleType.TEST_RUNTIME
//            )
//        }
//
//        ModuleType.TEST_RUNTIME -> {
//        }
//    }
//
//    val context = JsIrBackendContext(
//        analysisResult.moduleDescriptor as ModuleDescriptorImpl,
//        psi2IrContext.irBuiltIns,
//        psi2IrContext.symbolTable,
//        moduleFragment,
//        configuration,
//        moduleType
//    )
//
//    jsPhases.invokeToplevel(context.phaseConfig, context, moduleFragment)
//
//    val jsProgram = moduleFragment.accept(IrModuleToJsTransformer(context), null)
//
//    return CompiledModule(
//        moduleName,
//        jsProgram.toString(),
//        context.moduleFragmentCopy,
//        context.moduleFragmentCopy.descriptor as ModuleDescriptorImpl,
//        moduleType,
//        "",
//        dependencies,
//        moduleType == ModuleType.TEST_RUNTIME
//    )
//}
//
//private fun compileIntoKlib(
//    files: List<KtFile>,
//    project: Project,
//    configuration: CompilerConfiguration,
//    dependencies: List<CompiledModule>,
//    builtInsModule: CompiledModule?,
//    moduleType: ModuleType,
//    generateJsCode: Boolean,
//    klibPath: String
//): CompiledModule {
//    val analysisResult =
//        TopDownAnalyzerFacadeForJS.analyzeFiles(
//            files,
//            project,
//            configuration,
//            emptyList(), //dependencies.map { it.descriptor },
//            emptyList(),
//            thisIsBuiltInsModule = builtInsModule == null,
//            customBuiltInsModule = builtInsModule?.moduleDescriptor
//        )
//
//    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
//
//    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)
//
//    val symbolTable = SymbolTable()
//
//    val languageVersionSettings = configuration.languageVersionSettings
//    val psi2IrTranslator = Psi2IrTranslator(languageVersionSettings)
//    val psi2IrContext = psi2IrTranslator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext, symbolTable)
//
//    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files)
//    val metadataVersion = configuration.get(CommonConfigurationKeys.METADATA_VERSION)  as? JsKlibMetadataVersion
//        ?: JsKlibMetadataVersion.INSTANCE
//    val moduleName = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String
//
//    serializeModuleIntoKlib(
//        moduleName,
//        metadataVersion,
//        languageVersionSettings,
//        symbolTable,
//        psi2IrContext.bindingContext,
//        klibPath,
//        dependencies,
//        moduleFragment
//    )
//
//
//    val jsProgram = if (generateJsCode) {
//        val lookupTracker = configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER, LookupTracker.DO_NOTHING)
//        val (md, deserializedModuleFragment, st, irBuiltIns, dd) = deserializeModuleFromKlib(klibPath, moduleName, lookupTracker, storageManager, metadataVersion, languageVersionSettings, dependencies, null)
//
//        val context = JsIrBackendContext(md, irBuiltIns, st, deserializedModuleFragment, configuration, CompilationMode.KLIB_WITH_JS)
//
//        deserializedModuleFragment.replaceUnboundSymbols(context)
//
//        jsPhases.invokeToplevel(context.phaseConfig, context, deserializedModuleFragment)
//
//        deserializedModuleFragment.accept(IrModuleToJsTransformer(context), null)
//    } else null
//
//    return CompiledModule(moduleName, jsProgram?.toString(), null, null, klibPath, emptyList(), moduleType == ModuleType.TEST_RUNTIME)
//}
