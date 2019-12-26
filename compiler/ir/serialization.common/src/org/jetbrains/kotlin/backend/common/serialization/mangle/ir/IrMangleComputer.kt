/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle.ir

import org.jetbrains.kotlin.backend.common.serialization.functionPattern
import org.jetbrains.kotlin.backend.common.serialization.functionalPackages
import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.collect
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

abstract class IrMangleComputer(protected val builder: StringBuilder) : IrElementVisitor<Unit, Boolean>, KotlinMangleComputer<IrDeclaration> {

    private val typeParameterContainer = ArrayList<IrDeclaration>(4)

    private var isRealExpect = false

    open val IrFunction.platformSpecificFunctionName: String? get() = null

    protected abstract fun copy(): IrMangleComputer

    override fun computeMangle(declaration: IrDeclaration): String {
        declaration.accept(this, true)
        return builder.toString()
    }

    override fun computeMangleString(declaration: IrDeclaration): String {
        declaration.accept(this, false)
        return builder.toString()
    }

    private fun addPrefix(prefix: String, addPrefix: Boolean): Int {
        if (addPrefix) {
            builder.append(prefix)
            builder.append(':')
        }
        return builder.length
    }

    private fun IrDeclaration.mangleSimpleDeclaration(prefix: String, addPrefix: Boolean, name: String) {
        val prefixLength = addPrefix(prefix, addPrefix)
        parent.accept(this@IrMangleComputer, false)

        if (prefixLength != builder.length) builder.append('.')

        builder.append(name)
    }

    private fun IrFunction.mangleFunction(isCtor: Boolean, prefix: Boolean) {

        isRealExpect = isRealExpect or isExpect

        val prefixLength = addPrefix("kfun", prefix)

        typeParameterContainer.add(this)
        parent.accept(this@IrMangleComputer, false)

        if (prefixLength != builder.length) builder.append('.')

        builder.append('#')

        if (visibility != Visibilities.INTERNAL) builder.append(name)
        else {
            builder.append(name)
            builder.append('$')
            builder.append(module.name.asString().run { substring(1, lastIndex) })
        }

        mangleSignature(isCtor)

        if (prefix && isRealExpect) builder.append("#expect")
    }

    private fun IrFunction.mangleSignature(isCtor: Boolean) {

        extensionReceiverParameter?.let {
            builder.append('@')
            mangleValueParameter(builder, it)
        }

        valueParameters.collect(builder, ";", "(", ")") { mangleValueParameter(this, it) }
        typeParameters.collect(builder, ";", "{", "}") { mangleTypeParameter(this, it) }

        if (!isCtor && !returnType.isUnit()) {
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
                    is IrClassSymbol -> classifier.owner.accept(copy(), false)
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
            else -> error("Unexpected type $type")
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
        declaration.mangleSimpleDeclaration("kclass", data, declaration.name.asString())

        if (data && isRealExpect) builder.append("#expect")
    }

    override fun visitPackageFragment(declaration: IrPackageFragment, data: Boolean) {
        declaration.fqName.let { if (!it.isRoot) builder.append(it.asString()) }
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

    override fun visitEnumEntry(declaration: IrEnumEntry, data: Boolean) {
        declaration.mangleSimpleDeclaration("kenumentry", data, declaration.name.asString())
        if (data && isRealExpect) builder.append("#expect")
    }

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
            manglePropertyAccessor(it.owner, declaration, data)
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

        declaration.mangleFunction(false, data)
    }

    private fun manglePropertyAccessor(property: IrProperty, declaration: IrSimpleFunction, data: Boolean) {
        val length = addPrefix("kfun", data)

        typeParameterContainer.add(property)
        isRealExpect = isRealExpect or property.isExpect
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
    }

    override fun visitConstructor(declaration: IrConstructor, data: Boolean) = declaration.mangleFunction(true, data)
}
