/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.testing.chromium

import org.gradle.api.Plugin
import org.gradle.api.Project

open class ChromiumPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        check(project == project.rootProject) {
            "ChromiumPlugin can be applied only to root project"
        }

        this.extensions.create(ChromiumRootExtension.CHROMIUM, ChromiumRootExtension::class.java, this)
        tasks.create(ChromiumSetupTask.NAME, ChromiumSetupTask::class.java)
    }

    companion object {
        fun apply(project: Project): ChromiumRootExtension {
            val rootProject = project.rootProject
            rootProject.plugins.apply(ChromiumPlugin::class.java)
            return rootProject.extensions.getByName(ChromiumRootExtension.CHROMIUM) as ChromiumRootExtension
        }
    }
}
