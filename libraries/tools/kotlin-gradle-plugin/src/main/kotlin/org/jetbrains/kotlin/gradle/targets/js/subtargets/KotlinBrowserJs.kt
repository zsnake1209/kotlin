/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.subtargets

import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsPlatformTestRun
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinBrowserJsSupport
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
import org.jetbrains.kotlin.gradle.tasks.registerTask
import javax.inject.Inject

open class KotlinBrowserJsSubtarget @Inject constructor(target: KotlinJsTarget) :
    KotlinJsSubTarget(target, "browser"),
    KotlinBrowserJsSupport {

    override val webpackTaskName = disambiguateCamelCased(this, "webpack")

    override fun runTask(body: KotlinWebpack.() -> Unit) {
        project.tasks.withType(KotlinWebpack::class.java).named(runTaskName).configure(body)
    }

    override fun webpackTask(body: KotlinWebpack.() -> Unit) {
        project.tasks.withType(KotlinWebpack::class.java).named(webpackTaskName).configure(body)
    }
}

internal open class KotlinBrowserJsPlatformConfigurator : KotlinJsPlatformConfigurator<KotlinBrowserJsSupport>() {
    override fun configureRun(platform: KotlinBrowserJsSupport, compilation: KotlinJsCompilation) {
        val project = compilation.target.project
        val nodeJs = NodeJsRootPlugin.apply(project.rootProject)

        project.registerTask<KotlinWebpack>(disambiguateCamelCased(platform, "webpack")) {
            val compileKotlinTask = compilation.compileKotlinTask
            it.dependsOn(
                nodeJs.npmInstallTask,
                compileKotlinTask
            )

            it.compilation = compilation
            it.description = "build webpack bundle"

            project.tasks.getByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME).dependsOn(it)
        }

        val run = project.registerTask<KotlinWebpack>(disambiguateCamelCased(platform, "run")) {
            val compileKotlinTask = compilation.compileKotlinTask
            it.dependsOn(
                nodeJs.npmInstallTask,
                compileKotlinTask,
                project.tasks.named(compilation.processResourcesTaskName)
            )

            it.bin = "webpack-dev-server/bin/webpack-dev-server.js"
            it.compilation = compilation
            it.description = "start webpack dev server"

            it.devServer = KotlinWebpackConfig.DevServer(
                open = true,
                contentBase = listOf(compilation.output.resourcesDir.canonicalPath)
            )

            it.outputs.upToDateWhen { false }
        }

        if (platform is KotlinJsSubTarget) {
            platform.target.runTask.dependsOn(run)
        }
    }

    override fun configureDefaultTestFramework(testTask: KotlinJsTest) {
        testTask.useKarma {
            useChromeHeadless()
        }
    }

    override fun testTaskDescription(jsPlatform: KotlinBrowserJsSupport, testRun: KotlinJsPlatformTestRun): String =
        "Run ${disambiguateCamelCased(jsPlatform, "")} " +
                testRun.name.takeIf { it != KotlinTargetWithTests.DEFAULT_TEST_RUN_NAME }?.plus(" ").orEmpty() +
                "tests inside browser using karma and webpack"
}
