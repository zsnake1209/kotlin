/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionProvider.KotlinCodeVisionSettings
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import java.io.File

internal class KotlinCodeVisionProviderTest : InlayHintsProviderTestCase() {

    private val testFilesPath = File(PluginTestCaseBase.getTestDataPathBase(), "/codeVision/").absolutePath

    fun testInterfaceImplementations() {
        assertThatActualHintsMatch("InterfaceImplementations.kt", mode = inheritorsEnabled())
    }

    fun testInterfaceAbstractMethodImplementations() {
        assertThatActualHintsMatch("InterfaceAbstractMethodImplementation.kt", mode = inheritorsEnabled())
    }

    fun testInterfaceMethodsOverrides() {
        assertThatActualHintsMatch("InterfaceMethodsOverrides.kt", mode = inheritorsEnabled())
    }

    fun testInterfacePropertiesOverrides() {
        assertThatActualHintsMatch("InterfacePropertiesOverrides.kt", mode = inheritorsEnabled())
    }

    fun testClassInheritors() {
        assertThatActualHintsMatch("ClassInheritors.kt", mode = inheritorsEnabled())
    }

    fun testClassFunctionOverrides() {
        assertThatActualHintsMatch("ClassFunctionOverrides.kt", mode = inheritorsEnabled())
    }

    fun testClassPropertiesOverrides() {
        assertThatActualHintsMatch("ClassPropertiesOverrides.kt", mode = inheritorsEnabled())
    }

    fun testInterfaceUsages() {
        assertThatActualHintsMatch("InterfaceUsages.kt", mode = usagesEnabled())
    }

    fun testInterfaceMethodUsages() {
        assertThatActualHintsMatch("InterfaceMethodUsages.kt", mode = usagesEnabled())
    }

    fun testInterfacePropertyUsages() {
        assertThatActualHintsMatch("InterfacePropertyUsages.kt", mode = usagesEnabled())
    }

    fun testClassUsages() {
        assertThatActualHintsMatch("ClassUsages.kt", mode = usagesEnabled())
    }

    fun testClassMethodUsages() {
        assertThatActualHintsMatch("ClassMethodUsages.kt", mode = usagesEnabled())
    }

    fun testClassPropertyUsages() {
        assertThatActualHintsMatch("ClassPropertyUsages.kt", mode = usagesEnabled())
    }

    fun testGlobalFunctionUsages() {
        assertThatActualHintsMatch("GlobalFunctionUsages.kt", mode = usagesEnabled())
    }

    fun testUsagesAndInheritanceTogether() {
        assertThatActualHintsMatch("UsagesAndInheritanceTogether.kt", mode = usagesAndInheritorsEnabled())
    }

    fun testTooManyUsagesAndInheritors() {
        assertThatActualHintsMatch(
            "TooManyUsagesAndInheritors.kt", mode = usagesAndInheritorsEnabled(),
            usagesLimit = 3, inheritorsLimit = 2
        )
    }

    private fun assertThatActualHintsMatch(
        fileName: String, mode: KotlinCodeVisionSettings, usagesLimit: Int = 100, inheritorsLimit: Int = 100
    ) {
        val codeVisionProvider = KotlinCodeVisionProvider()
        codeVisionProvider.usagesLimit = usagesLimit
        codeVisionProvider.inheritorsLimit = inheritorsLimit

        val testFile = getTestFile(fileName)
        val fileContents = FileUtil.loadFile(testFile, true)
        testProvider("kotlinCodeVision.kt", fileContents, codeVisionProvider, mode)
    }

    private fun getTestFile(name: String): File {
        return File(testFilesPath, name)
    }

    private fun usagesAndInheritorsEnabled(): KotlinCodeVisionSettings = KotlinCodeVisionSettings(showUsages = true, showInheritors = true)

    private fun inheritorsEnabled(): KotlinCodeVisionSettings = KotlinCodeVisionSettings(showUsages = false, showInheritors = true)

    private fun usagesEnabled(): KotlinCodeVisionSettings = KotlinCodeVisionSettings(showUsages = true, showInheritors = false)
}
