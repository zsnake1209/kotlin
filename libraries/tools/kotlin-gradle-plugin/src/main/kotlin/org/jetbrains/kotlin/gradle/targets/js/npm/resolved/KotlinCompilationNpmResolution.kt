/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.npm.resolved

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.targets.js.npm.*
import org.jetbrains.kotlin.gradle.tasks.registerTask

/**
 * Resolved [NpmProject]
 */
class KotlinCompilationNpmResolution(
    val project: Project,
    val npmProject: NpmProject,
    val internalDependencies: Collection<KotlinCompilationNpmResolution>,
    val internalCompositeDependencies: Collection<GradleNodeModule>,
    val externalGradleDependencies: Collection<GradleNodeModule>,
    val externalNpmDependencies: Collection<NpmDependency>,
    val packageJson: PackageJson
) {
    val publishingPackageJsonTaskHolder: TaskProvider<PublishingPackageJsonTask> =
        project.registerTask(
            PublishingPackageJsonTask.NAME,
            listOf(this)
        ) {}
}