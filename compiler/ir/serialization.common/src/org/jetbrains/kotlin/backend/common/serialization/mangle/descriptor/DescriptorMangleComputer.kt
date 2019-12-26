/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor

import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.collect
import org.jetbrains.kotlin.builtins.functions.FunctionClassDescriptor
import org.jetbrains.kotlin.builtins.functions.FunctionInvokeDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.DynamicType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.types.typeUtil.isUnit
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty

abstract class DescriptorMangleComputer(protected val builder: StringBuilder, protected val specialPrefix: String) : DeclarationDescriptorVisitor<Unit, Boolean>, KotlinMangleComputer<DeclarationDescriptor> {

    override fun computeMangle(declaration: DeclarationDescriptor): String {
        declaration.accept(this, true)
        return builder.toString()
    }

    override fun computeMangleString(declaration: DeclarationDescriptor): String {
        declaration.accept(this, false)
        return builder.toString()
    }

    protected abstract fun copy(): DescriptorMangleComputer

    private val typeParameterContainer = ArrayList<DeclarationDescriptor>(4)

    private var isRealExpect = false

    private fun addPrefix(prefix: String, addPrefix: Boolean): Int {
        if (addPrefix) {
            builder.append(prefix)
            builder.append(':')
        }
        return builder.length
    }

    private fun DeclarationDescriptor.mangleSimpleDeclaration(prefix: String, addPrefix: Boolean, name: String) {
        val prefixLength = addPrefix(prefix, addPrefix)
        containingDeclaration?.accept(this@DescriptorMangleComputer, false)

        if (prefixLength != builder.length) builder.append('.')

        builder.append(name)
    }

    open val FunctionDescriptor.platformSpecificFunctionName: String? get() = null

    private fun reportUnexpectedDescriptor(descriptor: DeclarationDescriptor) {
        error("unexpected descriptor $descriptor")
    }

    private fun FunctionDescriptor.mangleFunction(isCtor: Boolean, prefix: Boolean) {

        isRealExpect = isRealExpect or isExpect

        val prefixLength = addPrefix("kfun", prefix)

        typeParameterContainer.add(this)
        containingDeclaration.accept(this@DescriptorMangleComputer, false)

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

    private fun FunctionDescriptor.mangleSignature(isCtor: Boolean) {

        extensionReceiverParameter?.let {
            builder.append('@')
            mangleExtensionReceiverParameter(builder, it)
        }

        valueParameters.collect(builder, ";", "(", ")") { mangleValueParameter(this, it) }
        typeParameters.filter { it.containingDeclaration == this }.collect(builder, ";", "{", "}") { mangleTypeParameter(this, it) }

        returnType?.run {
            if (!isCtor && !isUnit()) {
                mangleType(builder, this)
            }
        }
    }

    private fun mangleExtensionReceiverParameter(vpBuilder: StringBuilder, param: ReceiverParameterDescriptor) {
        mangleType(vpBuilder, param.type)
    }

    private fun mangleValueParameter(vpBuilder: StringBuilder, param: ValueParameterDescriptor) {
        mangleType(vpBuilder, param.type)

        if (param.varargElementType != null) vpBuilder.append("...")
    }

    private fun mangleTypeParameter(tpBuilder: StringBuilder, param: TypeParameterDescriptor) {
        tpBuilder.append(param.index)
        tpBuilder.append('§')

        param.upperBounds.collect(tpBuilder, "&", "<", ">") { mangleType(this, it) }
    }

    private fun mangleType(tBuilder: StringBuilder, wtype: KotlinType) {
        when (val type = wtype.unwrap()) {
            is SimpleType -> {
                when (val classifier = type.constructor.declarationDescriptor) {
                    is ClassDescriptor -> classifier.accept(copy(), false)
                    is TypeParameterDescriptor -> tBuilder.mangleTypeParameterReference(classifier)
                    else -> error("Unexpected classifier: $classifier")
                }

                type.arguments.ifNotEmpty {
                    collect(tBuilder, ",", "<", ">") { arg ->
                        if (arg.isStarProjection) {
                            append('*')
                        } else {
                            if (arg.projectionKind != Variance.INVARIANT) {
                                append(arg.projectionKind.label)
                                append('|')
                            }

                            mangleType(this, arg.type)
                        }
                    }
                }

                if (type.isMarkedNullable) tBuilder.append('?')
            }
            is DynamicType -> tBuilder.append("<dynamic>")
            else -> error("Unexpected type $wtype")
        }
    }

    private fun StringBuilder.mangleTypeParameterReference(typeParameter: TypeParameterDescriptor) {
        val parent = typeParameter.containingDeclaration
        val ci = typeParameterContainer.indexOf(parent)
        // TODO: what should we do in this case?
//            require(ci >= 0) { "No type container found for ${typeParameter.render()}" }
        append(ci)
        append(':')
        append(typeParameter.index)
    }

    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, data: Boolean) {
        descriptor.fqName.let { if (!it.isRoot) builder.append(it.asString()) }
    }

    override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, data: Boolean) = reportUnexpectedDescriptor(descriptor)

    override fun visitVariableDescriptor(descriptor: VariableDescriptor, data: Boolean) = reportUnexpectedDescriptor(descriptor)

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: Boolean) {
        if (descriptor is FunctionInvokeDescriptor) {
            if (descriptor.containingDeclaration.name.asString().contains("Function")) {
                builder.append(KotlinMangler.functionInvokeSymbolName(descriptor.containingDeclaration.name))
                return
            }
        }

        descriptor.platformSpecificFunctionName?.let {
            builder.append(it)
            return
        }

        descriptor.mangleFunction(false, data)
    }

    override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor, data: Boolean) {
        addPrefix("ktypeparam", data)
        descriptor.containingDeclaration.accept(this, data)

        builder.append('@')
        builder.append(descriptor.index)
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor, data: Boolean) {
        if (data && descriptor is FunctionClassDescriptor) {
            builder.append(KotlinMangler.functionClassSymbolName(descriptor.name))
            return
        }

        // TODO: what if EnumEntry descriptor?

        isRealExpect = isRealExpect or descriptor.isExpect
        typeParameterContainer.add(descriptor)
        val prefix = if (specialPrefix == "kenumentry") specialPrefix else "kclass"
        descriptor.mangleSimpleDeclaration(prefix, data, descriptor.name.asString())

        if (data && isRealExpect) builder.append("#expect")
    }

    override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: Boolean) {
        descriptor.mangleSimpleDeclaration("ktypealias", data, descriptor.name.asString())
    }

    override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: Boolean) = reportUnexpectedDescriptor(descriptor)

    override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor, data: Boolean) {
        constructorDescriptor.mangleFunction(true, data)
    }

    override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor, data: Boolean) = reportUnexpectedDescriptor(scriptDescriptor)

    override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: Boolean) {
        val extensionReceiver = descriptor.extensionReceiverParameter

        val prefix = if (specialPrefix == "kfield") specialPrefix else "kprop"
        val prefixLength = addPrefix(prefix, data)

        isRealExpect = isRealExpect or descriptor.isExpect
        typeParameterContainer.add(descriptor)
        descriptor.containingDeclaration.accept(this, false)

        if (prefixLength != builder.length) builder.append('.')

        if (extensionReceiver != null) {
            builder.append('@')
            mangleExtensionReceiverParameter(builder, extensionReceiver)
        }

        builder.append(descriptor.name)
        if (data && isRealExpect) builder.append("#expect")
    }

    override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor, data: Boolean) = reportUnexpectedDescriptor(descriptor)

    private fun manglePropertyAccessor(accessor: PropertyAccessorDescriptor, name: String, data: Boolean) {
        val length = addPrefix("kfun", data)

        val property = accessor.correspondingProperty

        typeParameterContainer.add(property)
        isRealExpect = isRealExpect or property.isExpect
        property.containingDeclaration.accept(this, false)

        if (length != builder.length) builder.append('.')
        builder.append('#')
        builder.append(property.name)

        accessor.extensionReceiverParameter?.let { e ->
            builder.append('@')
            mangleExtensionReceiverParameter(builder, e)
        }

        builder.append(name)

        if (data && isRealExpect) builder.append("#expect")
    }

    override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor, data: Boolean) {
        manglePropertyAccessor(descriptor, ":getter:", data)
    }

    override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor, data: Boolean) {
        manglePropertyAccessor(descriptor, ":setter:", data)
    }

    override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor, data: Boolean) = reportUnexpectedDescriptor(descriptor)
}