/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.util.KotlinMangler

class IdSignatureDescriptor(private val mangler: KotlinMangler.DescriptorMangler) {

    private inner class DescriptorBasedSignatureBuilder : IdSignatureBuilder<DeclarationDescriptor>(),
        DeclarationDescriptorVisitor<Unit, Nothing?> {

        override fun accept(d: DeclarationDescriptor) {
            d.accept(this, null)
        }

        private fun reportUnexpectedDescriptor(descriptor: DeclarationDescriptor) {
            error("unexpected descriptor $descriptor")
        }

        private fun collectFqNames(descriptor: DeclarationDescriptorNonRoot) {
            descriptor.containingDeclaration.accept(this, null)
            classFanSegments.add(descriptor.name.asString())
        }

        override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, data: Nothing?) {
            packageFqn = descriptor.fqName
        }

        override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, data: Nothing?) {
            packageFqn = descriptor.fqName
        }

        override fun visitVariableDescriptor(descriptor: VariableDescriptor, data: Nothing?) = reportUnexpectedDescriptor(descriptor)

        override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: Nothing?) {
            hash_id = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
        }

        override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(descriptor)

        override fun visitClassDescriptor(descriptor: ClassDescriptor, data: Nothing?) {
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
        }

        override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: Nothing?) {
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
        }

        override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: Nothing?) = reportUnexpectedDescriptor(descriptor)

        override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor, data: Nothing?) {
            hash_id = mangler.run { mangleDeclaration(constructorDescriptor).hashMangle() }
            collectFqNames(constructorDescriptor)
        }

        override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(scriptDescriptor)

        override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: Nothing?) {
            hash_id = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            collectFqNames(descriptor)
            setExpected(descriptor.isExpect)
        }

        override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(descriptor)

        override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor, data: Nothing?) {
            hash_id_acc = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            descriptor.correspondingProperty.accept(this, null)
            classFanSegments.add(descriptor.name.asString())
            setExpected(descriptor.isExpect)
        }

        override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor, data: Nothing?) {
            hash_id_acc = mangler.run { mangleDeclaration(descriptor).hashMangle() }
            descriptor.correspondingProperty.accept(this, null)
            classFanSegments.add(descriptor.name.asString())
            setExpected(descriptor.isExpect)
        }

        override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor, data: Nothing?) =
            reportUnexpectedDescriptor(descriptor)
    }

    private val composer = DescriptorBasedSignatureBuilder()

    fun composeSignature(descriptor: DeclarationDescriptor): IdSignature? {
        return if (mangler.isExport(descriptor)) {
            composer.buildSignature(descriptor)
        } else null
    }

    fun composeEnumEntrySignature(declarationDescriptor: ClassDescriptor): IdSignature? {
        return if (mangler.isExportEnumEntry(declarationDescriptor)) {
            TODO("..")
        } else null
    }
}