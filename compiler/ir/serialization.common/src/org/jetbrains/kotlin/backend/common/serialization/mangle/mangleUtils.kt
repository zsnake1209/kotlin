/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle

import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.name.FqName

val mangleSizes = mutableListOf<Int>()

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

internal val publishedApiAnnotation = FqName("kotlin.PublishedApi")

fun descriptorPrefix(declaration: IrDeclaration): String {
    return when (declaration) {
        is IrEnumEntry -> "kenumentry"
        is IrClass -> "kclass"
        is IrField -> "kfield"
        is IrProperty -> "kprop"
        else -> ""
    }
}