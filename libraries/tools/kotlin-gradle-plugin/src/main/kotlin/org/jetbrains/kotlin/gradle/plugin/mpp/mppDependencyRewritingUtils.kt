/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.Project
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.component.SoftwareComponentInternal
import org.gradle.api.internal.component.UsageContext
import org.gradle.api.plugins.InvalidPluginException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilationToRunnableFiles
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetComponent
import org.jetbrains.kotlin.gradle.utils.*

internal fun Project.rewritePomMppDependenciesToActualTargetModules(
    pomXml: XmlProvider,
    component: KotlinTargetComponent
) {
    if (component !is SoftwareComponentInternal)
        return

    val dependenciesNodeList = pomXml.asNode().get("dependencies") as NodeList
    val dependencyNodes = dependenciesNodeList.filterIsInstance<Node>().flatMap {
        (it.get("dependency") as? NodeList).orEmpty()
    }.filterIsInstance<Node>()

    val dependencyByNode = mutableMapOf<Node, ModuleDependency>()

    // Collect all the dependencies from the nodes:
    val dependencies = dependencyNodes.map { dependencyNode ->
        fun Node.getSingleChildValueOrNull(childName: String): String? =
            ((get(childName) as NodeList?)?.singleOrNull() as Node?)?.text()

        val groupId = dependencyNode.getSingleChildValueOrNull("groupId")
        val artifactId = dependencyNode.getSingleChildValueOrNull("artifactId")
        val version = dependencyNode.getSingleChildValueOrNull("version")
        (project.dependencies.module("$groupId:$artifactId:$version") as ModuleDependency)
            .also { dependencyByNode[dependencyNode] = it }
    }.toSet()

    // Get the dependencies mapping according to the component's UsageContexts:
    val resultDependenciesForEachUsageContext =
        component.usages.mapNotNull { usage ->
            if (usage is KotlinUsageContext)
                associateDependenciesWithActualModuleDependencies(usage, dependencies)
                    // We are only interested in dependencies that are mapped to some other dependencies:
                    .filter { (from, to) -> Triple(from.group, from.name, from.version) != Triple(to.group, to.name, to.version) }
            else null
        }

    // Rewrite the dependency nodes according to the mapping:
    dependencyNodes.forEach { dependencyNode ->
        val moduleDependency = dependencyByNode[dependencyNode]
        val mapDependencyTo = resultDependenciesForEachUsageContext.find { moduleDependency in it }?.get(moduleDependency)

        if (mapDependencyTo != null) {

            fun Node.setChildNodeByName(name: String, value: String?) {
                val childNode: Node? = (get(name) as NodeList?)?.firstOrNull() as Node?
                if (value != null) {
                    (childNode ?: appendNode(name)).setValue(value)
                } else {
                    childNode?.let { remove(it) }
                }
            }

            dependencyNode.setChildNodeByName("groupId", mapDependencyTo.group)
            dependencyNode.setChildNodeByName("artifactId", mapDependencyTo.name)
            dependencyNode.setChildNodeByName("version", mapDependencyTo.version)
        }
    }
}

private fun associateDependenciesWithActualModuleDependencies(
    usageContext: KotlinUsageContext,
    moduleDependencies: Set<ModuleDependency>
): Map<ModuleDependency, ModuleDependency> {
    val compilation = usageContext.compilation
    val project = compilation.target.project

    val targetDependenciesConfiguration = project.configurations.getByName(
        when (compilation) {
            is KotlinJvmAndroidCompilation -> {
                // TODO handle Android configuration names in a general way once we drop AGP < 3.0.0
                val variantName = compilation.name
                when (usageContext.usage.name) {
                    Usage.JAVA_API -> variantName + "CompileClasspath"
                    Usage.JAVA_RUNTIME_JARS -> variantName + "RuntimeClasspath"
                    else -> error("Unexpected Usage for usage context: ${usageContext.usage}")
                }
            }
            else -> when (usageContext.usage.name) {
                Usage.JAVA_API -> compilation.compileDependencyConfigurationName
                Usage.JAVA_RUNTIME_JARS -> (compilation as KotlinCompilationToRunnableFiles).runtimeDependencyConfigurationName
                else -> error("Unexpected Usage for usage context: ${usageContext.usage}")
            }
        }
    )

    val resolvedDependencies by lazy {
        // don't resolve if no project dependencies on MPP projects are found
        targetDependenciesConfiguration.resolvedConfiguration.lenientConfiguration.allModuleDependencies.associateBy {
            Triple(it.moduleGroup, it.moduleName, it.moduleVersion)
        }
    }

    val resolvedModulesByRootModuleCoordinates = targetDependenciesConfiguration
        .allDependencies.withType(ModuleDependency::class.java)
        .associate { dependency ->
            when (dependency) {
                is ProjectDependency -> {
                    val dependencyProject = dependency.dependencyProject

                    val dependencyProjectMultiplatformExtension = run {
                        val dependencyProjectKotlinExtension = dependencyProject.extensions.findByName("kotlin")
                            ?: return@associate dependency to dependency

                        ReflectedInstance.tryWrapAcrossClassLoaders<KotlinMultiplatformExtension>(dependencyProjectKotlinExtension)
                            ?: return@associate dependency to dependency
                    }

                    val resolved = resolvedDependencies[Triple(dependency.group, dependency.name, dependency.version)]
                        ?: return@associate dependency to dependency

                    val resolvedToConfiguration = resolved.configuration

                    reflectedAccessAcrossClassLoaders(
                        {
                            val dependencyTargetComponent: ReflectedInstance<KotlinTargetComponent> = run {
                                dependencyProjectMultiplatformExtension.get(KotlinMultiplatformExtension::targets).iterate()
                                    .forEach { target ->
                                        target.get(KotlinTarget::components).iterate().forEach { component ->
                                            if (findUsageContext(component, resolvedToConfiguration) != null)
                                                return@run component
                                        }
                                    }

                                // Failed to find a matching component:
                                return@associate dependency to dependency
                            }

                            val targetModulePublication =
                                dependencyTargetComponent
                                    .tryCastAcrossClassLoaders<KotlinTargetComponentWithPublication>()
                                    ?.getNullable(KotlinTargetComponentWithPublication::publicationDelegate)
                                    ?.extract()

                            val rootModulePublication =
                                dependencyProjectMultiplatformExtension
                                    .get(KotlinMultiplatformExtension::rootSoftwareComponent)
                                    .getNullable(KotlinSoftwareComponent::publicationDelegate)
                                    ?.extract()

                            // During Gradle POM generation, a project dependency is already written as the root module's coordinates. In the
                            // dependencies mapping, map the root module to the target's module:

                            val rootModule = project.dependencies.module(
                                listOf(
                                    rootModulePublication?.groupId ?: dependency.group,
                                    rootModulePublication?.artifactId ?: dependencyProject.name,
                                    rootModulePublication?.version ?: dependency.version
                                ).joinToString(":")
                            ) as ModuleDependency

                            rootModule to project.dependencies.module(
                                listOf(
                                    targetModulePublication?.groupId ?: dependency.group,
                                    targetModulePublication?.artifactId
                                        ?: dependencyTargetComponent.get(KotlinTargetComponent::defaultArtifactId).extract(),
                                    targetModulePublication?.version ?: dependency.version
                                ).joinToString(":")
                            ) as ModuleDependency
                        },
                        onApiMismatch = { e ->
                            throw InvalidPluginException(
                                "Cannot map a project-to-project dependency for Kotlin publication. The Kotlin plugin version in " +
                                        "$dependencyProject is different from the Kotlin plugin version in $project", e
                            )
                        }
                    )
                }
                else -> {
                    val resolvedDependency = resolvedDependencies[Triple(dependency.group, dependency.name, dependency.version)]
                        ?: return@associate dependency to dependency

                    if (resolvedDependency.moduleArtifacts.isEmpty() && resolvedDependency.children.size == 1) {
                        // This is a dependency on a module that resolved to another module; map the original dependency to the target module
                        val targetModule = resolvedDependency.children.single()
                        dependency to project.dependencies.module(
                            listOf(
                                targetModule.moduleGroup,
                                targetModule.moduleName,
                                targetModule.moduleVersion
                            ).joinToString(":")
                        ) as ModuleDependency

                    } else {
                        dependency to dependency
                    }
                }
            }
        }.mapKeys { (key, _) -> Triple(key.group, key.name, key.version) }

    return moduleDependencies.associate { dependency ->
        val key = Triple(dependency.group, dependency.name, dependency.version)
        val value = resolvedModulesByRootModuleCoordinates[key] ?: dependency
        dependency to value
    }
}

private fun findUsageContext(
    reflectedComponent: ReflectedInstance<KotlinTargetComponent>,
    configurationName: String
): ReflectedInstance<out UsageContext>? = reflectedAccessAcrossClassLoaders(
    {
        val usageContexts: Iterable<ReflectedInstance<out UsageContext>> = when {
            isInstanceAcrossClassLoaders<KotlinVariantWithMetadataDependency>(reflectedComponent.instance) ->
                reflectedComponent.tryCastAcrossClassLoaders<KotlinVariantWithMetadataDependency>()!!
                    .get(KotlinVariantWithMetadataDependency::originalUsages)
                    .iterate()
            reflectedComponent.instance is SoftwareComponentInternal -> reflectedComponent.instance.usages.map { ReflectedInstance(it, UsageContext::class) }
            else -> emptyList()
        }

        return usageContexts.find { usageContext ->
            val kotlinUsageContext = usageContext.tryCastAcrossClassLoaders<KotlinUsageContext>()
                ?: return@find false

            val compilation = kotlinUsageContext.get(KotlinUsageContext::compilation)

            if (configurationName in compilation.get(KotlinCompilation<*>::relatedConfigurationNames).extract())
                return@find true

            val target = compilation.get(KotlinCompilation<*>::target)
            val targetConfigurationNames = listOf(
                target.get(KotlinTarget::apiElementsConfigurationName).extract(),
                target.get(KotlinTarget::runtimeElementsConfigurationName).extract(),
                target.get(KotlinTarget::defaultConfigurationName).extract()
            )
            return@find configurationName in targetConfigurationNames
        }
    },
    onApiMismatch = { e: ReflectedAccessException -> throw e }
)