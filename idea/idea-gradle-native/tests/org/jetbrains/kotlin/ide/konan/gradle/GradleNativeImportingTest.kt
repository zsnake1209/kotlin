/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ide.konan.gradle

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.codeInsight.gradle.GradleImportingTestCase
import org.jetbrains.kotlin.idea.project.platform
import org.jetbrains.kotlin.idea.run.KotlinMPPGradleTestTasksProvider
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.plugins.gradle.execution.test.runner.GradleTestRunConfigurationProducer
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.runners.Parameterized
import org.junit.Test

class GradleNativeImportingTest : GradleImportingTestCase() {

    @Test
    fun testProject() {
        val files = importProjectFromTestData()
        println(files)
        files.forEach { println(findTasksToRun(it)) }
        println("Project name: ${myProject.name}")
        runInEdtAndWait {
            runReadAction {
                val moduleManager = ModuleManager.getInstance(myProject)
                moduleManager.modules.forEach {
                    println("$it ${it.moduleTypeName} ${it.platform}")
                    println(KotlinMPPGradleTestTasksProvider().getTasks(it))
                    println(GradleProjectResolverUtil.getGradlePath(it))
                }
            }
        }
    }

    private fun findTasksToRun(file: VirtualFile): List<String>? =
        GradleTestRunConfigurationProducer.findAllTestsTaskToRun(file, myProject)
            .flatMap { it.tasks }
            .sorted()


    override fun getExternalSystemConfigFileName() = GradleConstants.DEFAULT_SCRIPT_NAME

    override fun testDataDirName() = "nativeImport"

    companion object {
        @Parameterized.Parameters(name = "{index}: with Gradle-{0}")
        @Throws(Throwable::class)
        @JvmStatic
        fun data() = listOf(arrayOf("4.10.2"))
    }
}