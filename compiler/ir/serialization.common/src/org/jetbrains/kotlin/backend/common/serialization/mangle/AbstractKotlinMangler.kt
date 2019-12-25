/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle

import org.jetbrains.kotlin.backend.common.serialization.cityHash64
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.name.Name

abstract class AbstractKotlinMangler<D : Any> : KotlinMangler {
    private val specialHashes = listOf("Function", "KFunction", "SuspendFunction", "KSuspendFunction")
        .flatMap { name ->
            (0..255).map { KotlinMangler.functionClassSymbolName(Name.identifier(name + it)) }
        }.map { it.hashMangle }
        .toSet()

    override val String.hashMangle get() = (this.cityHash64() % PUBLIC_MANGLE_FLAG) or PUBLIC_MANGLE_FLAG

    override val Long.isSpecial: Boolean
        get() = specialHashes.contains(this)


    open val D.platformSpecificFunctionName: String? get() = null

    protected abstract fun getExportChecker(): KotlinExportChecker<D>
    protected abstract fun getMangleComputer(prefix: String): KotlinMangleComputer<D>
}