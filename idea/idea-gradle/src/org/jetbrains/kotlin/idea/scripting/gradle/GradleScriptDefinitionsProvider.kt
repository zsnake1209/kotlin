/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scripting.gradle

import com.intellij.execution.configurations.CommandLineTokenizer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import org.jetbrains.kotlin.idea.KotlinIdeaGradleBundle
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionSourceAsContributor
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionsManager
import org.jetbrains.kotlin.idea.core.script.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.scripting.gradle.importing.KotlinDslSyncListener
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinitionAdapterFromNewAPIBase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.getEnvironment
import org.jetbrains.kotlin.scripting.resolve.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass
import kotlin.script.dependencies.Environment
import kotlin.script.dependencies.ScriptContents
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.dependencies.DependenciesResolver
import kotlin.script.experimental.dependencies.DependenciesResolver.ResolveResult
import kotlin.script.experimental.dependencies.ScriptReport
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.location.ScriptExpectedLocation
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class GradleScriptDefinitionsContributor(private val project: Project) : ScriptDefinitionSourceAsContributor {

    override val id: String = "Gradle Kotlin DSL"
    private val failedToLoad = AtomicBoolean(false)

    init {
        subscribeToGradleSettingChanges()
    }

    companion object {
        private val kotlinDslDependencySelector = Regex("^gradle-(?:kotlin-dsl|core).*\\.jar\$")

        fun getGradleScriptDefinitionsClassPath(project: Project): List<File> {
            return try {
                getFullGradleScriptDefinitionsClassPath(project).first
            } catch (e: Throwable) {
                emptyList()
            }
        }

        private fun getFullGradleScriptDefinitionsClassPath(project: Project): Pair<List<File>, List<File>> {
            val allLinkedProjectsSettings = getGradleProjectSettings(project)

            // todo: choose gradle project correctly by gradle root dir
            val currentProjectSettings = allLinkedProjectsSettings.firstOrNull()
                ?: error(KotlinIdeaGradleBundle.message("error.text.project.isn.t.linked.with.gradle", project.name))

            val gradleExeSettings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
                project,
                currentProjectSettings.externalProjectPath,
                GradleConstants.SYSTEM_ID
            )

            val gradleHome = gradleExeSettings.gradleHome
                ?: error(KotlinIdeaGradleBundle.message("error.text.unable.to.get.gradle.home.directory"))

            val gradleLibDir = File(gradleHome, "lib")
                .let {
                    it.takeIf { it.exists() && it.isDirectory }
                        ?: error(KotlinIdeaGradleBundle.message("error.text.invalid.gradle.libraries.directory", it))
                }

            val templateClasspath = gradleLibDir
                /* an inference problem without explicit 'it', TODO: remove when fixed */
                .listFiles { it -> kotlinDslDependencySelector.matches(it.name) }
                ?.takeIf { it.isNotEmpty() }
                ?.asList()
                ?: error(KotlinIdeaGradleBundle.message("error.text.missing.jars.in.gradle.directory"))

            scriptingDebugLog { "gradle script templates classpath $templateClasspath" }

            val additionalClassPath = kotlinStdlibAndCompiler(gradleLibDir)

            scriptingDebugLog { "gradle script templates additional classpath $templateClasspath" }

            return templateClasspath to additionalClassPath
        }

        // TODO: check this against kotlin-dsl branch that uses daemon
        private fun kotlinStdlibAndCompiler(gradleLibDir: File): List<File> {
            // additionally need compiler jar to load gradle resolver
            return gradleLibDir.listFiles { file ->
                file?.name?.startsWith("kotlin-compiler-embeddable") == true || file?.name?.startsWith("kotlin-stdlib") == true
            }?.firstOrNull()?.let(::listOf).orEmpty()
        }
    }

    private fun subscribeToGradleSettingChanges() {
        val listener = object : GradleSettingsListenerAdapter() {
            override fun onGradleVmOptionsChange(oldOptions: String?, newOptions: String?) {
                reload()
            }

            override fun onGradleHomeChange(oldPath: String?, newPath: String?, linkedProjectPath: String) {
                reload()
            }

            override fun onServiceDirectoryPathChange(oldPath: String?, newPath: String?) {
                reload()
            }

            override fun onGradleDistributionTypeChange(currentValue: DistributionType?, linkedProjectPath: String) {
                reload()
            }
        }
        project.messageBus.connect().subscribe(GradleSettingsListener.TOPIC, listener)
    }

    // NOTE: control flow here depends on suppressing exceptions from loadGradleTemplates calls
    // TODO: possibly combine exceptions from every loadGradleTemplates call, be mindful of KT-19276
    override val definitions: Sequence<ScriptDefinition>
        get() {
            failedToLoad.set(false)

            try {
                val allLinkedProjectsSettings = getGradleProjectSettings(project)

                // todo: choose gradle project correctly by gradle root dir
                val currentProjectSettings = allLinkedProjectsSettings.firstOrNull()
                    ?: error(KotlinIdeaGradleBundle.message("error.text.project.isn.t.linked.with.gradle", project.name))

                val projectPath = currentProjectSettings.externalProjectPath

                val (templateClasspath, additionalClassPath) = getFullGradleScriptDefinitionsClassPath(project)

                val kotlinDslTemplates = ArrayList<ScriptDefinition>()

                loadGradleTemplates(
                    projectPath,
                    "org.gradle.kotlin.dsl.KotlinInitScript",
                    templateClasspath, additionalClassPath
                ).let { kotlinDslTemplates.addAll(it) }

                loadGradleTemplates(
                    projectPath,
                    "org.gradle.kotlin.dsl.KotlinSettingsScript",
                    templateClasspath, additionalClassPath
                ).let { kotlinDslTemplates.addAll(it) }

                // KotlinBuildScript should be last because it has wide scriptFilePattern
                loadGradleTemplates(
                    projectPath,
                    "org.gradle.kotlin.dsl.KotlinBuildScript",
                    templateClasspath, additionalClassPath
                ).let { kotlinDslTemplates.addAll(it) }


                if (kotlinDslTemplates.isNotEmpty()) {
                    return kotlinDslTemplates.distinct().asSequence()
                }
            } catch (t: Throwable) {
                scriptingDebugLog { "error loading gradle script templates ${t.message}" }
                // TODO: review exception handling
                failedToLoad.set(true)
                if (t is IllegalStateException) {
                    Logger.getInstance(GradleScriptDefinitionsContributor::class.java)
                        .info("[kts] error loading gradle script templates: ${t.message}")
                }
                return sequenceOf(ErrorGradleScriptDefinition(t.message))
            }

            return sequenceOf(ErrorGradleScriptDefinition())
        }

    private fun loadGradleTemplates(
        projectPath: String,
        templateClass: String,
        templateClasspath: List<File>,
        additionalClassPath: List<File>
    ): List<ScriptDefinition> {
        val gradleExeSettings = ExternalSystemApiUtil.getExecutionSettings<GradleExecutionSettings>(
            project,
            projectPath,
            GradleConstants.SYSTEM_ID
        )
        val hostConfiguration = createHostConfiguration(projectPath, gradleExeSettings)
        return loadDefinitionsFromTemplates(
            listOf(templateClass),
            templateClasspath,
            hostConfiguration,
            additionalClassPath
        ).map {
            it.asLegacyOrNull<KotlinScriptDefinitionFromAnnotatedTemplate>()?.let { legacyDef ->
                @Suppress("DEPRECATION")
                if (legacyDef.scriptExpectedLocations.contains(ScriptExpectedLocation.Project)) null
                else {
                    // Expand scope for old gradle script definition
                    ScriptDefinition.FromLegacy(
                        it.hostConfiguration,
                        GradleKotlinScriptDefinitionFromAnnotatedTemplate(
                            legacyDef
                        )
                    )
                }
            } ?: it
        }
    }

    private fun createHostConfiguration(
        projectPath: String,
        gradleExeSettings: GradleExecutionSettings
    ): ScriptingHostConfiguration {
        val gradleJvmOptions = gradleExeSettings.daemonVmOptions?.let { vmOptions ->
            CommandLineTokenizer(vmOptions).toList()
                .mapNotNull { it?.let { it as? String } }
                .filterNot(String::isBlank)
                .distinct()
        } ?: emptyList()

        val environment = mapOf(
            "gradleHome" to gradleExeSettings.gradleHome?.let(::File),
            "gradleJavaHome" to gradleExeSettings.javaHome,

            "projectRoot" to projectPath.let(::File),

            "gradleOptions" to emptyList<String>(), // There is no option in UI to set project wide gradleOptions
            "gradleJvmOptions" to gradleJvmOptions,
            "gradleEnvironmentVariables" to if (gradleExeSettings.isPassParentEnvs) EnvironmentUtil.getEnvironmentMap() else emptyMap()
        )
        return ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
            getEnvironment { environment }
        }
    }

    fun reloadIfNecessary() {
        if (failedToLoad.compareAndSet(true, false)) {
            reload()
        }
    }

    private fun reload() {
        ScriptDefinitionsManager.getInstance(project).reloadDefinitionsBy(this)
    }

    // TODO: refactor - minimize
    private class ErrorGradleScriptDefinition(message: String? = null) :
        ScriptDefinition.FromLegacy(
            ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration),
            LegacyDefinition(
                message
            )
        ) {

        private class LegacyDefinition(message: String?) : KotlinScriptDefinitionAdapterFromNewAPIBase() {
            companion object {
                private const val KOTLIN_DSL_SCRIPT_EXTENSION = "gradle.kts"
            }

            override val name: String get() = KotlinIdeaGradleBundle.message("text.default.kotlin.gradle.script")
            override val fileExtension: String = KOTLIN_DSL_SCRIPT_EXTENSION

            override val scriptCompilationConfiguration: ScriptCompilationConfiguration = ScriptCompilationConfiguration.Default
            override val hostConfiguration: ScriptingHostConfiguration = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration)
            override val baseClass: KClass<*> = ScriptTemplateWithArgs::class

            override val dependencyResolver: DependenciesResolver =
                ErrorScriptDependenciesResolver(
                    message
                )

            override fun toString(): String = "ErrorGradleScriptDefinition"
        }

        override fun equals(other: Any?): Boolean = other is ErrorGradleScriptDefinition
        override fun hashCode(): Int = name.hashCode()
    }

    private class ErrorScriptDependenciesResolver(private val message: String? = null) : DependenciesResolver {
        override fun resolve(scriptContents: ScriptContents, environment: Environment): ResolveResult {
            val failureMessage = if (KotlinDslSyncListener.gradleState.isSyncInProgress) {
                KotlinIdeaGradleBundle.message("error.text.highlighting.is.impossible.during.gradle.import")
            } else {
                message ?: KotlinIdeaGradleBundle.message(
                    "error.text.failed.to.load.script.definitions.by",
                    GradleScriptDefinitionsContributor::class.java.name
                )
            }
            return ResolveResult.Failure(ScriptReport(failureMessage, ScriptReport.Severity.FATAL))
        }
    }
}

class GradleKotlinScriptDefinitionFromAnnotatedTemplate(
    base: KotlinScriptDefinitionFromAnnotatedTemplate
) : KotlinScriptDefinitionFromAnnotatedTemplate(base.template, base.environment, base.templateClasspath) {
    @Suppress("DEPRECATION")
    override val scriptExpectedLocations: List<ScriptExpectedLocation>
        get() = listOf(ScriptExpectedLocation.Project)
}