/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesManager

class ScriptDependenciesSyntheticLibrary(
        private val project: Project
) : SyntheticLibrary() {
    private val dependenciesManager = project.service<ScriptDependenciesManager>()

    override fun getSourceRoots(): Collection<VirtualFile> = dependenciesManager.getAllLibrarySources()

    override fun getBinaryRoots(): Collection<VirtualFile> = dependenciesManager.getAllScriptsClasspath()

    override fun equals(other: Any?): Boolean {
        other as? ScriptDependenciesSyntheticLibrary ?: return false

        return project == other.project
    }

    override fun hashCode() = project.hashCode()
}

class ScriptDependenciesLibraryProvider: AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        return listOf(ScriptDependenciesSyntheticLibrary(project))
    }
}