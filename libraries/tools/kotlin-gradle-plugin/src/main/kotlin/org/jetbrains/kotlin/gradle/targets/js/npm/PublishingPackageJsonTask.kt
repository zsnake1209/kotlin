/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.targets.js.npm.resolved.KotlinCompilationNpmResolution
import java.io.File

open class PublishingPackageJsonTask(
    private val compilationResolution: KotlinCompilationNpmResolution
) : DefaultTask() {

    private val npmProject = compilationResolution.npmProject

    @get:Nested
    internal val externalDependencies: Collection<NestedNpmDependency>
        get() = compilationResolution.externalNpmDependencies
            .map {
                NestedNpmDependency(
                    scope = it.scope,
                    name = it.name,
                    version = it.version
                )
            }

    @get:Internal
    private val realExternalDependencies: Collection<NpmDependency>
        get() = compilationResolution.externalNpmDependencies

    @Input
    var skipOnEmptyNpmDependencies: Boolean = false

    @get:OutputFile
    val packageJson: File
        get() = npmProject.publishingPackageJson

    @TaskAction
    fun resolve() {
        packageJsonWithNpmDeps(npmProject, realExternalDependencies).let { packageJson ->
            val packageJsonFile = npmProject.publishingPackageJson
            if (skipOnEmptyNpmDependencies && packageJson.allDependencies.isEmpty()) {
                return
            }

            packageJson.saveTo(packageJsonFile)
        }
    }

    companion object {
        val NAME = "publishingPackageJson"
    }
}

internal data class NestedNpmDependency(
    @Input
    val scope: NpmDependency.Scope,
    @Input
    val name: String,
    @Input
    val version: String
)