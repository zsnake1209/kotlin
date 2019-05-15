/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.serialization

import org.jetbrains.kotlin.backend.common.serialization.GlobalDeclarationTable

class JvmGlobalDeclarationTable : GlobalDeclarationTable(JvmMangler) {
    companion object {
        const val PUBLIC_LOCAL_UNIQ_ID_EDGE = 0x7FFF_FFFF_FFFF_FFFFL + 1L
    }
}
