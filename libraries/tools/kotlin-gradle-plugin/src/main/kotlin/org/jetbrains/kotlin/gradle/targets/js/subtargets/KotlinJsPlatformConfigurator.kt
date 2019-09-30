/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.subtargets

import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.KotlinTestsConfigurator
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsPlatformTestRun
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsPlatformSupport
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmResolverPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.npmProject
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.tasks.registerTask
import org.jetbrains.kotlin.gradle.testing.internal.configureConventions
import org.jetbrains.kotlin.gradle.testing.internal.kotlinTestRegistry
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

internal abstract class KotlinJsPlatformConfigurator<in KotlinJsPlatformType : KotlinJsPlatformSupport> :
    KotlinTestsConfigurator<KotlinJsPlatformTestRun, KotlinJsPlatformType> {

    override val testRunClass: Class<KotlinJsPlatformTestRun>
        get() = KotlinJsPlatformTestRun::class.java

    override fun KotlinJsPlatformType.getProject(): Project = kotlinJsTarget.project

    fun configurePlatformSpecificModel(jsPlatform: KotlinJsPlatformType) {
        val project = jsPlatform.getProject()
        val target = jsPlatform.kotlinJsTarget

        target.compilations.all {
            val npmProject = it.npmProject
            it.compileKotlinTaskHolder.configure { task ->
                task.kotlinOptions.outputFile = npmProject.dir.resolve(npmProject.main).canonicalPath
            }
        }

        NodeJsRootPlugin.apply(project.rootProject)
        NpmResolverPlugin.apply(project)
        configureRun(jsPlatform)
    }

    override fun createTestRun(name: String, testable: KotlinJsPlatformType): KotlinJsPlatformTestRun =
        KotlinJsPlatformTestRun(name, testable).apply {
            testable.kotlinJsTarget.compilations.matching { it.name == KotlinCompilation.TEST_COMPILATION_NAME }.all { testCompilation ->
                configureTestRun(testable, this, NodeJsRootPlugin.apply(testable.getProject().rootProject), testCompilation)
            }
        }

    private fun configureTestRun(
        jsPlatform: KotlinJsPlatformType,
        testRun: KotlinJsPlatformTestRun,
        nodeJs: NodeJsRootExtension,
        compilation: KotlinJsCompilation
    ) {
        val target = jsPlatform.kotlinJsTarget
        val project = jsPlatform.getProject()

        fun KotlinJsPlatformTestRun.subtargetTestTaskName(): String =
            disambiguateCamelCased(
                jsPlatform,
                lowerCamelCaseName(
                    name.takeIf { it != KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME },
                    AbstractKotlinTargetConfigurator.testTaskNameSuffix
                )
            )

        val testJs = project.registerTask<KotlinJsTest>(testRun.subtargetTestTaskName()) { testJs ->
            val compileTask = compilation.compileKotlinTask

            testJs.group = LifecycleBasePlugin.VERIFICATION_GROUP
            testJs.description = testTaskDescription(jsPlatform, testRun)

            testJs.dependsOn(nodeJs.npmInstallTask, compileTask, nodeJs.nodeJsSetupTask)

            testJs.onlyIf {
                compileTask.outputFile.exists()
            }

            testJs.compilation = compilation
            testJs.targetName = listOfNotNull(target.disambiguationClassifier, jsPlatform.disambiguationClassifier)
                .takeIf { it.isNotEmpty() }
                ?.joinToString()

            testJs.configureConventions()
        }

        testRun.executionTask = testJs

        testJs.configure {
            if (it.testFramework == null) {
                configureDefaultTestFramework(it)
            }
        }

        if (jsPlatform is KotlinJsSubTarget) {
            (target as KotlinJsTarget).testRuns.matching { it.name == testRun.name }
                .all { parentTestRun ->
                    target.project.kotlinTestRegistry.registerTestTask(
                        testJs,
                        parentTestRun.executionTask
                    )
                }

        }
    }

    private fun configureRun(jsPlatform: KotlinJsPlatformType) {
        jsPlatform.kotlinJsTarget.compilations.all { compilation ->
            if (compilation.name == KotlinCompilation.MAIN_COMPILATION_NAME) {
                configureRun(jsPlatform, compilation)
            }
        }
    }

    private val KotlinJsPlatformType.disambiguationClassifier
        get() = when (this) {
            is KotlinJsSubTarget -> disambiguationClassifier
            else -> null
        }

    protected abstract fun configureRun(platform: KotlinJsPlatformType, compilation: KotlinJsCompilation)

    protected abstract fun configureDefaultTestFramework(testTask: KotlinJsTest)

    protected abstract fun testTaskDescription(jsPlatform: KotlinJsPlatformType, testRun: KotlinJsPlatformTestRun): String
}