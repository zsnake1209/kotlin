/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.npm.resolver.KotlinCompilationNpmResolver
import java.io.File

open class PublishingPackageJsonTask(
    private val nodeJs: NodeJsRootExtension,
    private val compilation: KotlinJsCompilation
) : DefaultTask() {
    private val compilationResolver
        get() = nodeJs.npmResolutionManager.requireConfiguringState()[project][compilation]

    private val producer: KotlinCompilationNpmResolver.PackageJsonProducer
        get() = compilationResolver.packageJsonProducer

    @get:Input
    val externalDependencies: Collection<String>
        get() = producer.inputs.externalDependencies

    @get:OutputFile
    val packageJson: File
        get() = compilationResolver.npmProject.packageJsonFile

    @TaskAction
    fun resolve() {
        producer.createPublishingPackageJson()
    }

    companion object {
        val NAME = "publishingPackageJson"
    }
}