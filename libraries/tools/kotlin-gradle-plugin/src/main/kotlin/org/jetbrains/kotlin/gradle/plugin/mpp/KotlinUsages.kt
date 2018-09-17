/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Usage.*
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.usageByName
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast

object KotlinUsages {
    const val KOTLIN_API = "kotlin-api"
    const val KOTLIN_RUNTIME = "kotlin-runtime"
    val values = setOf(KOTLIN_API, KOTLIN_RUNTIME)

    private val jvmPlatformTypes: Set<KotlinPlatformType> = setOf(jvm, androidJvm)

    internal fun consumerApiUsage(target: KotlinTarget) = target.project.usageByName(
        when (target.platformType) {
            in jvmPlatformTypes -> JAVA_API
            else -> KOTLIN_API
        }
    )

    internal fun consumerRuntimeUsage(target: KotlinTarget) = target.project.usageByName(
        when (target.platformType) {
            in jvmPlatformTypes -> JAVA_RUNTIME
            else -> KOTLIN_RUNTIME
        }
    )

    internal fun producerApiUsage(target: KotlinTarget) = target.project.usageByName(
        when (target.platformType) {
            in jvmPlatformTypes -> JAVA_API
            else -> KOTLIN_API
        }
    )

    internal fun producerRuntimeUsage(target: KotlinTarget) = target.project.usageByName(
        when (target.platformType) {
            in jvmPlatformTypes -> JAVA_RUNTIME_JARS
            else -> KOTLIN_RUNTIME
        }
    )

    private class KotlinJavaRuntimeJarsCompatibility : AttributeCompatibilityRule<Usage> {
        // When Gradle resolves a plain old JAR dependency with no metadata attached, the Usage attribute of that dependency
        // is 'java-runtime-jars'. This rule tells Gradle that Kotlin consumers can consume plain old JARs:
        override fun execute(details: CompatibilityCheckDetails<Usage>) = with(details) {
            when {
                consumerValue?.name == KOTLIN_API && producerValue?.name == JAVA_API -> compatible()
                consumerValue?.name in values && producerValue?.name == JAVA_RUNTIME_JARS -> compatible()
            }
        }
    }

    internal fun setupAttributesMatchingStrategy(attributesSchema: AttributesSchema) {
        if (isGradleVersionAtLeast(4, 0)) {
            attributesSchema.attribute(Usage.USAGE_ATTRIBUTE) { strategy ->
                // TODO do we need to set up a compatibilty rule saying 'kotlin-api consumers can also use kotlin-runtime'?
                strategy.compatibilityRules.add(KotlinJavaRuntimeJarsCompatibility::class.java)
            }
        }
    }
}