/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.internal.state

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.logging.kotlinDebug
import org.jetbrains.kotlin.gradle.tasks.KOTLIN_COMPILER_EMBEDDABLE
import org.jetbrains.kotlin.gradle.tasks.KOTLIN_MODULE_GROUP
import org.jetbrains.kotlin.gradle.utils.loadPropertyFromResources
import java.io.File
import java.net.URLClassLoader

/**
 * DO NOT save references to build specific objects (this may cause leaks!).
 * The lifetime is bound to the lifetime of plugin classes.
 */
internal class KGPContext private constructor(
    private val compilerClassloader: ClassLoader
) {
    val isJsLib: ((File) -> Boolean)
    val isJsIrLib: ((File) -> Boolean)
    val discoverScriptExtensions: (File) -> List<String>
    val jdkClassesRoots: List<File>

    init {
        val libraryUtils = compilerClassloader.loadClass("org.jetbrains.kotlin.utils.LibraryUtils")
        isJsLib = libraryUtils.static1("isKotlinJavascriptLibrary")
        isJsIrLib = libraryUtils.static1("isKotlinJavascriptIrLibrary")

        discoverScriptExtensions = compilerClassloader
            .loadClass("org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource")
            .static1("discoverScriptExtensionsFromGradle")

        val getJdkClassesRoots: () -> List<File> = compilerClassloader
            .loadClass("org.jetbrains.kotlin.utils.PathUtil")
            .static0("getJdkClassesRootsFromCurrentJre")
        jdkClassesRoots = getJdkClassesRoots()
    }

    companion object {
        @Volatile
        private var _instance: KGPContext? = null

        @Volatile
        private var initializingProject: Project? = null

        @Synchronized
        fun initializeLazily(project: Project) {
            if (initializingProject == null && _instance == null) {
                initializingProject = project
                project.gradle.buildFinished { initializingProject = null }
            }
        }

        val instance: KGPContext
            @Synchronized
            get() {
                if (_instance != null) return _instance!!

                val project = initializingProject ?: error("Kotlin Gradle plugin context was not initialized!")
                val pluginVersion = loadPropertyFromResources("project.properties", "project.version")
                val compilerClassloader = createCompilerClassloader(project, pluginVersion)
                return KGPContext(compilerClassloader).also {
                    _instance = it
                    initializingProject = null
                }
            }
    }
}

private fun createCompilerClassloader(project: Project, pluginVersion: String): ClassLoader {
    val compilerArtifact = "$KOTLIN_MODULE_GROUP:$KOTLIN_COMPILER_EMBEDDABLE:$pluginVersion"
    val log = project.logger
    log.kotlinDebug { "Trying to resolve '$compilerArtifact' artifact" }

    val projects = generateSequence(project) { project.parent }.toList()

    for (p in projects) {
        val buildscript = p.buildscript
        val dependency = buildscript.dependencies.create("$KOTLIN_MODULE_GROUP:$KOTLIN_COMPILER_EMBEDDABLE:$pluginVersion")
        val configuration = buildscript.configurations.detachedConfiguration(dependency)

        try {
            val files = configuration.resolve()
            val urls = files.map { it.toURI().toURL() }.toTypedArray()
            val systemClassloader = File::class.java.classLoader
            return URLClassLoader(urls, systemClassloader)
        } catch (e: Throwable) {
            log.kotlinDebug { "Could not resolve '$compilerArtifact' in '${project.path}' buildscript: $e" }
        }
    }

    error(
        "Could not resolve '$compilerArtifact' using buildscript dependencies of the following projects: " +
                "\n${projects.joinToString("\n")}"
    )
}

private inline fun <reified R> Class<*>.static0(methodName: String): () -> R {
    val m = this.getMethod(methodName, this)
    return { -> m.invoke(this) as R }
}

private inline fun <reified T, reified R> Class<*>.static1(methodName: String): (T) -> R {
    val m = this.getMethod(methodName, this, T::class.java)
    return { arg: T -> m.invoke(this, arg) as R }
}