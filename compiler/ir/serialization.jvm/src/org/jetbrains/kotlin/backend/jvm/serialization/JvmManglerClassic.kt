/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.serialization

import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicKotlinManglerImpl
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorBasedKotlinManglerImpl
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorExportCheckerVisitor
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorMangleComputer
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isInlined

// Copied from JsMangler for now

abstract class AbstractJvmManglerClassic : ClassicKotlinManglerImpl() {
    companion object {
        private val exportChecker = JvmClassicExportChecker()
    }

    private class JvmClassicExportChecker : ClassicExportChecker()

    private class JvmClassicMangleComputer : ClassicMangleComputer()

    override fun getExportChecker(): ClassicExportChecker = exportChecker

    override fun getMangleComputer(prefix: String): KotlinMangleComputer<IrDeclaration> = JvmClassicMangleComputer()
}

abstract class AbstractJvmDescriptorMangler : DescriptorBasedKotlinManglerImpl() {

    companion object {
        private val exportChecker = JvmDescriptorExportChecker()
    }

    private class JvmDescriptorExportChecker : DescriptorExportCheckerVisitor() {
        override fun DeclarationDescriptor.isPlatformSpecificExported() = false
    }

    private class JvmDescriptorManglerComputer(builder: StringBuilder, prefix: String) : DescriptorMangleComputer(builder, prefix) {
        override fun copy(): DescriptorMangleComputer = JvmDescriptorManglerComputer(builder, specialPrefix)
    }

    override fun getExportChecker(): KotlinExportChecker<DeclarationDescriptor> = exportChecker

    override fun getMangleComputer(prefix: String): KotlinMangleComputer<DeclarationDescriptor> {
        return JvmDescriptorManglerComputer(StringBuilder(256), prefix)
    }

    abstract class AbstractDescriptorManglerImpl : AbstractDescriptorMangler() {
        override fun getMangleComputer(prefix: String): DescriptorMangleComputer = JvmDescriptorManglerComputer(StringBuilder(256), prefix)
        override fun getExportChecker(): DescriptorExportCheckerVisitor = exportChecker
    }
}

object JvmManglerClassic : AbstractJvmManglerClassic() {

}

object JvmDescriptorMangler : AbstractJvmDescriptorMangler.AbstractDescriptorManglerImpl()