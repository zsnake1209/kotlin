/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.encodings

inline class BinaryCoordinates(val code: Long) {
    private fun diff(): Int = (code ushr 32).toInt()

    val startOffset: Int get() = code.toInt()
    val endOffset: Int get() = startOffset + diff()

    companion object {
        fun encode(startOffset: Int, endOffset: Int): Long {
            assert(startOffset <= endOffset)
            val diff = (endOffset - startOffset).toLong() shl 32
            return (startOffset.toLong() and 0x0FFFFFFFFL) or diff
        }

        fun decode(code: Long) = BinaryCoordinates(code).also { assert(it.startOffset <= it.endOffset) }
    }
}