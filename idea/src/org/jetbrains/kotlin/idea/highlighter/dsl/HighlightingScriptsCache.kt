/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter.dsl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import kotlinx.colorScheme.ColorSchemeDefinition
import org.jetbrains.kotlin.idea.caches.resolve.LibraryModificationTracker
import org.jetbrains.kotlin.idea.stubindex.KotlinSourceFilterScope
import org.jetbrains.kotlin.idea.stubindex.KotlinSuperClassIndex
import java.io.File
import java.net.URLClassLoader

class HighlightingScriptsCache(private val project: Project) {

    private val cached = CachedValuesManager.getManager(project).createCachedValue(
            {
                CachedValueProvider.Result.create(
                        loadScripts(project),
                        LibraryModificationTracker.getInstance(project),
                        ProjectRootModificationTracker.getInstance(project)
                )
            }, false
    )

    private fun loadScripts(project: Project): ColorSchemeDefinition? {
        try {
            val scriptClasses = KotlinSuperClassIndex.getInstance().get(
                    COLORSCHEME_DEFINITION_BASE_NAME, project,
                    KotlinSourceFilterScope.libraryClassFiles(EverythingGlobalScope(project), project)
            )
            val colorSchemeVirtualFiles = scriptClasses.map { it.containingFile.virtualFile }.filter { it.name.endsWith("_color.class") }

            val jarRootUrls = colorSchemeVirtualFiles.mapNotNull {
                JarFileSystem.getInstance().getLocalByEntry(it)
            }.map { VfsUtil.toUri(it).toURL() }.distinct().toTypedArray()

            val loader = URLClassLoader(
                    jarRootUrls + COLORSCHEME_JAR, ScriptBasedHighlighter::class.java.classLoader
            )

            scriptClasses.firstOrNull()?.let { scriptClass ->
                val fqName = scriptClass.fqName!!.asString()
                val loadedScript = loader.loadClass(fqName)
                return loadedScript.newInstance() as? ColorSchemeDefinition
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    fun getScript(): ColorSchemeDefinition? = cached.value

    companion object {
        private val COLORSCHEME_JAR = File("C:\\Dev\\Misc\\ColorScheme\\out\\artifacts\\ColorScheme_jar\\ColorScheme.jar").toURI().toURL()
        private val COLORSCHEME_DEFINITION_BASE_NAME = "ColorSchemeDefinition"
    }

}