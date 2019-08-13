/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.benchmarks

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.common.phaser.invokeToplevel
import org.jetbrains.kotlin.backend.common.serialization.DescriptorTable
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.backend.js.*
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrLinker
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsIrModuleSerializer
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir.JsMangler
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.metadata.JsKlibMetadataVersion
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.IrModuleToJsTransformer
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.konan.KonanVersionImpl
import org.jetbrains.kotlin.konan.MetaVersion
import org.jetbrains.kotlin.library.KonanLibraryVersioning
import org.jetbrains.kotlin.library.KotlinAbiVersion
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.SerializedIrModule
import org.jetbrains.kotlin.library.impl.KotlinLibraryOnlyIrWriter
import org.jetbrains.kotlin.library.impl.createKotlinLibrary
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrConfiguration
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext
import org.jetbrains.kotlin.psi2ir.generators.GeneratorExtensions
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.multiplatform.isCommonSource
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.junit.Test
import java.io.File
import org.jetbrains.kotlin.konan.file.File as KonanFile

class GenerateIrRuntime {
    private val lookupTracker: LookupTracker = LookupTracker.DO_NOTHING
    private val logger = object : LoggingContext {
        override var inVerbosePhase = false
        override fun log(message: () -> String) {}
    }

    fun loadKlib(klibPath: String, isPacked: Boolean, perFile: Boolean = false) = createKotlinLibrary(KonanFile("$klibPath${if (isPacked) ".klib" else ""}"), perFile = perFile)

    private fun buildConfiguration(environment: KotlinCoreEnvironment): CompilerConfiguration {
        val runtimeConfiguration = environment.configuration.copy()
        runtimeConfiguration.put(CommonConfigurationKeys.MODULE_NAME, "JS_IR_RUNTIME")
        runtimeConfiguration.put(JSConfigurationKeys.MODULE_KIND, ModuleKind.UMD)

        runtimeConfiguration.languageVersionSettings = LanguageVersionSettingsImpl(
            LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE,
            specificFeatures = mapOf(
                LanguageFeature.AllowContractsForCustomFunctions to LanguageFeature.State.ENABLED,
                LanguageFeature.MultiPlatformProjects to LanguageFeature.State.ENABLED
            ),
            analysisFlags = mapOf(
                AnalysisFlags.useExperimental to listOf("kotlin.contracts.ExperimentalContracts", "kotlin.Experimental", "kotlin.ExperimentalMultiplatform"),
                AnalysisFlags.allowResultReturnType to true
            )
        )

        return runtimeConfiguration
    }
    private val CompilerConfiguration.metadataVersion
        get() = get(CommonConfigurationKeys.METADATA_VERSION) as? JsKlibMetadataVersion ?: JsKlibMetadataVersion.INSTANCE


    private val environment =
        KotlinCoreEnvironment.createForTests(Disposable { }, CompilerConfiguration(), EnvironmentConfigFiles.JS_CONFIG_FILES)
    private val configuration = buildConfiguration(environment)
    private val project = environment.project
    private val phaseConfig = PhaseConfig(jsPhases)

    private val metadataVersion = configuration.metadataVersion
    private val languageVersionSettings = configuration.languageVersionSettings
    private val moduleName = configuration[CommonConfigurationKeys.MODULE_NAME]!!


    private fun isCommonSource(path: String) = listOf("common", "src", "unsigned").map {
        "fullRuntime/src/libraries/stdlib/$it"
    }.any { path.contains(it) }

    fun createPsiFile(fileName: String): KtFile {
        val psiManager = PsiManager.getInstance(environment.project)
        val fileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)

        val file = fileSystem.findFileByPath(fileName) ?: error("File not found: $fileName")

        return (psiManager.findFile(file) as KtFile).apply { isCommonSource = isCommonSource(fileName) }
    }

    private fun File.listAllFiles(): List<File> {
        return if (isDirectory) listFiles().flatMap { it.listAllFiles() }
        else listOf(this)
    }

    private fun createPsiFileFromDir(path: String): List<KtFile> = File(path).listAllFiles().map { createPsiFile(it.path) }

    private val fullRuntimeSourceSet = createPsiFileFromDir("compiler/ir/serialization.js/build/fullRuntime/src")
    private val reducedRuntimeSourceSet = createPsiFileFromDir("compiler/ir/serialization.js/build/reducedRuntime/src")

    @Test
    fun runFullPipeline() {
        repeat(2) {
            compile(fullRuntimeSourceSet)
        }
    }

    @Test
    fun runWithoutFrontEnd() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)

        repeat(10) {
            val rawModuleFragment = doPsi2Ir(files, analysisResult)

            val modulePath = doSerializeModule(rawModuleFragment, analysisResult.bindingContext, files)

            val (module, symbolTable, irBuiltIns) = doDeserializeModule(modulePath)

            val jsProgram = doBackEnd(module, symbolTable, irBuiltIns)
        }
    }

    @Test
    fun runPsi2Ir() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)

        repeat(200) {
            doPsi2Ir(files, analysisResult)
        }
    }

    @Test
    fun runSerialization() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)

        repeat(30) {
            doSerializeModule(rawModuleFragment, analysisResult.bindingContext, files)
        }
    }

    enum class MeasureUnits(val delimeter: Long, private val suffix: String) {
        NANOSECUNDS(1L, "ns"),
        MICROSECUNDS(1000L, "mcs"),
        MILISECUNDS(1000L * 1000L, "ms");

        fun convert(nanos: Long): String = "${(nanos / delimeter)}$suffix"
    }

    @Test
    fun runIrDeserializationMonolitic() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)
        val modulePath = doSerializeModule(rawModuleFragment, analysisResult.bindingContext, files, false)
        val moduleRef = loadKlib(modulePath, isPacked = false, perFile = false)
        val moduleDescriptor = doDeserializeModuleMetadata(moduleRef)

        runBenchWithWarmup("Ir Deserialization Monolithic", 40, 10, MeasureUnits.MILISECUNDS, System::gc) {
            doDeserializeIrModule(moduleDescriptor)
        }
    }

    @Test
    fun runIrDeserializationPerFile() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)
        val modulePath = doSerializeModule(rawModuleFragment, analysisResult.bindingContext, files, true)
        val moduleRef = loadKlib(modulePath, isPacked = false, perFile = true)
        val moduleDescriptor = doDeserializeModuleMetadata(moduleRef)

        runBenchWithWarmup("Ir Deserialization Per-File", 40, 10, MeasureUnits.MILISECUNDS, System::gc) {
            doDeserializeIrModule(moduleDescriptor)
        }
    }

    @Test
    fun runIrSerialization() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)

        runBenchWithWarmup("Ir Serialization", 10, 30, MeasureUnits.MILISECUNDS, System::gc) {
            doSerializeIrModule(rawModuleFragment)
        }
    }

    @Test
    fun runMonoliticDiskWriting() {
        val abiVersion = KotlinAbiVersion.CURRENT
        val compilerVersion = KonanVersionImpl(MetaVersion.DEV, 1, 3, 0, -1)
        val libraryVersion = "JSIR"

        val versions = KonanLibraryVersioning(abiVersion = abiVersion, libraryVersion = libraryVersion, compilerVersion = compilerVersion)
        val file = createTempFile()
        val writer = KotlinLibraryOnlyIrWriter(file.absolutePath, "", versions, false)
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)
        val fileCount = rawModuleFragment.files.size
        val serializedIr = doSerializeIrModule(rawModuleFragment)

        runBenchWithWarmup("Monolitic Disk Writing of $fileCount files", 10, 30, MeasureUnits.MILISECUNDS, writer::invalidate) {
            doWriteIrModuleToStorage(serializedIr, writer)
        }
    }

    @Test
    fun runPerfileDiskWriting() {
        val abiVersion = KotlinAbiVersion.CURRENT
        val compilerVersion = KonanVersionImpl(MetaVersion.DEV, 1, 3, 0, -1)
        val libraryVersion = "JSIR"

        val versions = KonanLibraryVersioning(abiVersion = abiVersion, libraryVersion = libraryVersion, compilerVersion = compilerVersion)
        val file = createTempFile()
        val writer = KotlinLibraryOnlyIrWriter(file.absolutePath, "", versions, true)
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)
        val fileCount = rawModuleFragment.files.size
        val serializedIr = doSerializeIrModule(rawModuleFragment)

        runBenchWithWarmup("Per-file Disk Writing of $fileCount files", 10, 30, MeasureUnits.MILISECUNDS, writer::invalidate) {
            doWriteIrModuleToStorage(serializedIr, writer)
        }
    }

    private fun runBenchWithWarmup(name: String, W: Int, N: Int, measurer: MeasureUnits, pre: () -> Unit = {}, bench: () -> Unit) {

        println("Run $name benchmark")

        println("Warmup: $W times...")

        repeat(W) {
            println("W: ${it + 1} out of $W")
            pre()
            bench()
        }

        var total = 0L

        println("Run bench: $N times...")

        repeat(N) {
            print("R: ${it + 1} out of $N ")
            pre()
            val start = System.nanoTime()
            bench()
            val iter = System.nanoTime() - start
            println("takes ${measurer.convert(iter)}")
            total += iter
        }

        println("$name takes ${measurer.convert(total / N)}")
    }

    @Test
    fun runDeserialization() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)
        val modulePath = doSerializeModule(rawModuleFragment, analysisResult.bindingContext, files)

        repeat(20) {
            doDeserializeModule(modulePath)
        }
    }

    @Test
    fun runDeserializationAndBackend() {
        val files = fullRuntimeSourceSet
        val analysisResult = doFrontEnd(files)
        val rawModuleFragment = doPsi2Ir(files, analysisResult)
        val modulePath = doSerializeModule(rawModuleFragment, analysisResult.bindingContext, files)

        repeat(100) {
            val (module, symbolTable, irBuiltIns) = doDeserializeModule(modulePath)
            doBackEnd(module, symbolTable, irBuiltIns)
        }
    }

    private fun doFrontEnd(files: List<KtFile>): AnalysisResult {
        val analysisResult =
            TopDownAnalyzerFacadeForJS.analyzeFiles(
                files,
                project,
                configuration,
                emptyList(),
                friendModuleDescriptors = emptyList(),
                thisIsBuiltInsModule = true,
                customBuiltInsModule = null
            )

        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()
        TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)

        return analysisResult
    }

    private fun doPsi2Ir(files: List<KtFile>, analysisResult: AnalysisResult): IrModuleFragment {
        val psi2IrContext = GeneratorContext(
            Psi2IrConfiguration(),
            analysisResult.moduleDescriptor,
            analysisResult.bindingContext,
            languageVersionSettings,
            SymbolTable(),
            GeneratorExtensions()
        )
        return Psi2IrTranslator(languageVersionSettings, psi2IrContext.configuration).generateModuleFragment(psi2IrContext, files)
    }

    private fun doSerializeModule(moduleFragment: IrModuleFragment, bindingContext: BindingContext, files: List<KtFile>, perFile: Boolean = false): String {
        val tmpKlibDir = createTempDir().also { it.deleteOnExit() }
        serializeModuleIntoKlib(
            moduleName,
            configuration,
            bindingContext,
            files,
            tmpKlibDir.path,
            emptyList(),
            moduleFragment,
            emptyList(),
            true,
            perFile
        )

        return tmpKlibDir.path
    }

    private fun doDeserializeModuleMetadata(moduleRef: KotlinLibrary): ModuleDescriptorImpl {
        val storageManager = LockBasedStorageManager("ModulesStructure")

        val parts = loadKlibMetadataParts(moduleRef)

        return loadKlibMetadata(
            parts,
            moduleRef,
            true,
            lookupTracker,
            storageManager,
            metadataVersion,
            languageVersionSettings,
            null,
            emptyList()
        )
    }

    private data class DeserializedModuleInfo(val module: IrModuleFragment, val symbolTable: SymbolTable, val irBuiltIns: IrBuiltIns)


    private fun doSerializeIrModule(module: IrModuleFragment): SerializedIrModule {
        val descriptorTable = DescriptorTable()
        val serializedIr = JsIrModuleSerializer(logger, module.irBuiltins, descriptorTable).serializedIrModule(module)
        return serializedIr
    }

    private fun doWriteIrModuleToStorage(serializedIrModule: SerializedIrModule, writer: KotlinLibraryOnlyIrWriter) {
        writer.writeIr(serializedIrModule)
    }

    private fun doDeserializeIrModule(moduleDescriptor: ModuleDescriptorImpl): DeserializedModuleInfo {
        val symbolTable = SymbolTable()
        val typeTranslator = TypeTranslator(symbolTable, languageVersionSettings, moduleDescriptor.builtIns).also {
            it.constantValueGenerator = ConstantValueGenerator(moduleDescriptor, symbolTable)
        }

        val irBuiltIns = IrBuiltIns(moduleDescriptor.builtIns, typeTranslator, symbolTable)

        val jsLinker = JsIrLinker(moduleDescriptor, JsMangler, logger, irBuiltIns, symbolTable)

        val moduleFragment = jsLinker.deserializeFullModule(moduleDescriptor)
        // Create stubs
        ExternalDependenciesGenerator(
            moduleDescriptor = moduleDescriptor,
            symbolTable = symbolTable,
            irBuiltIns = irBuiltIns,
            deserializer = jsLinker
        ).generateUnboundSymbolsAsDependencies()

        moduleFragment.patchDeclarationParents()

        return DeserializedModuleInfo(moduleFragment, symbolTable, irBuiltIns)
    }

    private fun doDeserializeModule(modulePath: String): DeserializedModuleInfo {
        val moduleRef = loadKlib(modulePath, false)
        val moduleDescriptor = doDeserializeModuleMetadata(moduleRef)

        val symbolTable = SymbolTable()
        val typeTranslator = TypeTranslator(symbolTable, languageVersionSettings, moduleDescriptor.builtIns).also {
            it.constantValueGenerator = ConstantValueGenerator(moduleDescriptor, symbolTable)
        }

        val irBuiltIns = IrBuiltIns(moduleDescriptor.builtIns, typeTranslator, symbolTable)

        val jsLinker = JsIrLinker(moduleDescriptor, JsMangler, logger, irBuiltIns, symbolTable)

        val moduleFragment = jsLinker.deserializeFullModule(moduleDescriptor)
        // Create stubs
        ExternalDependenciesGenerator(
            moduleDescriptor = moduleDescriptor,
            symbolTable = symbolTable,
            irBuiltIns = irBuiltIns,
            deserializer = jsLinker
        ).generateUnboundSymbolsAsDependencies()

        moduleFragment.patchDeclarationParents()

        return DeserializedModuleInfo(moduleFragment, symbolTable, irBuiltIns)
    }

    private fun doBackEnd(module: IrModuleFragment, symbolTable: SymbolTable, irBuiltIns: IrBuiltIns): CompilerResult {
        val context = JsIrBackendContext(module.descriptor, irBuiltIns, symbolTable, module, emptySet(), configuration)

        ExternalDependenciesGenerator(module.descriptor, symbolTable, irBuiltIns).generateUnboundSymbolsAsDependencies()

        jsPhases.invokeToplevel(phaseConfig, context, module)

        val transformer = IrModuleToJsTransformer(context, null, null)

        return transformer.generateModule(module)
    }

    fun compile(files: List<KtFile>): String {
        val analysisResult = doFrontEnd(files)

        val rawModuleFragment = doPsi2Ir(files, analysisResult)

        val modulePath = doSerializeModule(rawModuleFragment, analysisResult.bindingContext, files)

        val (module, symbolTable, irBuiltIns) = doDeserializeModule(modulePath)

        val jsProgram = doBackEnd(module, symbolTable, irBuiltIns)

        return jsProgram.toString()
    }
}