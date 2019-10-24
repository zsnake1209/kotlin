/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.testing.chromium

import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.DARWIN
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.LINUX
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.WIN
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlatform.X64

data class ChromiumPlatform(
    val platform: String,
    val architecture: String
) {
    val system: String
        get() = when (platform) {
            WIN, LINUX -> platform + if (architecture == X64) "_$X64" else ""
            DARWIN -> "mac"
            else -> throw IllegalArgumentException("Unsupported system: $platform-$architecture")
        }.capitalize()

    val archiveName: String
        get() = "chrome-" + when (platform) {
            LINUX -> platform
            DARWIN -> "mac"
            WIN -> "$platform${if (architecture == X64) "" else "32"}"
            else -> throw IllegalArgumentException("Unsupported system: $platform-$architecture")
        }

    val command: String
        get() {
            return when (platform) {
                LINUX -> "chrome"
                WIN -> "chrome.exe"
                DARWIN -> "Chromium.app/Contents/MacOS/Chromium"
                else -> throw IllegalArgumentException("Unsupported system: $platform-$architecture")
            }
        }

    operator fun component3(): String = system

    operator fun component4(): String = archiveName

    operator fun component5(): String = command
}