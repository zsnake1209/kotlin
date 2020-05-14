/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.backend

import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.ir.util.IdSignature

class FirSignatureComposer {
    fun composeSignature(declaration: FirDeclaration): IdSignature? = null
    fun composeEnumEntrySignature(enumEntry: FirEnumEntry): IdSignature? = null
}