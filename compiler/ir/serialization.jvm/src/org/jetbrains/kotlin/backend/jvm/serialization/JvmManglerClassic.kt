/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.serialization

import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinMangleComputer
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicKotlinManglerImpl
import org.jetbrains.kotlin.backend.common.serialization.mangle.classic.ClassicMangleComputer
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


object JvmManglerClassic : AbstractJvmManglerClassic() {

}
