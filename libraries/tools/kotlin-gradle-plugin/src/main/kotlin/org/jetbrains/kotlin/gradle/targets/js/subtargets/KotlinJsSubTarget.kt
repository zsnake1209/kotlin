/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.subtargets

import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetWithTests
import org.jetbrains.kotlin.gradle.targets.js.AbstractKotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsPlatformTestRun
import org.jetbrains.kotlin.gradle.targets.js.KotlinJsTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsPlatformSupport
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.utils.lowerCamelCaseName

internal fun disambiguateCamelCased(jsPlatform: KotlinJsPlatformSupport, name: String): String {
    val target = jsPlatform.kotlinJsTarget
    val disambiguationClassifier = if (jsPlatform is KotlinJsSubTarget) jsPlatform.disambiguationClassifier else null
    return lowerCamelCaseName(target.disambiguationClassifier, disambiguationClassifier, name)
}

val KotlinJsPlatformSupport.kotlinJsTarget: AbstractKotlinJsTarget<*>
    get() = when (this) {
        is AbstractKotlinJsTarget<*> -> this
        is KotlinJsSubTarget -> this.target
        else -> error("Unexpected target $this")
    }

abstract class KotlinJsSubTarget(
    val target: KotlinJsTarget,
    internal val disambiguationClassifier: String
) : KotlinJsPlatformSupport {
    val project get() = target.project

    override val runTaskName = disambiguateCamelCased(this, "run")
    override val testTaskName = disambiguateCamelCased(this, "test")

    final override lateinit var testRuns: NamedDomainObjectContainer<KotlinJsPlatformTestRun>
        internal set
}