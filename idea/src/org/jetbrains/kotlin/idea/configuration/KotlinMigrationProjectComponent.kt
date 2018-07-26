/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.framework.GRADLE_SYSTEM_ID
import org.jetbrains.kotlin.idea.framework.MAVEN_SYSTEM_ID
import org.jetbrains.kotlin.idea.project.languageVersionSettings
import org.jetbrains.kotlin.idea.util.application.runReadAction

class KotlinMigrationProjectComponent(val project: Project) {
    private var old: MigrationState? = null
    private var new: MigrationState? = null

    init {
        val connection = project.messageBus.connect()
        connection.subscribe(ProjectDataImportListener.TOPIC, ProjectDataImportListener {
            KotlinMigrationProjectComponent.getInstance(project).onImportFinished()
        })
    }

    @Synchronized
    fun onImportAboutToStart() {
        old = MigrationState.build(project)
    }

    @Synchronized
    fun onImportFinished() {
        new = MigrationState.build(project)

        checkForUpdates(project, old, new)
    }

    companion object {
        fun getInstance(project: Project): KotlinMigrationProjectComponent = project.getComponent(KotlinMigrationProjectComponent::class.java)!!

        private fun checkForUpdates(project: Project, old: MigrationState?, new: MigrationState?) {
            if (old == null || new == null) {
                return
            }

            val oldLibrary = old.libraries.firstOrNull()
            val newLibrary = new.libraries.firstOrNull()

            val oldApiVersion = old.apiVersions.firstOrNull()
            val newApiVersion = new.apiVersions.firstOrNull { newApi -> newApi !in old.apiVersions }

            val oldLanguageVersion = old.languageVersions.firstOrNull()
            val newLanguageVersion = new.languageVersions.firstOrNull { languageVersion -> languageVersion !in old.languageVersions }

            if (oldLibrary == null || newLibrary == null) {
                return
            }

            if (oldLibrary.version != newLibrary.version ||
                (oldApiVersion != newApiVersion && newApiVersion != null) ||
                (oldLanguageVersion != newLanguageVersion && newLanguageVersion != null)
            ) {
                notifyUpdate(
                    project,
                    MigrationInfo(
                        oldLibrary.version, newLibrary.version,
                        oldApiVersion, newApiVersion,
                        oldLanguageVersion, newLanguageVersion
                    )
                )
            }
        }
    }
}

private class MigrationState(
    var libraries: Collection<LibraryInfo>,
    var apiVersions: Collection<ApiVersion>,
    var languageVersions: Collection<LanguageVersion>
) {
    companion object {
        fun build(project: Project): MigrationState {
            val libraries = collectKotlinLibraries(project)
            val (apiVersions, languageVersions) = org.jetbrains.kotlin.idea.configuration.collectCompilerSettings(project)
            return MigrationState(libraries, apiVersions, languageVersions)
        }
    }
}

internal class MigrationInfo(
    val oldStdlibVersion: String,
    val newVersion: String,
    val oldApiVersion: ApiVersion?,
    val newApiVersion: ApiVersion?,
    val oldLanguageVersion: LanguageVersion?,
    val newLanguageVersion: LanguageVersion?
)

private fun notifyUpdate(project: Project, migrationInfo: MigrationInfo) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
        return
    }

    ApplicationManager.getApplication().invokeLater {
        // Notify
    }
}

data class LibraryInfo(val groupId: String, val artifactId: String, val version: String)

private const val KOTLIN_GROUP_ID = "org.jetbrains.kotlin"

private fun collectKotlinLibraries(project: Project): HashSet<LibraryInfo> {
    val oldKotlinLibraries = HashSet<LibraryInfo>()
    runReadAction {
        ProjectRootManager.getInstance(project).orderEntries().forEachLibrary { library ->
            if (ExternalSystemApiUtil.isExternalSystemLibrary(library, GRADLE_SYSTEM_ID) ||
                ExternalSystemApiUtil.isExternalSystemLibrary(library, MAVEN_SYSTEM_ID)
            ) {
                if (library.name?.contains(" $KOTLIN_GROUP_ID:kotlin-stdlib") == true) {
                    val libName = library.name ?: return@forEachLibrary true
                    val version = libName.substringAfterLastNullable(":") ?: return@forEachLibrary true

                    val nameWithoutVersion = libName.substringBeforeLastNullable(":") ?: return@forEachLibrary true
                    val artifactId = nameWithoutVersion.substringAfterLastNullable(":") ?: return@forEachLibrary true

                    oldKotlinLibraries.add(LibraryInfo(KOTLIN_GROUP_ID, artifactId, version))
                }
            }

            true
        }
    }

    return oldKotlinLibraries
}

private fun collectCompilerSettings(project: Project): Pair<Set<ApiVersion>, Set<LanguageVersion>> {
    return runReadAction {
        val apiVersions = HashSet<ApiVersion>()
        val languageVersion = HashSet<LanguageVersion>()
        val modules = ModuleManager.getInstance(project).modules
        for (module in modules) {
            val languageVersionSettings = module.languageVersionSettings
            apiVersions.add(languageVersionSettings.apiVersion)
            languageVersion.add(languageVersionSettings.languageVersion)
        }

        apiVersions to languageVersion
    }
}

fun String.substringBeforeLastNullable(delimiter: String, missingDelimiterValue: String? = null): String? {
    val index = lastIndexOf(delimiter)
    return if (index == -1) missingDelimiterValue else substring(0, index)
}

fun String.substringAfterLastNullable(delimiter: String, missingDelimiterValue: String? = null): String? {
    val index = lastIndexOf(delimiter)
    return if (index == -1) missingDelimiterValue else substring(index + 1, length)
}