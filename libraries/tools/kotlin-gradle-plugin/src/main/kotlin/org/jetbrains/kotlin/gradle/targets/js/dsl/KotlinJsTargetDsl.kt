/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.dsl

import groovy.lang.Closure
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.util.ConfigureUtil
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.KotlinTestable
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsReportAggregatingTestRun
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsPlatformTestRun
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.subtargets.disambiguateCamelCased
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack

interface KotlinJsTargetDsl {
    fun browser() = browser { }
    fun browser(body: KotlinBrowserJsSupport.() -> Unit)
    fun browser(fn: Closure<*>) {
        browser {
            ConfigureUtil.configure(fn, this)
        }
    }

    fun nodejs() = nodejs { }
    fun nodejs(body: KotlinNodeJsSupport.() -> Unit)
    fun nodejs(fn: Closure<*>) {
        nodejs {
            ConfigureUtil.configure(fn, this)
        }
    }

    val testRuns: NamedDomainObjectContainer<KotlinJsReportAggregatingTestRun>
}

interface KotlinJsPlatformSupport : KotlinTestable<KotlinJsPlatformTestRun> {
    val runTaskName: String
    val testTaskName: String

    fun testTask(body: KotlinJsTest.() -> Unit) {
        testRuns.getByName(KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME).executionTask.configure { body(it) }
    }

    fun testTask(fn: Closure<*>) = testTask { ConfigureUtil.configure(fn, this) }

    override val testRuns: NamedDomainObjectContainer<KotlinJsPlatformTestRun>
}

interface KotlinBrowserJsSupport : KotlinJsPlatformSupport {
    val webpackTaskName: String
        get() = disambiguateCamelCased(this, "webpack")

    fun runTask(body: KotlinWebpack.() -> Unit)
    fun runTask(fn: Closure<*>) = runTask { ConfigureUtil.configure(fn, this) }

    fun webpackTask(body: KotlinWebpack.() -> Unit)
    fun webpackTask(fn: Closure<*>) = webpackTask { ConfigureUtil.configure(fn, this) }
}

interface KotlinNodeJsSupport : KotlinJsPlatformSupport {
    fun runTask(body: NodeJsExec.() -> Unit)
    fun runTask(fn: Closure<*>) = runTask { ConfigureUtil.configure(fn, this) }
}