/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.load.java.components

import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.FilePreprocessorExtension
import org.jetbrains.kotlin.resolve.addElementToSlice
import org.jetbrains.kotlin.resolve.jvm.JvmBindingContextSlices

// TODO: this component is actually only needed by CLI, see CliLightClassGenerationSupport
class FilesByFacadeFqNameIndexer(private val trace: BindingTrace) : FilePreprocessorExtension {
    override fun preprocessFile(file: KtFile) {
        trace.addElementToSlice(JvmBindingContextSlices.FACADE_FQ_NAME_TO_FILES, file.javaFileFacadeFqName, file)
    }
}