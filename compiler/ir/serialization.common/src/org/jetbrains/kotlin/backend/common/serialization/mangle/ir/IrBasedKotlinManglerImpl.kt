/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle.ir

import org.jetbrains.kotlin.backend.common.serialization.mangle.AbstractKotlinMangler
import org.jetbrains.kotlin.backend.common.serialization.mangle.SpecialDeclarationType
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction

abstract class IrBasedKotlinManglerImpl : AbstractKotlinMangler<IrDeclaration>() {

    override val IrDeclaration.hashedMangle: Long
        get() = mangleString.hashMangle

    override fun IrDeclaration.isExported(): Boolean {
        return getExportChecker().check(this, SpecialDeclarationType.REGULAR)
    }

    override val IrFunction.functionName: String
        get() = getMangleComputer("").computeMangleString(this)

    override val IrDeclaration.mangleString: String
        get() = getMangleComputer("").computeMangle(this)

    override val manglerName = "Ir"
}