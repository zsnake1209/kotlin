/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.testing.chromium

import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.logging.kotlinInfo
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.DARWIN
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.LINUX
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.WIN
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.X64
import java.io.File

open class ChromiumRootExtension(val project: Project) {
    init {
        check(project.rootProject == project)
    }

    private val gradleHome = project.gradle.gradleUserHomeDir.also {
        project.logger.kotlinInfo("Storing cached files in $it")
    }

    var installationDir = gradleHome.resolve("chrome")

    var download = true
    var downloadBaseUrl = "https://storage.googleapis.com"

    val chromiumSetupTask: ChromiumSetupTask
        get() = project.tasks.getByName(ChromiumSetupTask.NAME) as ChromiumSetupTask

    // Sync with puppeteer 1.20.0
    // https://github.com/GoogleChrome/puppeteer/blob/master/package.json
    var revision = "706915"

    internal val environment: ChromiumEnv
        get() {
            val isWindows = NodeJsPlatform.name == NodeJsPlatform.WIN

            val dir = installationDir.resolve(revision)

            val platform = NodeJsPlatform.name
            val architecture = NodeJsPlatform.architecture

            val system: String = when (platform) {
                WIN, LINUX -> platform + if (architecture == X64) "_$X64" else ""
                DARWIN -> "mac"
                else -> throw IllegalArgumentException("Unsupported system: $platform-$architecture")
            }.capitalize()

            val archiveName: String = "chrome-" + when (platform) {
                LINUX -> platform
                DARWIN -> "mac"
                WIN -> "$platform${if (architecture == X64) "" else "32"}"
                else -> throw IllegalArgumentException("Unsupported system: $platform-$architecture")
            }

            val finalCommand = "Chromium${if (isWindows) ".exe" else ""}"
            val executable = if (download) File(dir, finalCommand).absolutePath else null

            return ChromiumEnv(
                home = dir,
                executable = executable,
                downloadUrl = "$downloadBaseUrl/chromium-browser-snapshots/$system/$revision/$archiveName.zip"
            )
        }

    companion object {
        const val CHROMIUM: String = "kotlinChromium"

        operator fun get(project: Project): ChromiumRootExtension {
            val rootProject = project.rootProject
            rootProject.plugins.apply(ChromiumPlugin::class.java)
            return rootProject.extensions.getByName(CHROMIUM) as ChromiumRootExtension
        }
    }
}

val Project.chromium: ChromiumRootExtension
    get() = ChromiumRootExtension[this]
