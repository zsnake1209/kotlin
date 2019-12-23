/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle

val mangleSizes = mutableListOf<Int>()
val vpSizes = mutableListOf<Int>()
val tpSizes = mutableListOf<Int>()

internal fun <T> Collection<T>.collect(builder: StringBuilder, separator: String, prefix: String, suffix: String, collect: StringBuilder.(T) -> Unit) {
    var first = true

    builder.append(prefix)

    for (e in this) {
        if (first) {
            first = false
        } else {
            builder.append(separator)
        }

        builder.collect(e)
    }

    builder.append(suffix)
}

internal const val PUBLIC_MANGLE_FLAG = 1L shl 63
