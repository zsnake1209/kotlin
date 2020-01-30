/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.jvm.serialization

import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicKotlinManglerImpl
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorBasedKotlinManglerImpl
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorExportCheckerVisitor
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor.DescriptorMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.IrBasedKotlinManglerImpl
import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.IrExportCheckerVisitor
import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.IrMangleComputer
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.MainFunctionDetector
import org.jetbrains.kotlin.ir.declarations.IrDeclaration

abstract class AbstractJvmManglerClassic : ClassicKotlinManglerImpl() {
    companion object {
        private val exportChecker = JvmClassicExportChecker()
    }

    private class JvmClassicExportChecker : ClassicExportChecker()

    private class JvmClassicMangleComputer : ClassicMangleComputer()

    override fun getExportChecker(): ClassicExportChecker = exportChecker

    override fun getMangleComputer(prefix: String): KotlinMangleComputer<IrDeclaration> = JvmClassicMangleComputer()
}

object JvmManglerClassic : AbstractJvmManglerClassic()

abstract class AbstractJvmManglerIr : IrBasedKotlinManglerImpl() {

    companion object {
        private val exportChecker = JvmIrExportChecker()
    }

    private class JvmIrExportChecker : IrExportCheckerVisitor() {
        override fun IrDeclaration.isPlatformSpecificExported() = false
    }

    private class JvmIrManglerComputer(builder: StringBuilder, skipSig: Boolean) : IrMangleComputer(builder, skipSig) {
        override fun copy(skipSig: Boolean): IrMangleComputer = JvmIrManglerComputer(builder, skipSig)
    }

    override fun getExportChecker(): KotlinExportChecker<IrDeclaration> = exportChecker

    override fun getMangleComputer(prefix: String): KotlinMangleComputer<IrDeclaration> {
        return JvmIrManglerComputer(StringBuilder(256), false)
    }
}

object JvmManglerIr : AbstractJvmManglerIr()

abstract class AbstractJvmDescriptorMangler(private val mainDetector: MainFunctionDetector?) : DescriptorBasedKotlinManglerImpl() {

    companion object {
        private val exportChecker = JvmDescriptorExportChecker()
    }

    private class JvmDescriptorExportChecker : DescriptorExportCheckerVisitor() {
        override fun DeclarationDescriptor.isPlatformSpecificExported() = false
    }

    private class JvmDescriptorManglerComputer(builder: StringBuilder, prefix: String, private val mainDetector: MainFunctionDetector?, skipSig: Boolean) :
        DescriptorMangleComputer(builder, prefix, skipSig) {
        override fun copy(skipSig: Boolean): DescriptorMangleComputer = JvmDescriptorManglerComputer(builder, specialPrefix, mainDetector, skipSig)

        private fun isMainFunction(descriptor: FunctionDescriptor): Boolean = mainDetector?.isMain(descriptor) ?: false

        override fun FunctionDescriptor.platformSpecificSuffix(): String? {
            return if (isMainFunction(this)) {
                return source.containingFile.name
            } else null
        }
    }

    override fun getExportChecker(): KotlinExportChecker<DeclarationDescriptor> = exportChecker

    override fun getMangleComputer(prefix: String): KotlinMangleComputer<DeclarationDescriptor> {
        return JvmDescriptorManglerComputer(StringBuilder(256), prefix, mainDetector, false)
    }
}

class JvmManglerDesc(mainDetector: MainFunctionDetector? = null) : AbstractJvmDescriptorMangler(mainDetector)