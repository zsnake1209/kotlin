/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin

class NpmResolverPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val project = target
        val nodeJs = NodeJsRootPlugin.apply(project)
        nodeJs.npmResolutionManager.requireConfiguringState().addProject(project)

        project.processProjectDependencies(nodeJs)
    }

    companion object {
        fun apply(project: Project) {
            project.plugins.apply(NpmResolverPlugin::class.java)
        }
    }
}

private fun Project.processProjectDependencies(nodeJs: NodeJsRootExtension) {
    configurations.all { configuration ->
        configuration.allDependencies.all { dependency ->
            if (dependency is ProjectDependency) {
                with(dependency.dependencyProject) {
                    plugins.withType(NpmResolverPlugin::class.java) {
                        val dependentNodeJs = NodeJsRootPlugin.apply(this)
                        val dependentResolver = dependentNodeJs.npmResolutionManager.requireConfiguringState()[this]
                        nodeJs.npmResolutionManager.requireConfiguringState().addProject(this, dependentResolver)
                    }
                    processProjectDependencies(nodeJs)
                }
            }
        }
    }
}