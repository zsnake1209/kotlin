/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.testing.internal

import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.testing.TestTaskReports
import org.gradle.testing.base.plugins.TestingBasePlugin
import org.jetbrains.kotlin.gradle.tasks.KotlinTest
import org.jetbrains.kotlin.gradle.utils.isGradleVersionAtLeast
import java.io.File

@Suppress("UnstableApiUsage")
internal val Project.testResultsDir: File
    get() = project.buildDir.resolve(TestingBasePlugin.TEST_RESULTS_DIR_NAME)
internal val Project.reportsDir: File
    get() = project.extensions.getByType(ReportingExtension::class.java).baseDir
@Suppress("UnstableApiUsage")
internal val Project.testReportsDir: File
    get() = reportsDir.resolve(TestingBasePlugin.TESTS_DIR_NAME)

internal fun KotlinTest.configureConventions() {
    reports.configureConventions(project, name)
    val binaryResultsDirDefault: () -> File = { project.testResultsDir.resolve("$name/binary") }

    @Suppress("UnstableApiUsage")
    val defaultBinaryResultsRelativePath = listOf(TestingBasePlugin.TEST_RESULTS_DIR_NAME, name, "binary").joinToString(File.separator)

    if (isGradleVersionAtLeast(4, 4)) {
        @Suppress("UnstableApiUsage")
        binaryResultsDirectory.convention(project.layout.buildDirectory.dir(defaultBinaryResultsRelativePath))
    } else {
        conventionMapping.map("binResultsDir", binaryResultsDirDefault)
    }
}

internal fun TestTaskReports.configureConventions(project: Project, name: String) {
    val htmlReport = DslObject(html)
    val xmlReport = DslObject(junitXml)

    xmlReport.conventionMapping.map("destination") { project.testResultsDir.resolve(name) }
    htmlReport.conventionMapping.map("destination") { project.testReportsDir.resolve(name) }
}