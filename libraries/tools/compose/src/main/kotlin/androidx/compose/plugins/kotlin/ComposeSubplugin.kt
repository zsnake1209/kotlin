/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package androidx.compose.plugins.kotlin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.*

class ComposeGradlePlugin : Plugin<Project> {
    companion object {
        fun isEnabled(project: Project) = project.plugins.findPlugin(ComposeGradlePlugin::class.java) != null
    }

    override fun apply(project: Project) {
        // nothing here
    }
}

class ComposeKotlinGradleSubplugin : KotlinGradleSubplugin<AbstractCompile> {
    companion object {
        const val COMPOSE_GROUP_NAME = "org.jetbrains.kotlin"
        const val COMPOSE_ARTIFACT_NAME = "compose-gradle-plugin"
    }

    override fun isApplicable(project: Project, task: AbstractCompile): Boolean =
        ComposeGradlePlugin.isEnabled(project)

    override fun apply(
        project: Project,
        kotlinCompile: AbstractCompile,
        javaCompile: AbstractCompile?,
        variantData: Any?,
        androidProjectHandler: Any?,
        kotlinCompilation: KotlinCompilation<*>?
    ): List<SubpluginOption> {
        if (project.findProperty("kotlin.compose.disable.runtime.sources") == null) {
            addRuntimeSourcesHack(project)
        }

        return emptyList()
    }

    private fun addRuntimeSourcesHack(project: Project) {
        val version = project.getKotlinPluginVersion()

        val d = project.dependencies.create("org.jetbrains.kotlin:compose-js-runtime-sources:$version:sources")
        val c = project.configurations.detachedConfiguration(d)
        val archive = c.resolve().first()
        val dest = project.buildDir.resolve("compose-js-runtime-src")

        dest.resolve("commonMain").mkdirs()
        dest.resolve("jsMain").mkdirs()

        project.copy {
            it.from(project.zipTree(archive))
            it.into(dest)
        }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            (project.extensions.findByName("kotlin") as KotlinProjectExtension).apply {
                sourceSets.findByName("commonMain").also { ss ->
                    ss!!.kotlin.srcDir(dest.resolve("commonMain/kotlin").absolutePath)
                }
                sourceSets.findByName("jsMain").also { ss ->
                    ss!!.kotlin.srcDir(dest.resolve("jsMain/kotlin").absolutePath)
                }
            }
        }
    }

    override fun getPluginArtifact(): SubpluginArtifact =
        SubpluginArtifact(COMPOSE_GROUP_NAME, COMPOSE_ARTIFACT_NAME)

    override fun getCompilerPluginId() = "org.jetbrains.compose.plugin"
}
