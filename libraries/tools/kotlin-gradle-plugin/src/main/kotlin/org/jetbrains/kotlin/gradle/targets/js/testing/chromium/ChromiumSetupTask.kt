/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.js.testing.chromium

import de.undercouch.gradle.tasks.download.DownloadAction
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

open class ChromiumSetupTask : DefaultTask() {
    private val settings get() = project.chromium
    private val env by lazy { settings.environment }

    val destination: File
        @OutputDirectory get() = env.home

    val downloadUrl
        @Input get() = env.downloadUrl

    init {
        onlyIf {
            settings.download
        }
    }

    @TaskAction
    fun setup() {
        val dir = temporaryDir
        val zip = download(dir)
        extract(zip, destination)
    }

    private fun download(tar: File): File {
        val action = DownloadAction(project)
        action.src(downloadUrl)
        action.dest(tar)
        action.execute()
        return action.outputFiles.singleOrNull() ?: error("Cannot get downloaded file $downloadUrl")
    }

    private fun extract(archive: File, destination: File) {
        val dirInZip = archive.name.removeSuffix(".zip")

        project.copy {
            it.from(project.zipTree(archive))
            it.into(destination)
            it.includeEmptyDirs = false

            it.eachFile { fileCopy ->
                fileCopy.path = fileCopy.path.removePrefix(dirInZip)
            }
        }
    }

    companion object {
        const val NAME: String = "kotlinChromiumSetup"
    }
}
