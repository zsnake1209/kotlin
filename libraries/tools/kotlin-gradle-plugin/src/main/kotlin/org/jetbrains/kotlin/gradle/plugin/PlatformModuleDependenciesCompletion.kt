/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.ProjectState
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast

class PlatformModuleDependenciesCompletion(
    val platformName: String
) {
    private val platformDisplayName = when (platformName) {
        "jvm" -> "JVM"
        "js" -> "JS"
        else -> platformName
    }

    fun runForPlatformProject(project: Project) {
        if (!isGradleVersionAtLeast(4, 4)) {
            // Gradle does not support modifying dependencies upon their resolution.
            return
        }


        project.afterEvaluate {
            // Workaround: with configure-on-demand, we still need to evaluate all projects. This forces them to evaluate:
            project.rootProject.allprojects {
                project.rootProject.evaluationDependsOn(it.path)
            }

            val mainSourceSet = project.sourceSets.getByName("main")
            val configurationToAddDeps = project.configurations.getByName("implementation")
            val configurationToCheckDeps =
                mainSourceSet.compileClasspath as? Configuration
                        ?: project.configurations.findByName(mainSourceSet.compileClasspathConfigurationName)
                        ?: configurationToAddDeps

            setUpPlatformModuleDependencies(
                project,
                configurationToCheck = configurationToCheckDeps,
                configurationToModify = configurationToAddDeps
            )
        }
    }

    private fun setUpPlatformModuleDependencies(
        project: Project,
        configurationToCheck: Configuration,
        configurationToModify: Configuration
    ) {
        // Run the checks when the dependencies are resolved in the configuration that we'd like to add the dependencies to:
        configurationToModify.withDependencies {
            val expectedByProjectDeps =
                project.configurations.getByName(EXPECTED_BY_CONFIG_NAME).allDependencies
                    .filterIsInstance<ProjectDependency>()

            val transitiveCommonDeps: List<Project> =
                expectedByProjectDeps
                    .flatMap { dep -> getProjectDependencies(dep.dependencyProject, dep.targetConfiguration ?: "default") }
                    .distinct()

            val projectDependencies =
                getProjectDependencies(project, configurationToCheck.name)

            transitiveCommonDeps.forEach { commonModule ->
                val existingPlatformModulesForCommon =
                    commonModule.configurations
                        .findByName(platformImplementationConfigName(platformName))?.dependencies.orEmpty()
                        .filterIsInstance<ProjectDependency>().map { it.dependencyProject }

                val thisProjectDependsOnPlatformImpls = projectDependencies.filter { it in existingPlatformModulesForCommon }

                // When the project depends on a platform module for commonModule, check that the commonModule is not in expectedBy:
                if (thisProjectDependsOnPlatformImpls.isNotEmpty() &&
                    expectedByProjectDeps.any { it.dependencyProject == commonModule }
                ) {
                    project.logger.kotlinWarn(
                        "The platform module ${project.path} is expected by a common module ${commonModule.path} and at the same time " +
                                "depends on its platform implementation (${thisProjectDependsOnPlatformImpls.joinToString { it.path }}). " +
                                "Please make sure there is either an expectedBy dependency OR a platform dependency, not both.\n"
                    )
                }

                when {
                    thisProjectDependsOnPlatformImpls.size == 1 -> Unit

                    thisProjectDependsOnPlatformImpls.size > 1 -> {
                        project.logger.kotlinWarn(
                            "The platform module ${project.path} depends on more than one ${platformDisplayName} " +
                                    "platform module for ${commonModule.path}: \n" +
                                    thisProjectDependsOnPlatformImpls.joinToString("\n") { " * ${it.path}" } +
                                    "\nTo avoid undefined behavior at runtime, please make sure there is a dependency on only one of them.\n")
                    }

                // Otherwise this project does not have a dependency on a platform implementation for commonModule:

                    existingPlatformModulesForCommon.isEmpty() -> {
                        project.logger.kotlinWarn(
                            "No $platformDisplayName platform module found for the common dependency ${commonModule.path} of " +
                                    "platform module ${project.path}. Please make sure there is a ${platformDisplayName} " +
                                    "platform implementation and " +
                                    "the project containing it is evaluated at the moment of the dependency resolution.\n"
                        )
                    }

                    existingPlatformModulesForCommon.size == 1 && expectedByProjectDeps.none { it.dependencyProject == commonModule } -> {
                        val singlePlatformImpl = existingPlatformModulesForCommon.single()
                        project.logger.kotlinInfo(
                            "Found a single ${platformDisplayName} platform implementation ${singlePlatformImpl.path} " +
                                    "for the common dependency ${commonModule.path}. " +
                                    "Adding it to ${configurationToModify}.\n"
                        )
                        project.dependencies.add(configurationToModify.name, singlePlatformImpl)
                    }

                    existingPlatformModulesForCommon.size > 1 -> { // existingPlatformModulesForCommon.size > 1
                        project.logger.kotlinWarn(
                            "A platform module ${project.path} does not explicitly depend on a ${platformDisplayName} platform implementation " +
                                    "of its common dependency ${commonModule.path}. " +
                                    "Please manually choose one of the platform modules and add it as a dependency:\n" +
                                    existingPlatformModulesForCommon.joinToString("\n") { " * ${it.path}" } + "\n"
                        )
                    }
                }
            }
        }
    }

    private fun getProjectDependencies(from: Project, fromConfiguration: String): List<Project> {

        data class ProjectConfiguration(val project: Project, val configuration: String)

        fun collectDependenciesFrom(project: Project, configuration: String) =
            project.configurations.findByName(configuration)?.allDependencies
                .orEmpty()
                .filterIsInstance<ProjectDependency>()
                .map { ProjectConfiguration(it.dependencyProject, it.targetConfiguration ?: "default") }

        val bfsSequence = generateSequence(listOf(ProjectConfiguration(from, fromConfiguration))) { prev ->
            prev.flatMap { (project, conf) -> collectDependenciesFrom(project, conf) }
                .takeIf { it.isNotEmpty() }
        }

        return bfsSequence
            .drop(1)
            .flatten()
            .map { it.project }
            .distinct()
            .toList()
    }
}

private val Project.sourceSets: SourceSetContainer
    get() = convention.getPlugin(JavaPluginConvention::class.java).sourceSets
