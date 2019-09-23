/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.serialization

import org.jetbrains.kotlin.backend.common.serialization.KotlinManglerImpl
import org.jetbrains.kotlin.backend.common.serialization.cityHash64
import org.jetbrains.kotlin.backend.jvm.serialization.JvmGlobalDeclarationTable.Companion.PUBLIC_LOCAL_UNIQ_ID_EDGE
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isInlined

// Copied from JsMangler for now
object JvmMangler : KotlinManglerImpl() {
    private const val MOD_VALUE = PUBLIC_LOCAL_UNIQ_ID_EDGE

    override val String.hashMangle: Long get() = cityHash64() % MOD_VALUE

    override val IrType.isInlined: Boolean
        get() = this.isInlined()
}
