/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scripting.gradle.importing

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import org.jetbrains.kotlin.idea.core.script.ScriptDefinitionContributor
import org.jetbrains.kotlin.idea.framework.GRADLE_SYSTEM_ID
import org.jetbrains.kotlin.idea.scripting.gradle.GradleScriptDefinitionsContributor
import org.jetbrains.kotlin.idea.scripting.gradle.roots.GradleBuildRootsManager
import java.lang.Exception

class KotlinDslSyncListener : ExternalSystemTaskNotificationListenerAdapter() {
    private val workingDirs = hashMapOf<ExternalSystemTaskId, String>()

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (!isGradleProjectImport(id)) return
        if (workingDir == null) return

        gradleState.isSyncInProgress = true
        workingDirs[id] = workingDir

        val project = id.findProject() ?: return

        GradleBuildRootsManager.getInstance(project).markImportingInProgress(workingDir)
        project.kotlinGradleDslSync[id] = KotlinDslGradleBuildSync(id)
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        if (!isGradleProjectImport(id)) return

        gradleState.isSyncInProgress = false

        val workingDir = workingDirs.remove(id) ?: return
        val project = id.findProject() ?: return

        @Suppress("DEPRECATION")
        ScriptDefinitionContributor.find<GradleScriptDefinitionsContributor>(project)?.reloadIfNecessary()

        val sync = project.kotlinGradleDslSync.remove(id)
        if (sync != null) {
            // For Gradle 6.0 or higher
            sync.workingDir = workingDir
            saveScriptModels(project, sync)
        }
    }

    override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        if (id.projectSystemId != GRADLE_SYSTEM_ID) return

        val project = id.findProject() ?: return
        val sync = project.kotlinGradleDslSync[id] ?: return

        sync.failed = true
    }

    override fun onCancel(id: ExternalSystemTaskId) {
        if (!isGradleProjectImport(id)) return

        gradleState.isSyncInProgress = false

        val workingDir = workingDirs.remove(id)

        val project = id.findProject() ?: return

        val cancelled = project.kotlinGradleDslSync.remove(id)
        if (cancelled != null && workingDir != null) {
            cancelled.workingDir = workingDir
            GradleBuildRootsManager.getInstance(project).markImportingInProgress(cancelled.workingDir, false)
        }
    }

    private fun isGradleProjectImport(id: ExternalSystemTaskId): Boolean {
        return id.type == ExternalSystemTaskType.RESOLVE_PROJECT || id.projectSystemId == GRADLE_SYSTEM_ID
    }

    companion object {
        internal val gradleState = GradleSyncState()
    }
}

// TODO: state should be stored by gradle build,
// now it is marked as complete after first gradle project was imported
internal class GradleSyncState {
    var isSyncInProgress: Boolean = false
}
