/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.backend.common.CompilerPhaseManager
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.backend.js.lower.serialization.*
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.util.ConstantValueGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.TypeTranslator
import org.jetbrains.kotlin.ir.util.patchDeclarationParents
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS
import org.jetbrains.kotlin.js.resolve.JsPlatform
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.CompilerDeserializationConfiguration
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.serialization.js.*
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.utils.JsMetadataVersion
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadataUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.lang.StringBuilder

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

        val metadata = File(stdKlibDir, "${project.name}${KotlinJavascriptMetadataUtils.META_JS_SUFFIX}").also {
            it.writeText(serializedData.asString())
        }

//
        val storageManager = LockBasedStorageManager("JsConfig")
//        // CREATE NEW MODULE DESCRIPTOR HERE AND DESERIALIZE IT
//
        val metadatas = KotlinJavascriptMetadataUtils.loadMetadata(metadata)
        val mt = metadatas.single()
        val lookupTracker = configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER, LookupTracker.DO_NOTHING)
        val parts = serializer.readModuleAsProto(mt.body, mt.version)
        val md = ModuleDescriptorImpl(
            Name.special("<" + mt.moduleName + ">"), storageManager, JsPlatform.builtIns
        )
        val provider = createKotlinJavascriptPackageFragmentProvider(
            storageManager, md, parts.header, parts.body, mt.version,
            CompilerDeserializationConfiguration(configuration.languageVersionSettings),
            lookupTracker
        )

        md.initialize(provider)
        md.setDependencies(listOf(md, md.builtIns.builtInsModule))



//        return moduleDescriptor


//        val md = moduleDescriptor
//        val packageView = md.getPackage(FqName("kotlin"))
//        val memberScope = packageView.memberScope
//            .memberScope.getFunctionNames()

//            print(md)

        val st = SymbolTable()

        val typeTranslator = TypeTranslator(st, configuration.languageVersionSettings).also {
            it.constantValueGenerator = ConstantValueGenerator(md, st)
        }
        //TODO: reference builtIns in DeclarationTable()
        val deserializer = KonanIrModuleDeserializer(
            md,
            context,
            IrBuiltIns(md.builtIns, typeTranslator, st),
            st,
            stdKlibDir,
            null
        )
        val deserializedModuleFragment = deserializer.deserializeIrModule(md, moduleFile.readBytes(), true)


//        return
        TODO("Implemenet IrSerialization")
    }

    CompilerPhaseManager(context, context.phases, moduleFragment, JsPhaseRunner).run {
        jsPhases.fold(data) { m, p -> phase(p, context, m) }
    }

    return Result(analysisResult.moduleDescriptor, context.jsProgram.toString(), context.moduleFragmentCopy)
}


fun moduleToString(desc: ModuleDescriptor): String {
    val stream = ByteArrayOutputStream()
    desc.deepPrint(PrintStream(stream))
    return stream.toString()
}

fun DeclarationDescriptor.deepPrint(outputStream: PrintStream) {
    this.accept(DeepPrintVisitor(PrintVisitor(outputStream)), 0)
}

class DeepPrintVisitor(worker: DeclarationDescriptorVisitor<Boolean, Int>): DeepVisitor<Int>(worker) {

    override fun visitChildren(descriptor: DeclarationDescriptor?, data: Int): Boolean {
        return super.visitChildren(descriptor, data+1)
    }

    override fun visitChildren(descriptors: Collection<DeclarationDescriptor>, data: Int): Boolean {
        return super.visitChildren(descriptors, data+1)
    }
}

class PrintVisitor(private val outputStream: PrintStream): DeclarationDescriptorVisitor<Boolean, Int> {

    fun printDescriptor(descriptor: DeclarationDescriptor, amount: Int): Boolean {
        outputStream.println("${nTabs(amount)} ${descriptor.toString()}")
        return true;
    }

    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitVariableDescriptor(descriptor: VariableDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitClassDescriptor(descriptor: ClassDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitConstructorDescriptor(descriptor: ConstructorDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitScriptDescriptor(descriptor: ScriptDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)

    override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor, data: Int): Boolean
            = printDescriptor(descriptor, data)
}

open class DeepVisitor<D>(val worker: DeclarationDescriptorVisitor<Boolean, D>) : DeclarationDescriptorVisitor<Boolean, D> {

    open fun visitChildren(descriptors: Collection<DeclarationDescriptor>, data: D): Boolean {
        for (descriptor in descriptors) {
            if (!descriptor.accept(this, data)) return false
        }
        return true
    }

    open fun visitChildren(descriptor: DeclarationDescriptor?, data: D): Boolean {
        if (descriptor == null) return true

        return descriptor.accept(this, data)
    }

    fun applyWorker(descriptor: DeclarationDescriptor, data: D): Boolean {
        return descriptor.accept(worker, data)
    }

    fun processCallable(descriptor: CallableDescriptor, data: D): Boolean {
        return applyWorker(descriptor, data)
                && visitChildren(descriptor.getTypeParameters(), data)
                && visitChildren(descriptor.getExtensionReceiverParameter(), data)
                && visitChildren(descriptor.getValueParameters(), data)
    }

    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, data: D): Boolean? {
        return applyWorker(descriptor, data) && visitChildren(DescriptorUtils.getAllDescriptors(descriptor.getMemberScope()), data)
    }

    override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, data: D): Boolean? {
        return applyWorker(descriptor, data) && visitChildren(DescriptorUtils.getAllDescriptors(descriptor.memberScope), data)
    }

    override fun visitVariableDescriptor(descriptor: VariableDescriptor, data: D): Boolean? {
        return processCallable(descriptor, data)
    }

    override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: D): Boolean? {
        return processCallable(descriptor, data)
                && visitChildren(descriptor.getter, data)
                && visitChildren(descriptor.setter, data)
    }

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: D): Boolean? {
        return processCallable(descriptor, data)
    }

    override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor, data: D): Boolean? {
        return applyWorker(descriptor, data)
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor, data: D): Boolean? {
        return applyWorker(descriptor, data)
                && visitChildren(descriptor.getThisAsReceiverParameter(), data)
                && visitChildren(descriptor.getConstructors(), data)
                && visitChildren(descriptor.getTypeConstructor().getParameters(), data)
                && visitChildren(DescriptorUtils.getAllDescriptors(descriptor.getDefaultType().memberScope), data)
    }

    override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: D): Boolean? {
        return applyWorker(descriptor, data) && visitChildren(descriptor.getDeclaredTypeParameters(), data)
    }

    override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: D): Boolean? {
        return applyWorker(descriptor, data) && visitChildren(descriptor.getPackage(FqName.ROOT), data)
    }

    override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor, data: D): Boolean? {
        return visitFunctionDescriptor(constructorDescriptor, data)
    }

    override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor, data: D): Boolean? {
        return visitClassDescriptor(scriptDescriptor, data)
    }

    override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor, data: D): Boolean? {
        return visitVariableDescriptor(descriptor, data)
    }

    override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor, data: D): Boolean? {
        return visitFunctionDescriptor(descriptor, data)
    }

    override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor, data: D): Boolean? {
        return visitFunctionDescriptor(descriptor, data)
    }

    override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor, data: D): Boolean? {
        return applyWorker(descriptor, data)
    }
}

fun nTabs(amount: Int): String {
    return String.format("%1$-${(amount+1)*4}s", "")
}