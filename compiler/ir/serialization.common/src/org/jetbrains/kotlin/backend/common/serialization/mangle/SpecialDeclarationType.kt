/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle

import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrEnumEntry
import org.jetbrains.kotlin.ir.declarations.IrField

enum class SpecialDeclarationType {
    REGULAR,
    ANON_INIT,
    BACKING_FIELD,
    ENUM_ENTRY;

    companion object {
        fun declarationToType(declaration: IrDeclaration): SpecialDeclarationType {
            return when {
                declaration is IrAnonymousInitializer -> ANON_INIT
                declaration is IrEnumEntry -> ENUM_ENTRY
                declaration is IrField && declaration.correspondingPropertySymbol?.owner?.run { isConst || isLateinit && visibility.isPublicAPI } == false -> BACKING_FIELD
                else -> REGULAR
            }
        }
    }
}