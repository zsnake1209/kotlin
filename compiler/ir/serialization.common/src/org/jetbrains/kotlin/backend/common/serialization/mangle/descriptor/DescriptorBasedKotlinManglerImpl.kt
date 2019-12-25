/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle.descriptor

import org.jetbrains.kotlin.backend.common.serialization.mangle.AbstractKotlinMangler
import org.jetbrains.kotlin.backend.common.serialization.mangle.SpecialDeclarationType
import org.jetbrains.kotlin.backend.common.serialization.mangle.descriptorPrefix
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction

abstract class DescriptorBasedKotlinManglerImpl : AbstractKotlinMangler<DeclarationDescriptor>() {

    override val IrDeclaration.hashedMangle: Long
        get() = mangleString.hashMangle

    override fun IrDeclaration.isExported() = getExportChecker().check(descriptor, SpecialDeclarationType.declarationToType(this))

    override val IrFunction.functionName: String
        get() = getMangleComputer("fun").computeMangleString(descriptor)

    override val IrDeclaration.mangleString: String
        get() = getMangleComputer(descriptorPrefix(this)).computeMangle(descriptor)

    override val manglerName = "Descriptor"
}