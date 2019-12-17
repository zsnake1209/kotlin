/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.AbstractKotlinTargetConfigurator.Companion.runTaskNameSuffix
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinModeDsl
import org.jetbrains.kotlin.gradle.targets.js.mode.KotlinMode
import org.jetbrains.kotlin.gradle.tasks.locateTask
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport
import org.jetbrains.kotlin.gradle.testing.testTaskName
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import javax.inject.Inject

open class KotlinJsTarget @Inject constructor(project: Project, platformType: KotlinPlatformType) :
    KotlinOnlyTarget<KotlinJsCompilation>(project, platformType),
    KotlinTargetWithTests<JsAggregatingExecutionSource, KotlinJsReportAggregatingTestRun>,
    KotlinJsTargetDsl {
    override lateinit var testRuns: NamedDomainObjectContainer<KotlinJsReportAggregatingTestRun>
        internal set

    val testTaskName get() = testRuns.getByName(KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME).testTaskName
    val testTask: TaskProvider<KotlinTestReport>
        get() = checkNotNull(project.locateTask(testTaskName))

    val runTaskName get() = lowerCamelCaseName(disambiguationClassifier, runTaskNameSuffix)
    val runTask
        get() = project.tasks.maybeCreate(runTaskName).also {
            it.description = "Run js on all configured platforms"
        }

    private val modeConfiguredHandlers = mutableListOf<KotlinModeDsl.() -> Unit>()

    internal val mode: KotlinMode
        get() = _mode ?: throw IllegalStateException("Neither intermediate nor terminal mode is not configured")

    private val _mode: KotlinMode?
        get() {
            if (intermediateLazyDelegate.isInitialized()) {
                return intermediate
            }

            if (terminalLazyDelegate.isInitialized()) {
                return terminal
            }

            return null
        }

    private val intermediateLazyDelegate = lazy {
        project.objects.newInstance(KotlinMode::class.java, this).also {
            modeConfiguredHandlers.forEach { handler -> handler(it) }
            modeConfiguredHandlers.clear()
        }
    }

    val intermediate: KotlinMode by intermediateLazyDelegate

    override fun intermediate(body: KotlinModeDsl.() -> Unit) {
        intermediate.body()
    }

    private val terminalLazyDelegate = lazy {
        project.objects.newInstance(KotlinMode::class.java, this)
    }

    val terminal: KotlinMode by terminalLazyDelegate

    override fun terminal(body: KotlinModeDsl.() -> Unit) {
        terminal.body()
    }

    fun whenModeConfigured(body: KotlinModeDsl.() -> Unit) {
        if (_mode != null) {
            body(mode)
        } else {
            modeConfiguredHandlers += body
        }
    }

    fun useCommonJs() {
        compilations.all {
            it.compileKotlinTask.kotlinOptions {
                moduleKind = "commonjs"
                sourceMap = true
                sourceMapEmbedSources = null
            }
        }
    }
}