/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization

import org.jetbrains.kotlin.backend.common.ir.isProperExpect
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

//public var maxSize = Int.MIN_VALUE
//public var minSize = Int.MAX_VALUE
//public var totalSize = 0
//public var totalCount = 0

val mangleSizes = mutableListOf<Int>()
val vpSizes = mutableListOf<Int>()
val tpSizes = mutableListOf<Int>()

private fun <T> Collection<T>.collect(builder: StringBuilder, separator: String, prefix: String, suffix: String, collect: StringBuilder.(T) -> Unit) {
    var first = true

    builder.append(prefix)

    for (e in this) {
        if (first) {
            first = false
        } else {
            builder.append(separator)
        }

        builder.collect(e)
    }

    builder.append(suffix)
}

private const val PUBLIC_MANGLE_FLAG = 1L shl 63

private class IsExportVisitor : IrElementVisitor<Boolean, Nothing?> {

    private val publishedApiAnnotation = FqName("kotlin.PublishedApi")

    private fun IrDeclaration.isExported(annotations: List<IrConstructorCall>, visibility: Visibility?): Boolean {
        if (annotations.hasAnnotation(publishedApiAnnotation)) return true
        if (visibility != null && !visibility.isPubliclyVisible()) return false

        return parent.accept(this@IsExportVisitor, null)
    }

    private fun Visibility.isPubliclyVisible(): Boolean = isPublicAPI || this === Visibilities.INTERNAL

    override fun visitElement(element: IrElement, data: Nothing?): Boolean = error("Should bot reach here ${element.render()}")

    override fun visitDeclaration(declaration: IrDeclaration, data: Nothing?) = declaration.run { isExported(annotations, null) }

    override fun visitField(declaration: IrField, data: Nothing?): Boolean {
        val annotations = declaration.run { correspondingPropertySymbol?.owner?.annotations ?: annotations }
        return declaration.run { isExported(annotations, visibility) }
    }

    override fun visitProperty(declaration: IrProperty, data: Nothing?): Boolean {
        return declaration.run { isExported(annotations, visibility) }
    }

    override fun visitPackageFragment(declaration: IrPackageFragment, data: Nothing?): Boolean = true

    override fun visitTypeAlias(declaration: IrTypeAlias, data: Nothing?): Boolean =
        if (declaration.parent is IrPackageFragment) true
        else declaration.run { isExported(annotations, visibility) }

    override fun visitClass(declaration: IrClass, data: Nothing?): Boolean {
        if (declaration.name == SpecialNames.NO_NAME_PROVIDED) return false
        return declaration.run { isExported(annotations, visibility) }
    }

    override fun visitConstructor(declaration: IrConstructor, data: Nothing?): Boolean {
        val klass = declaration.constructedClass
        return if (klass.kind.isSingleton) klass.accept(this, null) else declaration.run { isExported(annotations, visibility) }
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?): Boolean {
        val annotations = declaration.run { correspondingPropertySymbol?.owner?.annotations ?: annotations }
        return declaration.run { isExported(annotations, visibility) }
    }
}

private open class ManglerVisitor(private val builder: StringBuilder) : IrElementVisitor<Unit, Boolean> {

    private val typeParameterContainer = ArrayList<IrDeclaration>(4)

    private var isRealExpect = false

    open val IrFunction.platformSpecificFunctionName: String? get() = null

    private fun addPrefix(prefix: String, addPrefix: Boolean): Int {
        if (addPrefix) {
            builder.append(prefix)
            builder.append(':')
        }
        return builder.length
    }

    private fun IrDeclarationWithName.mangleSimpleDeclaration(prefix: String, addPrefix: Boolean, name: String) {
        val prefixLength = addPrefix(prefix, addPrefix)
        parent.accept(this@ManglerVisitor, false)

        if (prefixLength != builder.length) builder.append('.')

        builder.append(name)
    }

    private fun IrFunction.mangleFunction(prefix: Boolean) {

        isRealExpect = isRealExpect or isExpect

        val prefixLength = addPrefix("kfun", prefix)

        typeParameterContainer.add(this)
        parent.accept(this@ManglerVisitor, false)

        if (prefixLength != builder.length) builder.append('.')

        builder.append('#')

        if (visibility != Visibilities.INTERNAL) builder.append(name)
        else {
            builder.append(name)
            builder.append('$')
            builder.append(module.name.asString().run { substring(1, lastIndex) })
        }

        mangleSignature()

        if (prefix && isRealExpect) builder.append("#expect")
    }

    private fun IrFunction.mangleSignature() {

        extensionReceiverParameter?.let {
            builder.append('@')
            mangleValueParameter(builder, it)
        }

        valueParameters.collect(builder, ";", "(", ")") { mangleValueParameter(this, it) }
        typeParameters.collect(builder, ";", "{", "}") { mangleTypeParameter(this, it) }

        if (!returnType.isUnit()) {
            mangleType(builder, returnType)
        }
    }

    private fun IrTypeParameter.effectiveParent(): IrDeclaration = when (val irParent = parent) {
        is IrSimpleFunction -> irParent.correspondingPropertySymbol?.owner ?: irParent
        is IrTypeParametersContainer -> irParent
        else -> error("Unexpected type parameter container ${irParent.render()} for TP ${render()}")
    }

    private fun mangleValueParameter(vpBuilder: StringBuilder, param: IrValueParameter) {
        mangleType(vpBuilder, param.type)

        if (param.isVararg) vpBuilder.append("...")
    }

    private fun mangleTypeParameter(tpBuilder: StringBuilder, param: IrTypeParameter) {
        tpBuilder.append(param.index)
        tpBuilder.append('§')

        param.superTypes.collect(tpBuilder, "&", "<", ">") { mangleType(this, it) }
    }

    private fun StringBuilder.mangleTypeParameterReference(typeParameter: IrTypeParameter) {
        val parent = typeParameter.effectiveParent()
        val ci = typeParameterContainer.indexOf(parent)
        // TODO: what should we do in this case?
//            require(ci >= 0) { "No type container found for ${typeParameter.render()}" }
        append(ci)
        append(':')
        append(typeParameter.index)
    }

    private fun mangleType(tBuilder: StringBuilder, type: IrType) {
        when (type) {
            is IrSimpleType -> {
                when (val classifier = type.classifier) {
                    is IrClassSymbol -> classifier.owner.accept(ManglerVisitor(tBuilder), false)
                    is IrTypeParameterSymbol -> tBuilder.mangleTypeParameterReference(classifier.owner)
                }

                type.arguments.ifNotEmpty {
                    collect(tBuilder, ",", "<", ">") { arg ->
                        when (arg) {
                            is IrStarProjection -> append("*")
                            is IrTypeProjection -> {
                                if (arg.variance != Variance.INVARIANT) {
                                    append(arg.variance.label)
                                    append('|')
                                }

                                mangleType(this, arg.type)
                            }
                        }
                    }
                }

                if (type.hasQuestionMark) tBuilder.append('?')
            }
            is IrDynamicType -> tBuilder.append("<dynamic>")
        }
    }

    private fun IrClass.isBuiltFunctionClass(): Boolean {
        (parent as? IrPackageFragment)?.let { if (it.fqName !in functionalPackages) return false } ?: return false

        return functionPattern.matcher(name.asString()).find()
    }

    override fun visitElement(element: IrElement, data: Boolean) = error("unexpected element ${element.render()}")

    override fun visitClass(declaration: IrClass, data: Boolean) {
        if (data && declaration.isBuiltFunctionClass()) {
            builder.append(KotlinMangler.functionClassSymbolName(declaration.name))
            return
        }

        isRealExpect = isRealExpect or declaration.isExpect
        typeParameterContainer.add(declaration)
        declaration.mangleSimpleDeclaration("ktype", data, declaration.name.asString())

        if (data && isRealExpect) builder.append("#expect")
    }

    override fun visitPackageFragment(declaration: IrPackageFragment, data: Boolean) {
        declaration.fqName.let { builder.append(if (it.isRoot) "" else it) }
    }

    override fun visitProperty(declaration: IrProperty, data: Boolean) {
        val extensionReceiver = declaration.run { (getter ?: setter)?.extensionReceiverParameter }

        val prefixLength = addPrefix("kprop", data)

        isRealExpect = isRealExpect or declaration.isExpect
        typeParameterContainer.add(declaration)
        declaration.parent.accept(this, false)

        if (prefixLength != builder.length) builder.append('.')

        if (extensionReceiver != null) {
            builder.append('@')
            mangleValueParameter(builder, extensionReceiver)
        }

        builder.append(declaration.name)
        if (data && isRealExpect) builder.append("#expect")
    }

    override fun visitField(declaration: IrField, data: Boolean) = declaration.mangleSimpleDeclaration("kfield", data, declaration.name.asString())

    override fun visitEnumEntry(declaration: IrEnumEntry, data: Boolean) = declaration.mangleSimpleDeclaration("kenumentry", data, declaration.name.asString())

    override fun visitTypeAlias(declaration: IrTypeAlias, data: Boolean) = declaration.mangleSimpleDeclaration("ktypealias", data, declaration.name.asString())

    override fun visitTypeParameter(declaration: IrTypeParameter, data: Boolean) {
        addPrefix("ktypeparam", data)
        declaration.effectiveParent().accept(this, data)

        builder.append('@')
        builder.append(declaration.index)
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Boolean) {

        isRealExpect = isRealExpect or declaration.isExpect

        declaration.correspondingPropertySymbol?.let {
            val length = addPrefix("kfun", data)
            val property = it.owner
            typeParameterContainer.add(property)
            property.parent.accept(this, false)

            if (length != builder.length) builder.append('.')
            builder.append('#')
            builder.append(property.name)

            declaration.extensionReceiverParameter?.let { e ->
                builder.append('@')
                mangleValueParameter(builder, e)
            }

            if (property.getter === declaration) {
                builder.append(":getter:")
            } else {
                builder.append(":setter:")
            }

            if (data && isRealExpect) builder.append("#expect")
            return
        }

        if (declaration.parent.let { it is IrClass && it.isBuiltFunctionClass() }) {
            if (declaration.name.asString() == "invoke") {
                builder.append(KotlinMangler.functionInvokeSymbolName(declaration.parentAsClass.name))
                return
            }
        }

        declaration.platformSpecificFunctionName?.let {
            builder.append(it)
            return
        }

        declaration.mangleFunction(data)
    }

    override fun visitConstructor(declaration: IrConstructor, data: Boolean) = declaration.mangleFunction(data)
}

abstract class KotlinManglerImpl : KotlinMangler {

    override val String.hashMangle get() = (this.cityHash64() % PUBLIC_MANGLE_FLAG) or PUBLIC_MANGLE_FLAG

    private fun hashedMangleImpl1(declaration: IrDeclaration): String {
        return declaration.uniqSymbolName()
    }

    private fun hashedMangleImpl2(declaration: IrDeclaration): String {
        val sb = StringBuilder(256) // this capacity in enough for JS stdlib which 99%% is 225 symbols
        declaration.accept(ManglerVisitor(sb), true)

        mangleSizes.add(sb.length)

        return sb.toString()
    }

    private fun hashedMangleImpl(declaration: IrDeclaration): Long {

        val m1 = hashedMangleImpl1(declaration)
        val m2 = hashedMangleImpl2(declaration)
        if (m1 != m2) println("Classic: $m1\nVisitor: $m2\n")
        val m = m1.hashMangle
        return m
    }

    override val IrDeclaration.hashedMangle: Long
        get() = hashedMangleImpl(this)


    // We can't call "with (super) { this.isExported() }" in children.
    // So provide a hook.
    protected open fun IrDeclaration.isPlatformSpecificExported(): Boolean = false

    override fun IrDeclaration.isExported(): Boolean = isExportedImpl(this)

    /**
     * Defines whether the declaration is exported, i.e. visible from other modules.
     *
     * Exported declarations must have predictable and stable ABI
     * that doesn't depend on any internal transformations (e.g. IR lowering),
     * and so should be computable from the descriptor itself without checking a backend state.
     */

    private val isExportedVisitor = IsExportVisitor()

    private fun isExportedImpl(declaration: IrDeclaration): Boolean {
        return if (declaration.isPlatformSpecificExported()) true
        else declaration.accept(isExportedVisitor, null)
    }

    private fun IrTypeParameter.effectiveParent(): IrDeclaration = when (val irParent = parent) {
        is IrClass -> irParent
        is IrConstructor -> irParent
        is IrSimpleFunction -> irParent.correspondingPropertySymbol?.owner ?: irParent
        else -> error("Unexpected type parameter container")
    }

    private fun collectTypeParameterContainers(element: IrElement): List<IrDeclaration> {
        val result = mutableListOf<IrDeclaration>()

        tailrec fun collectTypeParameterContainersImpl(element: IrElement) {
            when (element) {
                is IrConstructor -> result += element
                is IrSimpleFunction -> result.add(element.correspondingPropertySymbol?.owner ?: element)
                is IrProperty -> result += element
                is IrClass -> result += element
                else -> return
            }

            collectTypeParameterContainersImpl((element as IrDeclaration).parent)
        }

        collectTypeParameterContainersImpl(element)

        return result
    }

    private fun mapTypeParameterContainers(element: IrElement): Map<IrDeclaration, Int> {
        return collectTypeParameterContainers(element).mapIndexed { i, d -> d to i }.toMap()
    }

    protected open fun mangleTypeParameter(typeParameter: IrTypeParameter, typeParameterNamer: (IrTypeParameter) -> String?): String {
        return typeParameterNamer(typeParameter) ?: error("No parent for ${typeParameter.render()}")
    }

    protected fun acyclicTypeMangler(type: IrType, typeParameterNamer: (IrTypeParameter) -> String?): String {

        var hashString = type.classifierOrNull?.let {
            when (it) {
                is IrClassSymbol -> it.owner.fqNameForIrSerialization.asString()
                is IrTypeParameterSymbol -> mangleTypeParameter(it.owner, typeParameterNamer)
                else -> error("Unexpected type constructor")
            }
        } ?: "<dynamic>"

        when (type) {
            is IrSimpleType -> {
                if (!type.arguments.isEmpty()) {
                    hashString += "<${type.arguments.map {
                        when (it) {
                            is IrStarProjection -> "*"
                            is IrTypeProjection -> {
                                val variance = it.variance.label
                                val projection = if (variance == "") "" else "${variance}|"
                                projection + acyclicTypeMangler(it.type, typeParameterNamer)
                            }
                            else -> error(it)
                        }
                    }.joinToString(",")}>"
                }

                if (type.hasQuestionMark) hashString += "?"
            }
            !is IrDynamicType -> {
                error(type)
            }
        }
        return hashString
    }

    protected fun typeToHashString(type: IrType, typeParameterNamer: (IrTypeParameter) -> String?) =
        acyclicTypeMangler(type, typeParameterNamer)

    fun IrValueParameter.extensionReceiverNamePart(typeParameterNamer: (IrTypeParameter) -> String?): String =
        "@${typeToHashString(this.type, typeParameterNamer)}"

    open fun IrFunction.valueParamsPart(typeParameterNamer: (IrTypeParameter) -> String?): String {
        return this.valueParameters.joinToString(";", "(", ")") {
            "${typeToHashString(it.type, typeParameterNamer)}${if (it.isVararg) "..." else ""}"
        }
    }

    open fun IrFunction.typeParamsPart(typeParameters: List<IrTypeParameter>, typeParameterNamer: (IrTypeParameter) -> String?): String {
//        if (typeParameters.isEmpty()) return ""

        fun mangleTypeParameter(index: Int, typeParameter: IrTypeParameter): String {
            // We use type parameter index instead of name since changing name is not a binary-incompatible change
            return typeParameter.superTypes.joinToString("&", "$index§<", ">") {
                acyclicTypeMangler(it, typeParameterNamer)
            }
        }

        return typeParameters.withIndex().joinToString(";", "{", "}") { (i, tp) ->
            mangleTypeParameter(i, tp)
        }
    }

    open fun IrFunction.signature(typeParameterNamer: (IrTypeParameter) -> String?): String {
        val extensionReceiverPart = this.extensionReceiverParameter?.extensionReceiverNamePart(typeParameterNamer) ?: ""
        val valueParamsPart = this.valueParamsPart(typeParameterNamer)
        // Distinguish value types and references - it's needed for calling virtual methods through bridges.
        // Also is function has type arguments - frontend allows exactly matching overrides.
        val signatureSuffix =
            when {
//                this.typeParameters.isNotEmpty() -> "Generic"
//                returnType.isInlined -> "ValueType"
                !returnType.isUnit() -> typeToHashString(returnType, typeParameterNamer)
                else -> ""
            }

        val typesParamsPart = this.typeParamsPart(typeParameters, typeParameterNamer)

        return "$extensionReceiverPart$valueParamsPart$typesParamsPart$signatureSuffix"
    }

    open val IrFunction.platformSpecificFunctionName: String? get() = null

    // TODO: rename to indicate that it has signature included
    override val IrFunction.functionName: String
        get() {
            // TODO: Again. We can't call super in children, so provide a hook for now.
            this.platformSpecificFunctionName?.let { return it }

            val typeContainerMap = mapTypeParameterContainers(this)
            val typeParameterNamer: (IrTypeParameter) -> String? = {
                val eParent = it.effectiveParent()
                typeContainerMap[eParent]?.let { ci ->
                    "$ci:${it.index}"
                }
            }

            if (isAccessor) {
                val property = (this as IrSimpleFunction).correspondingPropertySymbol!!.owner
                val suffix = if (this === property.getter) ":getter:" else ":setter:"
                val extensionReceiverPart = extensionReceiverParameter?.extensionReceiverNamePart(typeParameterNamer) ?: ""

                return property.name.asString() + extensionReceiverPart + suffix
            }

            val name = this.name.mangleIfInternal(this.module, this.visibility)
            return "$name${signature(typeParameterNamer)}"
        }

    override val Long.isSpecial: Boolean
        get() = specialHashes.contains(this)

    fun Name.mangleIfInternal(moduleDescriptor: ModuleDescriptor, visibility: Visibility): String =
        if (visibility != Visibilities.INTERNAL) {
            this.asString()
        } else {
            val moduleName = moduleDescriptor.name.asString()
                .let { it.substring(1, it.lastIndex) } // Remove < and >.

            "$this\$$moduleName"
        }

    val IrField.symbolName: String
        get() {
            val containingDeclarationPart = parent.fqNameForIrSerialization.let {
                if (it.isRoot) "" else "$it."
            }
            return "kfield:$containingDeclarationPart$name"

        }

    val IrClass.typeInfoSymbolName: String
        get() {
            assert(isExportedImpl(this))
            if (isBuiltInFunction(this))
                return KotlinMangler.functionClassSymbolName(name)
            return "ktype:" + this.fqNameForIrSerialization.toString()
        }

    val IrTypeParameter.symbolName: String
        get() {

            val parentDeclaration = (parent as? IrSimpleFunction)?.correspondingPropertySymbol?.owner ?: parent
            val containingDeclarationPart = when (parentDeclaration) {
                is IrDeclaration -> parentDeclaration.uniqSymbolName()
                else -> error("Unexpected type parameter parent")
            }
            return "ktypeparam:$containingDeclarationPart@$index"
        }

    val IrTypeAlias.symbolName: String
        get() {
            val containingDeclarationPart = parent.fqNameForIrSerialization.let {
                if (it.isRoot) "" else "$it."
            }
            return "ktypealias:$containingDeclarationPart$name"
        }

// This is a little extension over what's used in real mangling
// since some declarations never appear in the bitcode symbols.

    internal fun IrDeclaration.uniqSymbolName(): String = when (this) {
        is IrFunction -> this.uniqFunctionName
        is IrProperty -> this.symbolName
        is IrClass -> this.typeInfoSymbolName
        is IrField -> this.symbolName
        is IrEnumEntry -> this.symbolName
        is IrTypeParameter -> this.symbolName
        is IrTypeAlias -> this.symbolName
        else -> error("Unexpected exported declaration: $this")
    } + expectPart

    // TODO: need to figure out the proper OptionalExpectation behavior
    private val IrDeclaration.expectPart get() = if (this.isProperExpect) "#expect" else ""

    private val IrDeclarationParent.fqNameUnique: FqName
        get() = when (this) {
            is IrPackageFragment -> this.fqName
            is IrDeclaration -> this.parent.fqNameUnique.child(this.uniqName)
            else -> error(this)
        }

    private val IrDeclaration.uniqName: Name
        get() = when (this) {
            is IrSimpleFunction -> Name.special("<${this.uniqFunctionName}>")
            else -> this.nameForIrSerialization
        }

    private val IrProperty.symbolName: String
        get() {
            val typeContainerMap = mapTypeParameterContainers(this)
            val typeParameterNamer: (IrTypeParameter) -> String = {
                val eParent = it.effectiveParent()
                "${typeContainerMap[eParent] ?: error("No parent for ${it.render()}")}:${it.index}"
            }

            val extensionReceiver: String = getter?.extensionReceiverParameter?.extensionReceiverNamePart(typeParameterNamer) ?: ""

            val containingDeclarationPart = parent.fqNameForIrSerialization.let {
                if (it.isRoot) "" else "$it."
            }
            return "kprop:$containingDeclarationPart$extensionReceiver$name"
        }

    private val IrEnumEntry.symbolName: String
        get() {
            val containingDeclarationPart = parent.fqNameForIrSerialization.let {
                if (it.isRoot) "" else "$it."
            }
            return "kenumentry:$containingDeclarationPart$name"
        }

    // This is basicly the same as .symbolName, but disambiguates external functions with the same C name.
// In addition functions appearing in fq sequence appear as <full signature>.
    private val IrFunction.uniqFunctionName: String
        get() {
            if (isBuiltInFunction(this))
                return KotlinMangler.functionInvokeSymbolName(parentAsClass.name)
            val parent = this.parent

            val containingDeclarationPart = parent.fqNameUnique.let {
                if (it.isRoot) "" else "$it."
            }

            return "kfun:$containingDeclarationPart#$functionName"
        }

    private val specialHashes = listOf("Function", "KFunction", "SuspendFunction", "KSuspendFunction")
        .flatMap { name ->
            (0..255).map { KotlinMangler.functionClassSymbolName(Name.identifier(name + it)) }
        }.map { it.hashMangle }
        .toSet()
}
