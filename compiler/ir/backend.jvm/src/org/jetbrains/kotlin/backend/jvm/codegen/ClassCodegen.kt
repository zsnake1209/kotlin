/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.backend.jvm.codegen

import org.jetbrains.kotlin.backend.common.ir.ir2string
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.backend.jvm.descriptors.JvmDescriptorWithExtraFlags
import org.jetbrains.kotlin.backend.jvm.lower.allOverridden
import org.jetbrains.kotlin.builtins.jvm.JavaToKotlinClassMap
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.AsmUtil.NO_FLAG_PACKAGE_PRIVATE
import org.jetbrains.kotlin.codegen.binding.CodegenBinding.ASM_TYPE
import org.jetbrains.kotlin.codegen.inline.DefaultSourceMapper
import org.jetbrains.kotlin.codegen.inline.SourceMapper
import org.jetbrains.kotlin.codegen.serialization.JvmSerializationBindings
import org.jetbrains.kotlin.codegen.serialization.JvmSerializerExtension
import org.jetbrains.kotlin.codegen.signature.BothSignatureWriter
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.load.java.JavaVisibilities
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.load.kotlin.TypeMappingMode
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.checkers.ExpectedActualDeclarationChecker.Companion.OPTIONAL_EXPECTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.kotlin.serialization.DescriptorSerializer
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmClassSignature
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.Type.getObjectType
import java.io.File
import java.lang.RuntimeException
import java.util.ArrayList
import java.util.LinkedHashSet

open class ClassCodegen protected constructor(
    internal val irClass: IrClass,
    val context: JvmBackendContext,
    private val parentClassCodegen: ClassCodegen? = null
) : InnerClassConsumer {

    private val innerClasses = mutableListOf<IrClass>()

    val state = context.state

    val typeMapper = IrTypeMapper(context.state.typeMapper)

    val descriptor = irClass.descriptor

    private val isAnonymous = irClass.isAnonymousObject

    val type: Type = if (isAnonymous)
        state.bindingContext.get(ASM_TYPE, descriptor)!!
    else typeMapper.mapType(irClass)

    private val sourceManager = context.psiSourceManager

    private val fileEntry = sourceManager.getFileEntry(irClass.fileParent)

    val psiElement = irClass.descriptor.psiElement

    val visitor: ClassBuilder = createClassBuilder()

    open fun createClassBuilder() = state.factory.newVisitor(
        OtherOrigin(psiElement, descriptor),
        type,
        listOf(File(fileEntry.name))
    )

    private var sourceMapper: DefaultSourceMapper? = null

    private val serializerExtension = JvmSerializerExtension(visitor.serializationBindings, state)
    private val serializer: DescriptorSerializer? =
        when (val metadata = irClass.metadata) {
            is MetadataSource.Class -> DescriptorSerializer.create(metadata.descriptor, serializerExtension, parentClassCodegen?.serializer)
            is MetadataSource.File -> DescriptorSerializer.createTopLevel(serializerExtension)
            else -> null
        }

    fun generate() {
        val superClassInfo = irClass.getSuperClassInfo(typeMapper)
        val signature = getSignature(irClass, type, superClassInfo, typeMapper)

        visitor.defineClass(
            psiElement,
            state.classFileVersion,
            irClass.calculateClassFlags(),
            signature.name,
            signature.javaGenericSignature,
            signature.superclassName,
            signature.interfaces.toTypedArray()
        )
        AnnotationCodegen(this, context.state, visitor.visitor::visitAnnotation).genAnnotations(irClass, null)
        /* TODO: Temporary workaround: ClassBuilder needs a pathless name. */
        val shortName = File(fileEntry.name).name
        visitor.visitSource(shortName, null)

        val nestedClasses = irClass.declarations.mapNotNull { declaration ->
            if (declaration is IrClass) {
                ClassCodegen(declaration, context, this)
            } else null
        }

        val companionObjectCodegen = nestedClasses.firstOrNull { it.irClass.isCompanion }

        for (declaration in irClass.declarations) {
            generateDeclaration(declaration, companionObjectCodegen)
        }

        // Generate nested classes at the end, to ensure that codegen for companion object will have the necessary JVM signatures in its
        // trace for properties moved to the outer class
        for (codegen in nestedClasses) {
            codegen.generate()
        }

        generateKotlinMetadataAnnotation()

        done()
    }

    private fun generateKotlinMetadataAnnotation() {
        when (val metadata = irClass.metadata) {
            is MetadataSource.Class -> {
                val classProto = serializer!!.classProto(metadata.descriptor).build()
                writeKotlinMetadata(visitor, state, KotlinClassHeader.Kind.CLASS, 0) {
                    AsmUtil.writeAnnotationData(it, serializer, classProto)
                }
            }
            is MetadataSource.File -> {
                val packageFqName = irClass.getPackageFragment()!!.fqName
                val packageProto = serializer!!.packagePartProto(packageFqName, metadata.descriptors)

                serializerExtension.serializeJvmPackage(packageProto, type)

                writeKotlinMetadata(visitor, state, KotlinClassHeader.Kind.FILE_FACADE, 0) {
                    AsmUtil.writeAnnotationData(it, serializer, packageProto.build())
                    // TODO: JvmPackageName
                }
            }
            else -> {
                writeSyntheticClassMetadata(visitor, state)
            }
        }
    }

    private fun done() {
        writeInnerClasses()

        sourceMapper?.let {
            SourceMapper.flushToClassBuilder(it, visitor)
        }

        visitor.done()
    }

    companion object {
        fun generate(irClass: IrClass, context: JvmBackendContext) {
            val state = context.state

//            // We don't have IR error classes, do we? TODO: check with @dmitry.petrov
//            if (ErrorUtils.isError(descriptor)) {
//                badClass(irClass, state.classBuilderMode)
//                return
//            }

            if (irClass.name == SpecialNames.NO_NAME_PROVIDED) {
                badClass(irClass, state.classBuilderMode)
            }

            ClassCodegen(irClass, context).generate()
        }

        private fun badClass(irClass: IrClass, mode: ClassBuilderMode) {
            if (mode.generateBodies) {
                throw IllegalStateException("Generating bad class in ClassBuilderMode = $mode: ${irClass.dump()}")
            }
        }
    }

    private fun generateDeclaration(declaration: IrDeclaration, companionObjectCodegen: ClassCodegen?) {
        when (declaration) {
            is IrField ->
                generateField(declaration, companionObjectCodegen)
            is IrFunction -> {
                generateMethod(declaration)
            }
            is IrAnonymousInitializer -> {
                // skip
            }
            is IrClass -> {
                // Nested classes are generated separately
            }
            else -> throw RuntimeException("Unsupported declaration $declaration")
        }
    }

    fun generateLocalClass(klass: IrClass) {
        ClassCodegen(klass, context, this).generate()
    }

    private fun generateField(field: IrField, companionObjectCodegen: ClassCodegen?) {
        if (field.origin == IrDeclarationOrigin.FAKE_OVERRIDE) return

        val fieldType = typeMapper.mapType(field)
        val fieldSignature = typeMapper.mapFieldSignature(field.type, field)
        val fieldName = field.name.asString()
        val fv = visitor.newField(
            field.OtherOrigin, field.calculateCommonFlags(), fieldName, fieldType.descriptor,
            fieldSignature, null/*TODO support default values*/
        )

        AnnotationCodegen(this, state, fv::visitAnnotation).genAnnotations(field, fieldType)

        val descriptor = field.metadata?.descriptor
        if (descriptor != null) {
            val codegen = if (JvmAbi.isPropertyWithBackingFieldInOuterClass(descriptor)) {
                companionObjectCodegen ?: error("Class with a property moved from the companion must have a companion:\n${irClass.dump()}")
            } else this
            codegen.visitor.serializationBindings.put(JvmSerializationBindings.FIELD_FOR_PROPERTY, descriptor, fieldType to fieldName)
        }
    }

    private fun generateMethod(method: IrFunction) {
        if (method.origin == IrDeclarationOrigin.FAKE_OVERRIDE) return

        val signature = FunctionCodegen(method, this).generate().asmMethod

        val metadata = method.metadata
        when (metadata) {
            is MetadataSource.Property -> {
                // We can't check for JvmLoweredDeclarationOrigin.SYNTHETIC_METHOD_FOR_PROPERTY_ANNOTATIONS because for interface methods
                // moved to DefaultImpls, origin is changed to DEFAULT_IMPLS
                // TODO: fix origin somehow, because otherwise $annotations methods in interfaces also don't have ACC_SYNTHETIC
                assert(method.name.asString().endsWith(JvmAbi.ANNOTATED_PROPERTY_METHOD_NAME_SUFFIX)) { method.dump() }

                val codegen = if (DescriptorUtils.isInterface(metadata.descriptor.containingDeclaration)) {
                    assert(irClass.origin == JvmLoweredDeclarationOrigin.DEFAULT_IMPLS) { irClass.dump() }
                    parentClassCodegen!!
                } else {
                    this
                }
                codegen.visitor.serializationBindings.put(
                    JvmSerializationBindings.SYNTHETIC_METHOD_FOR_PROPERTY, metadata.descriptor, signature
                )
            }
            is MetadataSource.Function -> {
                visitor.serializationBindings.put(JvmSerializationBindings.METHOD_FOR_FUNCTION, metadata.descriptor, signature)
            }
            null -> {
            }
            else -> error("Incorrect metadata source $metadata for:\n${method.dump()}")
        }
    }

    private fun writeInnerClasses() {
        // JVMS7 (4.7.6): a nested class or interface member will have InnerClasses information
        // for each enclosing class and for each immediate member
        val classForInnerClassRecord = getClassForInnerClassRecord()
        if (classForInnerClassRecord != null) {
            parentClassCodegen?.innerClasses?.add(classForInnerClassRecord)

            var codegen: ClassCodegen? = this
            while (codegen != null) {
                val outerClass = codegen.getClassForInnerClassRecord()
                if (outerClass != null) {
                    innerClasses.add(outerClass)
                }
                codegen = codegen.parentClassCodegen
            }
        }

        for (innerClass in innerClasses) {
            writeInnerClass(innerClass, typeMapper, context, visitor)
        }
    }

    private fun getClassForInnerClassRecord(): IrClass? {
        return if (parentClassCodegen != null) irClass else null
    }

    // It's necessary for proper recovering of classId by plain string JVM descriptor when loading annotations
    // See FileBasedKotlinClass.convertAnnotationVisitor
    override fun addInnerClassInfoFromAnnotation(innerClass: IrClass) {
        var current: IrDeclaration? = innerClass
        while (current != null) {
            if (current is IrClass) {
                innerClasses.add(current)
            }
            current = current.parent as? IrDeclaration
        }
    }


    fun getOrCreateSourceMapper(): DefaultSourceMapper {
        if (sourceMapper == null) {
            sourceMapper = context.getSourceMapper(irClass)
        }
        return sourceMapper!!
    }
}

fun IrClass.calculateClassFlags(): Int {
    var flags = 0
    flags = flags or if (isJvmInterface) ACC_INTERFACE else ACC_SUPER
    flags = flags or calcModalityFlag()
    flags = flags or getVisibilityAccessFlagForClass()
    flags = flags or if (kind == ClassKind.ENUM_CLASS) ACC_ENUM else 0
    flags = flags or if (kind == ClassKind.ANNOTATION_CLASS) ACC_ANNOTATION else 0
    return flags
}

fun IrDeclaration.calculateCommonFlags(): Int {
    var flags = 0

    if (this is IrDeclarationWithVisibility) {
        if (Visibilities.isPrivate(visibility)) {
            flags = flags.or(ACC_PRIVATE)
        } else if (visibility == Visibilities.PUBLIC || visibility == Visibilities.INTERNAL) {
            flags = flags.or(ACC_PUBLIC)
        } else if (visibility == Visibilities.PROTECTED) {
            flags = flags.or(ACC_PROTECTED)
        } else if (visibility == JavaVisibilities.PACKAGE_VISIBILITY) {
            // default visibility
        } else {
            throw RuntimeException("Unsupported visibility $visibility for declaration ${ir2string(this)}")
        }
    }

    when (this) {
        is IrClass -> flags = flags.or(calcModalityFlag())
        is IrField -> flags = flags.or(calcModalityFlag())
        is IrSimpleFunction -> flags = flags.or(calcModalityFlag())
        else -> {}
    }

    if (this is JvmDescriptorWithExtraFlags) {
        flags = flags or extraFlags
    }

    return flags
}

private fun IrClass.calcModalityFlag(): Int {
    var flags = 0
    when (effectiveModality) {
        Modality.ABSTRACT -> {
            flags = flags.or(ACC_ABSTRACT)
        }
        Modality.FINAL -> {
            if (!isEnumClass) {
                flags = flags.or(ACC_FINAL)
            }
        }
        Modality.OPEN -> {
            assert(!Visibilities.isPrivate(visibility))
        }
        else -> throw RuntimeException("Unsupported modality $modality for IrClass ${ir2string(this)}")
    }

    return flags
}

private fun IrField.calcModalityFlag(): Int {
    var flags = 0
    if (isFinal) {
        flags = flags.or(ACC_FINAL)
    }
    if (isStatic) {
        flags = flags.or(ACC_STATIC)
    }
    return flags
}

private fun IrSimpleFunction.calcModalityFlag(): Int {
    var flags = 0
    when (modality) {
        Modality.ABSTRACT -> {
            flags = flags.or(ACC_ABSTRACT)
        }
        Modality.FINAL -> {
            flags = flags.or(ACC_FINAL)
        }
        Modality.OPEN -> {
            assert(!Visibilities.isPrivate(visibility))
        }
        else -> throw RuntimeException("Unsupported modality $modality for IrSimpleFunction ${ir2string(this)}")
    }

    if (dispatchReceiverParameter == null) {
        flags = flags or ACC_STATIC
    }
    return flags
}

private val IrClass.effectiveModality: Modality
    get() {
        if (modality == Modality.SEALED || isAnnotationClass) {
            return Modality.ABSTRACT
        }

        return modality
    }


private val IrField.OtherOrigin: JvmDeclarationOrigin
    get() = OtherOrigin(descriptor.psiElement, this.descriptor)

internal val IrFunction.OtherOrigin: JvmDeclarationOrigin
    get() = OtherOrigin(descriptor.psiElement, this.descriptor)

private fun IrClass.getSuperClassInfo(typeMapper: IrTypeMapper): IrSuperClassInfo {
    if (isInterface) {
        return IrSuperClassInfo(AsmTypes.OBJECT_TYPE, null)
    }

    for (superType in superTypes) {
        val superClass = superType.safeAs<IrSimpleType>()?.classifier?.safeAs<IrClassSymbol>()?.owner
        if (superClass != null && !superClass.isJvmInterface) {
            return IrSuperClassInfo(typeMapper.mapClass(superClass), superType)
        }
    }

    return IrSuperClassInfo(AsmTypes.OBJECT_TYPE, null)
}