/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames

class DescriptorExportCheckerVisitor : DeclarationDescriptorVisitor<Boolean, Boolean> {
    private fun reportUnexpectedDescriptor(descriptor: DeclarationDescriptor): Nothing {
        error("unexpected descriptor $descriptor")
    }
    private val publishedApiAnnotation = FqName("kotlin.PublishedApi")

    private fun Visibility.isPubliclyVisible(): Boolean = isPublicAPI || this === Visibilities.INTERNAL

    private fun DeclarationDescriptorNonRoot.isExported(annotations: Annotations, visibility: Visibility?): Boolean {
        if (annotations.hasAnnotation(publishedApiAnnotation)) return true
        if (visibility != null && !visibility.isPubliclyVisible()) return false

        return containingDeclaration.accept(this@DescriptorExportCheckerVisitor, false)
    }

    override fun visitPackageFragmentDescriptor(descriptor: PackageFragmentDescriptor, data: Boolean): Boolean {
        return true
    }

    override fun visitPackageViewDescriptor(descriptor: PackageViewDescriptor, data: Boolean): Boolean {
        return true
    }

    override fun visitVariableDescriptor(descriptor: VariableDescriptor, data: Boolean): Boolean {
        return false
    }

    override fun visitFunctionDescriptor(descriptor: FunctionDescriptor, data: Boolean): Boolean {
        return descriptor.run { isExported(annotations, visibility) }
    }

    override fun visitTypeParameterDescriptor(descriptor: TypeParameterDescriptor, data: Boolean): Boolean {
        return descriptor.run { isExported(annotations, null) }
    }

    override fun visitClassDescriptor(descriptor: ClassDescriptor, data: Boolean): Boolean {
        if (descriptor.name == SpecialNames.NO_NAME_PROVIDED) return false
        return descriptor.run { isExported(annotations, visibility) }
    }

    override fun visitTypeAliasDescriptor(descriptor: TypeAliasDescriptor, data: Boolean) =
        if (descriptor.containingDeclaration is PackageFragmentDescriptor) true
        else descriptor.run { isExported(annotations, visibility) }

    override fun visitModuleDeclaration(descriptor: ModuleDescriptor, data: Boolean): Boolean {
        reportUnexpectedDescriptor(descriptor)
    }

    override fun visitConstructorDescriptor(constructorDescriptor: ConstructorDescriptor, data: Boolean): Boolean {
        val klass = constructorDescriptor.constructedClass
        return if (klass.kind.isSingleton) klass.accept(this, false) else constructorDescriptor.run { isExported(annotations, visibility) }
    }

    override fun visitScriptDescriptor(scriptDescriptor: ScriptDescriptor, data: Boolean): Boolean {
        reportUnexpectedDescriptor(scriptDescriptor)
    }

    override fun visitPropertyDescriptor(descriptor: PropertyDescriptor, data: Boolean): Boolean {
        if (data) return descriptor.annotations.hasAnnotation(publishedApiAnnotation)
        return descriptor.run { isExported(annotations, visibility) }
    }

    override fun visitValueParameterDescriptor(descriptor: ValueParameterDescriptor, data: Boolean): Boolean {
        return false
    }

    override fun visitPropertyGetterDescriptor(descriptor: PropertyGetterDescriptor, data: Boolean): Boolean {
        return descriptor.run { isExported(correspondingProperty.annotations, visibility) }
    }

    override fun visitPropertySetterDescriptor(descriptor: PropertySetterDescriptor, data: Boolean): Boolean {
        return descriptor.run { isExported(correspondingProperty.annotations, visibility) }
    }

    override fun visitReceiverParameterDescriptor(descriptor: ReceiverParameterDescriptor, data: Boolean): Boolean {
        return false
    }

}