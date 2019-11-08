/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.compilerRunner.KonanCompilerRunner
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.tasks.addArg
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.konan.target.KonanTarget
import java.io.File

// Rename cached -> Precompiled?
data class CachedKlib(val klib: File, val cache: File)

fun String.toPrecompiledName(target: KonanTarget): String {
    val outputKind = CompilerOutputKind.DYNAMIC_CACHE
    val suffix = outputKind.suffix(target)
    val prefix = outputKind.prefix(target)
    return "$prefix$this$suffix"
}

// TODO: May be inherit AbstractKotlinCompile?
open class DynamicCacheTask : DefaultTask() {

    @Internal
    lateinit var compilation: KotlinNativeCompilation

    @get:Input
    val target: String
        get() = compilation.konanTarget.name

    @get:InputFiles
    val librariesToPrecompile: Configuration
        get() = project.configurations.getByName(compilation.compileDependencyConfigurationName)

    // TODO: Better naming.
    @get:OutputDirectory
    val outputDirectory: File = project.buildDir.resolve("precompiled-klibs/bin/${this.name}")

    // We store mapping klib -> cache in a file to correctly work with incremental building.
    // TODO: Can we do this better?
    // TODO: Better naming.
    @get:OutputFile
    val mappingFile: File = project.buildDir.resolve("precompiled-klibs/mapping/${this.name}")

    private fun buildArgs(
        inputKlib: File,
        outputCache: File,
        precompiledDependencies: List<CachedKlib>
    ): List<String> = mutableListOf<String>().apply {
        addArg("-p", CompilerOutputKind.DYNAMIC_CACHE.name.toLowerCase())
        addArg("-target", target)
        addArg("-o", outputCache.absolutePath)

        add("-Xmake-cache=${inputKlib.absolutePath}")

        // TODO: Better caching for stdlib and other std libraries.
        val stdlibCache = "stdlib_cache".toPrecompiledName(compilation.konanTarget)
        add("-Xcached-library=${project.konanHome}/klib/common/stdlib,${project.konanHome}/$stdlibCache")

        precompiledDependencies.forEach { (klib, cache) ->
            addArg("-l", klib.absolutePath)
            add("-Xcached-library=${klib.absolutePath},${cache.absolutePath}")
        }

        // TODO: Additional flags.
    }

    private fun precompile(dependency: ResolvedDependency): Set<CachedKlib> {
        val precompiledTransitives = dependency.children.flatMap {
            precompile(it)
        }
        val precompiledArtifacts = mutableSetOf<CachedKlib>()
        dependency.moduleArtifacts.filter {
            it.file.extension == "klib" // We can get other artifacts, e.g. sources jars so filters klibs only for now.
        }.forEach {
            // TODO: Are these names unique?
            // TODO: Filter only downloaded libs
            val inputFile = it.file
            val outputFile = outputDirectory.resolve(inputFile.nameWithoutExtension.toPrecompiledName(compilation.konanTarget))
            val args = buildArgs(inputFile, outputFile, precompiledTransitives)

            KonanCompilerRunner(project).run(args)
            precompiledArtifacts.add(CachedKlib(inputFile, outputFile))
        }
        return precompiledArtifacts + precompiledTransitives
    }

    // TODO: Make incremental
    @TaskAction
    fun precompile() {
        val precompiledKlibs =
            librariesToPrecompile.resolvedConfiguration.firstLevelModuleDependencies.flatMap {
                precompile(it)
            }
        mappingFile.writeText(
            precompiledKlibs.joinToString(separator = "\n") { "${it.klib};${it.cache}" }
        )
    }
}