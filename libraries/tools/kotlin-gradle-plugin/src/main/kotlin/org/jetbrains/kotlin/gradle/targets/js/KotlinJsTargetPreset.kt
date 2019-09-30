/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch")

// Old package for compatibility
package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.KotlinOnlyTargetConfigurator
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.targets.js.*
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinBrowserJsPlatformConfigurator
import org.jetbrains.kotlin.gradle.targets.js.subtargets.KotlinNodeJsPlatformConfigurator

abstract class AbstractKotlinJsTargetPreset<TargetType : AbstractKotlinJsTarget<*>>(
    project: Project,
    kotlinPluginVersion: String
) : KotlinOnlyTargetPreset<TargetType, KotlinJsCompilation>(project, kotlinPluginVersion) {

    override val platformType: KotlinPlatformType
        get() = KotlinPlatformType.js

    override fun createCompilationFactory(forTarget: KotlinOnlyTarget<KotlinJsCompilation>) =
        KotlinJsCompilationFactory(project, forTarget)

    abstract override fun instantiateTarget(): TargetType

    abstract override fun createKotlinTargetConfigurator(): KotlinOnlyTargetConfigurator<KotlinJsCompilation, TargetType>
}

open class KotlinJsTargetPreset(
    project: Project,
    kotlinPluginVersion: String
) : AbstractKotlinJsTargetPreset<KotlinJsTarget>(
    project,
    kotlinPluginVersion
) {
    override fun instantiateTarget(): KotlinJsTarget {
        return project.objects.newInstance(KotlinJsTarget::class.java, project)
    }

    override fun createKotlinTargetConfigurator() = KotlinJsTargetConfigurator(kotlinPluginVersion)

    override fun getName(): String = PRESET_NAME

    companion object {
        const val PRESET_NAME = "js"
    }
}

class KotlinJsSingleTargetPreset(
    project: Project,
    kotlinPluginVersion: String
) :
    KotlinJsTargetPreset(project, kotlinPluginVersion) {

    // In a Kotlin/JS single-platform project, we don't need any disambiguation suffixes or prefixes in the names:
    override fun provideTargetDisambiguationClassifier(target: KotlinOnlyTarget<KotlinJsCompilation>): String? = null

    override fun createKotlinTargetConfigurator() = KotlinJsTargetConfigurator(kotlinPluginVersion)
}

class KotlinNodeJsTargetPreset(project: Project, kotlinPluginVersion: String) :
    AbstractKotlinJsTargetPreset<KotlinNodeJsTarget>(project, kotlinPluginVersion) {

    override fun instantiateTarget(): KotlinNodeJsTarget =
        project.objects.newInstance(KotlinNodeJsTarget::class.java, project)

    override fun createKotlinTargetConfigurator(): KotlinOnlyTargetConfigurator<KotlinJsCompilation, KotlinNodeJsTarget> =
        KotlinJsPlatformTargetConfigurator(kotlinPluginVersion, KotlinNodeJsPlatformConfigurator())

    override fun getName(): String = PRESET_NAME

    companion object {
        const val PRESET_NAME = "nodeJs"
    }
}

class KotlinBrowserJsTargetPreset(project: Project, kotlinPluginVersion: String) :
    AbstractKotlinJsTargetPreset<KotlinBrowserJsTarget>(project, kotlinPluginVersion) {

    override fun instantiateTarget(): KotlinBrowserJsTarget =
        project.objects.newInstance(KotlinBrowserJsTarget::class.java, project)

    override fun createKotlinTargetConfigurator(): KotlinOnlyTargetConfigurator<KotlinJsCompilation, KotlinBrowserJsTarget> =
        KotlinJsPlatformTargetConfigurator(kotlinPluginVersion, KotlinBrowserJsPlatformConfigurator())

    override fun getName(): String = PRESET_NAME

    companion object {
        const val PRESET_NAME = "browserJs"
    }
}