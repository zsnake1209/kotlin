/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsPlatformSupport
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinJsPlatformConfigurator
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import org.jetbrains.kotlin.gradle.testing.internal.kotlinTestRegistry
import org.jetbrains.kotlin.gradle.testing.testTaskName

abstract class AbstractKotlinJsTargetConfigurator<TargetType : AbstractKotlinJsTarget<TestRunType>, TestRunType>(
    kotlinPluginVersion: String
) :
    KotlinOnlyTargetConfigurator<KotlinJsCompilation, TargetType>(true, true, kotlinPluginVersion),
    KotlinTargetWithTestsConfigurator<TestRunType, TargetType>
        where TestRunType : KotlinTargetTestRun<*>, TestRunType : ExecutionTaskHolder<*> {

    override fun configureCompilations(target: TargetType) {
        super.configureCompilations(target)

        target.compilations.all {
            it.compileKotlinTask.kotlinOptions {
                moduleKind = "umd"
                sourceMap = true
                sourceMapEmbedSources = null
            }
        }
    }

    override fun buildCompilationProcessor(compilation: KotlinJsCompilation): KotlinSourceSetProcessor<*> {
        val tasksProvider = KotlinTasksProvider(compilation.target.targetName)
        return Kotlin2JsSourceSetProcessor(compilation.target.project, tasksProvider, compilation, kotlinPluginVersion)
    }
}

open class KotlinJsTargetConfigurator(kotlinPluginVersion: String) :
    AbstractKotlinJsTargetConfigurator<KotlinJsTarget, KotlinJsReportAggregatingTestRun>(kotlinPluginVersion) {

    override val testRunClass: Class<KotlinJsReportAggregatingTestRun> get() = KotlinJsReportAggregatingTestRun::class.java

    override fun createTestRun(
        name: String,
        testable: KotlinJsTarget
    ): KotlinJsReportAggregatingTestRun {
        val result = KotlinJsReportAggregatingTestRun(name, testable)

        val testTask = testable.project.kotlinTestRegistry.getOrCreateAggregatedTestTask(
            name = result.testTaskName,
            description = "Run JS tests for all platforms"
        )

        // workaround to avoid the infinite recursion in item factories of the target and the subtargets:
        testable.testRuns.matching { it.name == name }.whenObjectAdded {
            it.configureAllExecutions {
                // do not do anything with the aggregated test run, but ensure that they are created
            }
        }

        result.executionTask = testTask

        return result
    }
}

internal open class KotlinJsPlatformTargetConfigurator<TargetType>(
    kotlinPluginVersion: String,
    private val platformConfigurator: KotlinJsPlatformConfigurator<TargetType>
) : AbstractKotlinJsTargetConfigurator<TargetType, KotlinJsPlatformTestRun>(kotlinPluginVersion),
    KotlinTestsConfigurator<KotlinJsPlatformTestRun, TargetType>
        where TargetType : AbstractKotlinJsTarget<KotlinJsPlatformTestRun>, TargetType : KotlinJsPlatformSupport {

    override val testRunClass: Class<KotlinJsPlatformTestRun>
        get() = platformConfigurator.testRunClass

    override fun createTestRun(name: String, testable: TargetType): KotlinJsPlatformTestRun =
        platformConfigurator.createTestRun(name, testable)

    override fun configurePlatformSpecificModel(target: TargetType) {
        super.configurePlatformSpecificModel(target)
        platformConfigurator.configurePlatformSpecificModel(target)
    }
}