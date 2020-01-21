/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle

import org.jetbrains.kotlin.backend.common.serialization.cityHash64
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorExportCheckerVisitor
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorMangleComputer
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.name.Name

abstract class AbstractKotlinMangler<D : Any> : KotlinMangler {
    override val String.hashMangle get() = (this.cityHash64() % PUBLIC_MANGLE_FLAG) or PUBLIC_MANGLE_FLAG

    abstract class AbstractDescriptorMangler : KotlinMangler.DescriptorMangler {

        override fun String.hashMangle() = (this.cityHash64() % PUBLIC_MANGLE_FLAG) or PUBLIC_MANGLE_FLAG

        protected abstract fun getMangleComputer(prefix: String): DescriptorMangleComputer
        protected abstract fun getExportChecker(): DescriptorExportCheckerVisitor

        private fun withPrefix(prefix: String, descriptor: DeclarationDescriptor): String =
            getMangleComputer(prefix).computeMangle(descriptor)

        override fun isExport(declarationDescriptor: DeclarationDescriptor): Boolean =
            getExportChecker().check(declarationDescriptor, SpecialDeclarationType.REGULAR)

        override fun isExportEnumEntry(declarationDescriptor: ClassDescriptor): Boolean =
            getExportChecker().check(declarationDescriptor, SpecialDeclarationType.ENUM_ENTRY)

        override fun mangleDeclaration(descriptor: DeclarationDescriptor) = withPrefix("", descriptor)

        override fun mangleEnumEntry(descriptor: ClassDescriptor) = withPrefix("kenumentry", descriptor)
    }

    protected abstract fun getExportChecker(): KotlinExportChecker<D>
    protected abstract fun getMangleComputer(prefix: String): KotlinMangleComputer<D>
}