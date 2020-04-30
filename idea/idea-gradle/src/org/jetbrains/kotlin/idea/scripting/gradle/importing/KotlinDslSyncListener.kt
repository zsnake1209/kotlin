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

class KotlinDslSyncListener : ExternalSystemTaskNotificationListenerAdapter() {
    private val workingDirs = hashMapOf<ExternalSystemTaskId, String>()

    override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        if (id.projectSystemId != GRADLE_SYSTEM_ID) return
        if (workingDir == null) return

        workingDirs[id] = workingDir

        val project = id.findProject() ?: return

        gradleState.isSyncInProgress = true

        project.kotlinGradleDslSync[id] = KotlinDslGradleBuildSync(id)
    }

    override fun onEnd(id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        if (id.projectSystemId != GRADLE_SYSTEM_ID) return
        val workingDir = workingDirs.remove(id) ?: return

        val project = id.findProject() ?: return

        gradleState.isSyncInProgress = false

        ScriptDefinitionContributor.find<GradleScriptDefinitionsContributor>(project)?.reloadIfNecessary()

        val sync = project.kotlinGradleDslSync.remove(id) ?: return
        sync.workingDir = workingDir

        saveScriptModels(project, sync)
    }

    override fun onCancel(id: ExternalSystemTaskId) {
        if (id.type != ExternalSystemTaskType.RESOLVE_PROJECT) return
        if (id.projectSystemId != GRADLE_SYSTEM_ID) return

        val project = id.findProject() ?: return

        gradleState.isSyncInProgress = true

        project.kotlinGradleDslSync.remove(id)
        workingDirs.remove(id)
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