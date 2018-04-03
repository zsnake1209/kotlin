/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.gradle.plugin

import com.android.build.gradle.BaseExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.SourceSet
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonProjectExtension
import org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile

abstract class KotlinPlatformPluginBase(val platformName: String) : Plugin<Project> {
    companion object {
        @JvmStatic
        protected inline fun <reified T : Plugin<*>> Project.applyPlugin() {
            pluginManager.apply(T::class.java)
        }
    }
}

open class KotlinPlatformCommonPlugin : KotlinPlatformPluginBase("common") {
    override fun apply(project: Project) {
        project.applyPlugin<KotlinCommonPluginWrapper>()
    }
}

const val EXPECTED_BY_CONFIG_NAME = "expectedBy"

const val IMPLEMENT_CONFIG_NAME = "implement"
const val IMPLEMENT_DEPRECATION_WARNING = "The '$IMPLEMENT_CONFIG_NAME' configuration is deprecated and will be removed. " +
                                          "Use '$EXPECTED_BY_CONFIG_NAME' instead."

open class KotlinPlatformImplementationPluginBase(platformName: String) : KotlinPlatformPluginBase(platformName) {
    private val platformKotlinTasksBySourceSetName = hashMapOf<String, AbstractKotlinCompile<*>>()

    override fun apply(project: Project) {
        project.tasks.filterIsInstance<AbstractKotlinCompile<*>>().associateByTo(platformKotlinTasksBySourceSetName) { it.sourceSetName }

        val implementConfig = project.configurations.create(IMPLEMENT_CONFIG_NAME)
        val expectedByConfig = project.configurations.create(EXPECTED_BY_CONFIG_NAME)

        implementConfig.dependencies.whenObjectAdded {
            if (!implementConfigurationIsUsed) {
                implementConfigurationIsUsed = true
                project.logger.kotlinWarn(IMPLEMENT_DEPRECATION_WARNING)
            }
        }

        listOf(implementConfig, expectedByConfig).forEach { config ->
            config.isTransitive = false

            config.dependencies.whenObjectAdded { dep ->
                if (dep is ProjectDependency) {
                    //fixme remove the dependency maybe?
//                    config.dependencies.remove(dep)
                    val commonProject = dep.dependencyProject
                    commonProject.whenEvaluated {
                        commonProject.extensions.findByType(KotlinCommonProjectExtension::class.java)
                            ?.addPlatformModule(project.path)
                                ?: throw GradleException(
                                    "Platform module has a ${config.name} dependency to $commonProject that does not have " +
                                            "the 'kotlin-platform-common' plugin applied.")
                    }
                }
                else {
                    throw GradleException("$project '${config.name}' dependency is not a project: $dep")
                }
            }
        }

        val incrementalMultiplatform = PropertiesProvider(project).incrementalMultiplatform ?: true
        project.afterEvaluate {
            project.tasks.withType(AbstractKotlinCompile::class.java).all {
                if (it.incremental && !incrementalMultiplatform) {
                    project.logger.debug("IC is turned off for task '${it.path}' because multiplatform IC is not enabled")
                }
                it.incremental = it.incremental && incrementalMultiplatform
            }
        }
    }

    private var implementConfigurationIsUsed = false

    open fun addCommonSourceSetToPlatformSourceSet(commonSourceSet: SourceSet, platformProject: Project) {
        val platformTask = platformKotlinTasksBySourceSetName[commonSourceSet.name]
        commonSourceSet.kotlin!!.srcDirs.forEach { platformTask?.source(it) }
    }

    protected val SourceSet.kotlin: SourceDirectorySet?
        get() {
            // Access through reflection, because another project's KotlinSourceSet might be loaded
            // by a different class loader:
            val convention = (getConvention("kotlin") ?: getConvention("kotlin2js")) ?: return null
            val kotlinSourceSetIface = convention.javaClass.interfaces.find { it.name == KotlinSourceSet::class.qualifiedName }
            val getKotlin = kotlinSourceSetIface?.methods?.find { it.name == "getKotlin" } ?: return null
            return getKotlin(convention) as? SourceDirectorySet
        }
}

open class KotlinPlatformAndroidPlugin : KotlinPlatformImplementationPluginBase("android") {
    override fun apply(project: Project) {
        project.applyPlugin<KotlinAndroidPluginWrapper>()
        super.apply(project)
    }

    override fun addCommonSourceSetToPlatformSourceSet(commonSourceSet: SourceSet, platformProject: Project) {
        val androidExtension = platformProject.extensions.getByName("android") as BaseExtension
        val androidSourceSet = androidExtension.sourceSets.findByName(commonSourceSet.name) ?: return
        val kotlinSourceSet = androidSourceSet.getConvention(KOTLIN_DSL_NAME) as? KotlinSourceSet ?: return
        kotlinSourceSet.kotlin.source(commonSourceSet.kotlin!!)
    }
}

open class KotlinPlatformJvmPlugin : KotlinPlatformImplementationPluginBase("jvm") {
    override fun apply(project: Project) {
        project.applyPlugin<KotlinPluginWrapper>()
        super.apply(project)
    }
}

open class KotlinPlatformJsPlugin : KotlinPlatformImplementationPluginBase("js") {
    override fun apply(project: Project) {
        project.applyPlugin<Kotlin2JsPluginWrapper>()
        super.apply(project)
    }
}