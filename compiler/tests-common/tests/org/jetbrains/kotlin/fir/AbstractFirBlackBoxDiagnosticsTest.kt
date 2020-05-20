/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import java.io.File

abstract class AbstractFirBlackBoxDiagnosticsTest : AbstractFirOldFrontendDiagnosticsTest() {
    override fun createTestFileFromPath(filePath: String): File {
        val newPath = if (!File(filePath).readText().contains("// BAD_FIR_RESOLUTION")) filePath else filePath.replace(".kt", ".fir.kt")
        return File(newPath).also {
            prepareTestDataFile(filePath, it)
        }
    }

    override fun compareAndMergeFirAndOriginalFile(oldFile: File, testDataFile: File) {
        compareAndMergeFirFileAndOldFrontendFileForBlackBox(oldFile, testDataFile)
    }
}

