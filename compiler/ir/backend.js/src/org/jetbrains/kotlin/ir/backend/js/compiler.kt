/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.CompilerPhaseManager
import org.jetbrains.kotlin.backend.common.LoggingContext
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.*
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.serialization.js.JsModuleDescriptor
import org.jetbrains.kotlin.serialization.js.KotlinJavascriptSerializationUtil
import org.jetbrains.kotlin.serialization.js.KotlinJavascriptSerializerExtension
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadataUtils
import java.io.File

data class Result(val moduleDescriptor: ModuleDescriptor, val generatedCode: String, val moduleFragment: IrModuleFragment)

fun compile(
    project: Project,
    files: List<KtFile>,
    configuration: CompilerConfiguration,
    export: FqName? = null,
    isKlibCompilation: Boolean = false,
    dependencies: List<ModuleDescriptor> = listOf(),
    irDependencyModules: List<IrModuleFragment> = listOf()
): Result {
    val analysisResult =
        TopDownAnalyzerFacadeForJS.analyzeFiles(
            files,
            project,
            configuration,
            dependencies.mapNotNull { it as? ModuleDescriptorImpl },
            emptyList()
        )

    ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

    TopDownAnalyzerFacadeForJS.checkForErrors(files, analysisResult.bindingContext)

    val symbolTable = SymbolTable()
//    irDependencyModules.forEach { symbolTable.loadModule(it) }

    val psi2IrTranslator = Psi2IrTranslator(configuration.languageVersionSettings)
    val psi2IrContext = psi2IrTranslator.createGeneratorContext(analysisResult.moduleDescriptor, analysisResult.bindingContext, symbolTable)

    val moduleDescriptor = analysisResult.moduleDescriptor

//    val deserializer = KonanIrModuleDeserializer(
//        moduleDescriptor,
//        object : LoggingContext {
//            override fun log(message: () -> String) {
//
//            }
//        },
//        psi2IrContext.irBuiltIns,
//        psi2IrContext.symbolTable,
//        null
//    )

//    val irModules = moduleDescriptor.allDependencyModules.map {
//        moduleToLibrary[it].let { l ->
//            deserializer.deserializeIrModule(l, library.wholeIr, true)
//        }
//    }.filterNotNull()

//    val symbols = KonanSymbols(context, generatorContext.symbolTable, generatorContext.symbolTable.lazyWrapper)
    val moduleFragment = psi2IrTranslator.generateModuleFragment(psi2IrContext, files/*, deserializer*/)

    val context = JsIrBackendContext(
        analysisResult.moduleDescriptor,
        psi2IrContext.irBuiltIns,
        psi2IrContext.symbolTable,
        moduleFragment,
        configuration,
        irDependencyModules
    )

    moduleFragment.patchDeclarationParents() // why do we need it?

    if (isKlibCompilation) {
        val declarationTable = DeclarationTable(moduleFragment.irBuiltins, DescriptorTable())
        val serializedIr = IrModuleSerializer(context, declarationTable/*, onlyForInlines = false*/).serializedIrModule(moduleFragment)
        val serializer = KotlinJavascriptSerializationUtil
//        val ddeserializer = KotlinJavascriptSerializationUtil
//        val serializedData = serializer.serializeModule(analysisResult.moduleDescriptor, serializedIr)
        val moduleDescription = JsModuleDescriptor(
            name = configuration.get(CommonConfigurationKeys.MODULE_NAME) as String,
            data = moduleFragment.descriptor,
            kind = ModuleKind.UMD,
            imported = dependencies.map { it.name.asString() }
        )
        val serializedData = serializer.serializeMetadata(
            psi2IrContext.bindingContext,
            moduleDescription,
            configuration.get(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS)!!,
            configuration.get(CommonConfigurationKeys.METADATA_VERSION)  as? JsMetadataVersion
                ?: JsMetadataVersion.INSTANCE
        ) {
            val index = declarationTable.descriptorTable.get(it)
            index?.let { newDescriptorUniqId(it) }
        }
//        buildLibrary(serializedData)
//

        val stdKlibDir = File("js/js.translator/testData/out/klibs/runtime/").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        val moduleFile = File(stdKlibDir, "module.kji")
        moduleFile.writeBytes(serializedIr.module)

        for ((id, data) in serializedIr.declarations) {
            val file = File(stdKlibDir, "${id.index}${if (id.isLocal) "L" else "G"}.kjd")
            file.writeBytes(data)
        }


        val debugFile = File(stdKlibDir, "debug.txt")

        for ((id, data) in serializedIr.debugIndex) {
            debugFile.appendText(id.toString())
            debugFile.appendText(" --- ")
            debugFile.appendText(data)
            debugFile.appendText("\n")
        }

        val metadata = File(stdKlibDir, "meta.kjm").also { it.writeText(serializedData.asString()) }

        val deserializer = KonanIrModuleDeserializer(
            moduleDescriptor,
            context,
            context.irBuiltIns,
            SymbolTable(),
            stdKlibDir,
            null
        )

        val storageManager = LockBasedStorageManager("JsConfig")
        // CREATE NEW MODULE DESCRIPTOR HERE AND DESERIALIZE IT

        val metadatas = KotlinJavascriptMetadataUtils.loadMetadata(metadata)
        val mt = metadatas.single()
        val desc = serializer.readModuleAsProto(mt.body, mt.version)


        val deserializedModuleFragment = deserializer.deserializeIrModule(moduleDescriptor, moduleFile.readBytes(), true)


//        return
        TODO("Implemenet IrSerialization")
    }

    CompilerPhaseManager(context, context.phases, moduleFragment, JsPhaseRunner).run {
        jsPhases.fold(data) { m, p -> phase(p, context, m) }
    }

    return Result(analysisResult.moduleDescriptor, context.jsProgram.toString(), context.moduleFragmentCopy)
}