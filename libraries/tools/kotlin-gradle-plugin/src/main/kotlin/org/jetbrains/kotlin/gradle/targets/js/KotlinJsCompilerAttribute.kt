/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.AttributesSchema
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.jetbrains.kotlin.gradle.targets.metadata.isKotlinGranularMetadataEnabled
import java.io.Serializable

// For Gradle attributes
@Suppress("EnumEntryName")
enum class KotlinJsCompilerAttribute : Named, Serializable {
    legacy,
    ir;

    override fun getName(): String =
        name

    override fun toString(): String =
        getName()

    companion object {
        val jsCompilerAttribute = Attribute.of(
            "org.jetbrains.kotlin.js.compiler",
            KotlinJsCompilerAttribute::class.java
        )

        fun setupAttributesMatchingStrategy(project: Project, attributesSchema: AttributesSchema) {
            attributesSchema.attribute(jsCompilerAttribute) { strategy ->
                if (project.isKotlinGranularMetadataEnabled) {
                    strategy.disambiguationRules.add(KotlinJsCompilerDisambiguationRule::class.java)
                }
            }
        }
    }
}

private class KotlinJsCompilerDisambiguationRule : AttributeDisambiguationRule<KotlinJsCompilerAttribute> {
    override fun execute(details: MultipleCandidatesDetails<KotlinJsCompilerAttribute>) {
        TODO()
    }
}