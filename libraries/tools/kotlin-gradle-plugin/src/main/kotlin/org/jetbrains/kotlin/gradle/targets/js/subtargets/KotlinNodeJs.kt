/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.subtargets

import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsPlatformTestRun
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinNodeJsSupport
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsExec
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import javax.inject.Inject

open class KotlinNodeJsSubtarget @Inject constructor(target: KotlinJsTarget) :
    KotlinJsSubTarget(target, "node"),
    KotlinNodeJsSupport {

    override fun runTask(body: NodeJsExec.() -> Unit) {
        project.tasks.withType(NodeJsExec::class.java).named(runTaskName).configure(body)
    }
}

internal open class KotlinNodeJsPlatformConfigurator : KotlinJsPlatformConfigurator<KotlinNodeJsSupport>() {
    override fun configureRun(platform: KotlinNodeJsSupport, compilation: KotlinJsCompilation) {
        val runTaskHolder = NodeJsExec.create(compilation, disambiguateCamelCased(platform, "run"))
        if (platform is KotlinJsSubTarget) {
            platform.target.runTask.dependsOn(runTaskHolder)
        }
    }

    override fun configureDefaultTestFramework(testTask: KotlinJsTest) {
        testTask.useNodeJs { }
    }

    override fun testTaskDescription(jsPlatform: KotlinNodeJsSupport, testRun: KotlinJsPlatformTestRun): String =
        "Run ${disambiguateCamelCased(jsPlatform, "")} " +
                testRun.name.takeIf { it != KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME }?.plus(" ").orEmpty() +
                "tests inside Node.js using the builtin test framework"
}
