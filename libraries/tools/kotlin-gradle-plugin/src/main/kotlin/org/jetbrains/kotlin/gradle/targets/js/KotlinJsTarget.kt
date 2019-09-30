/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.AbstractKotlinTargetConfigurator.Companion.runTaskNameSuffix
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinOnlyTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinBrowserJsSupport
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinNodeJsSupport
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsPlatformSupport
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsTargetDsl
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.subtargets.*
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinBrowserJsPlatformConfigurator
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinJsPlatformConfigurator
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinNodeJsPlatformConfigurator
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName
import javax.inject.Inject

abstract class AbstractKotlinJsTarget<TestRunType> @Inject constructor(project: Project) :
    KotlinTargetWithTests<TestRunType>,
    KotlinOnlyTarget<KotlinJsCompilation>(project, KotlinPlatformType.js)
        where TestRunType : KotlinTargetTestRun<*>, TestRunType : ExecutionTaskHolder<*> {

    final override lateinit var testRuns: NamedDomainObjectContainer<TestRunType>
        internal set

    val testTaskName: String get() = testRuns.getByName(KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME).executionTask.name

    val runTaskName get() = lowerCamelCaseName(disambiguationClassifier, runTaskNameSuffix)

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

open class KotlinNodeJsTarget @Inject constructor(project: Project) :
    AbstractKotlinJsTarget<KotlinJsPlatformTestRun>(project),
    KotlinNodeJsSupport {
    override fun runTask(body: NodeJsExec.() -> Unit) {
        project.tasks.withType(NodeJsExec::class.java).named(runTaskName).configure(body)
    }
}

open class KotlinBrowserJsTarget @Inject constructor(project: Project) :
    AbstractKotlinJsTarget<KotlinJsPlatformTestRun>(project),
    KotlinBrowserJsSupport {
    override fun runTask(body: KotlinWebpack.() -> Unit) {
        project.tasks.withType(KotlinWebpack::class.java).named(runTaskName).configure(body)
    }

    override fun webpackTask(body: KotlinWebpack.() -> Unit) {
        project.tasks.withType(KotlinWebpack::class.java).named(webpackTaskName).configure(body)
    }
}

open class KotlinJsTarget @Inject constructor(project: Project) :
    AbstractKotlinJsTarget<KotlinJsReportAggregatingTestRun>(project),
    KotlinJsTargetDsl {
    val runTask
        get() = project.tasks.maybeCreate(runTaskName).also {
            it.description = "Run js on all configured platforms"
        }

    private val browserLazyDelegate = lazy {
        project.objects.newInstance(KotlinBrowserJsSubtarget::class.java, this).also {
            KotlinBrowserJsPlatformConfigurator().configurePlatform(it)
            browserConfiguredHandlers.forEach { handler -> handler(it) }
            browserConfiguredHandlers.clear()
        }
    }

    private val browserConfiguredHandlers = mutableListOf<KotlinBrowserJsSupport.() -> Unit>()

    val browser by browserLazyDelegate

    internal val isBrowserConfigured: Boolean = browserLazyDelegate.isInitialized()

    override fun browser(body: KotlinBrowserJsSupport.() -> Unit) {
        body(browser)
    }

    private fun <T : KotlinJsPlatformSupport> KotlinJsPlatformConfigurator<T>.configurePlatform(
        kotlinJsPlatformSupport: T
    ) {
        configurePlatformSpecificModel(kotlinJsPlatformSupport)
        configureTest(kotlinJsPlatformSupport)
    }

    private val nodejsLazyDelegate = lazy {
        project.objects.newInstance(KotlinNodeJsSubtarget::class.java, this).also {
            KotlinNodeJsPlatformConfigurator().configurePlatform(it)
            nodejsConfiguredHandlers.forEach { handler -> handler(it) }
            nodejsConfiguredHandlers.clear()
        }
    }

    private val nodejsConfiguredHandlers = mutableListOf<KotlinNodeJsSupport.() -> Unit>()

    val nodejs by nodejsLazyDelegate

    internal val isNodejsConfigured: Boolean = nodejsLazyDelegate.isInitialized()

    override fun nodejs(body: KotlinNodeJsSupport.() -> Unit) {
        body(nodejs)
    }

    fun whenBrowserConfigured(body: KotlinBrowserJsSupport.() -> Unit) {
        if (browserLazyDelegate.isInitialized()) {
            browser(body)
        } else {
            browserConfiguredHandlers += body
        }
    }

    fun whenNodejsConfigured(body: KotlinNodeJsSupport.() -> Unit) {
        if (nodejsLazyDelegate.isInitialized()) {
            nodejs(body)
        } else {
            nodejsConfiguredHandlers += body
        }
    }
}