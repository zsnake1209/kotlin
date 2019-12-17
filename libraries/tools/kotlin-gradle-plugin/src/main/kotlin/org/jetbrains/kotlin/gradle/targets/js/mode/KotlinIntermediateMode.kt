/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.mode

import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import javax.inject.Inject

open class KotlinIntermediateMode
@Inject constructor(target: KotlinJsTarget) : KotlinMode(target) {
    override val testsOnly: Boolean = true

    override fun configure() {
        target.compilations
            .all {
                it.kotlinOptions.freeCompilerArgs += if (it.name == KotlinCompilation.TEST_COMPILATION_NAME) {
                    listOf(
                        "-Xir-produce-js", "-Xir-only"
                    )
                } else {
                    listOf(
                        "-Xir-produce-klib-dir", "-Xir-only"
                    )
                }
            }
    }
}