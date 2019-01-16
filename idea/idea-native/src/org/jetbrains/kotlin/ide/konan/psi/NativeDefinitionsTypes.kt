/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ide.konan.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.*
import org.jetbrains.kotlin.ide.konan.*
import javax.swing.Icon

class NativeDefinitionsElementType(@NonNls debugName: String) : IElementType(debugName, NativeDefinitionsLanguage.INSTANCE)

class NativeDefinitionsTokenType(@NonNls debugName: String) : IElementType(debugName, NativeDefinitionsLanguage.INSTANCE) {

    override fun toString(): String {
        return "NativeDefinitionsTokenType." + super.toString()
    }
}

class NativeDefinitionsFile(@NotNull viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NativeDefinitionsLanguage.INSTANCE) {

    override fun getFileType(): FileType = NativeDefinitionsFileType.INSTANCE

    override fun toString(): String = NATIVE_DEFINITIONS_DESCRIPTION

    override fun getIcon(flags: Int): Icon? = super.getIcon(flags)
}

