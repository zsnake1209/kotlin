/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.gradle.api.artifacts.Dependency
import java.io.File

interface KotlinNpmDependencyHandler {
    fun npm(name: String, version: String = "*"): Dependency

    fun npm(name: String, directory: File): Dependency

    fun npm(directory: File): Dependency

    @Deprecated(
        message = "Use npm(name, version) instead. Name like in package.json"
    )
    fun npm(org: String? = null, packageName: String, version: String = "*"): Dependency
}