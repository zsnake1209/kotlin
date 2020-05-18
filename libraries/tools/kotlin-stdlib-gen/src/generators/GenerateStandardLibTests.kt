/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package generators

import templates.*
import templates.tests.*
import java.io.File
import kotlin.system.exitProcess

/**
 * Generates methods in the standard library which are mostly identical
 * but just using a different input kind.
 *
 * Kinda like mimicking source macros here, but this avoids the inefficiency of type conversions
 * at runtime.
 */
fun main(args: Array<String>) {
    val templateGroups = sequenceOf<TestTemplateGroupBase>(
        AggregatesTest,
        NumbersTest,
        ComparablesTest,
        ElementsTest,
        ArraysTest
    )

    COPYRIGHT_NOTICE = readCopyrightNoticeFromProfile { Thread.currentThread().contextClassLoader.getResourceAsStream("apache.xml").reader() }

    val targetBaseDirs = mutableMapOf<KotlinTarget, File>()

    when (args.size) {
        1 -> {
            val baseDir = File(args.first())
            targetBaseDirs[KotlinTarget.Common] = baseDir.resolveExistingDir("libraries/stdlib/test/generated")
            targetBaseDirs[KotlinTarget.JVM] = baseDir.resolveExistingDir("libraries/stdlib/jvm/test/generated")
            targetBaseDirs[KotlinTarget.JS] = baseDir.resolveExistingDir("libraries/stdlib/js/test/generated")
        }
        2 -> {
            val (targetName, targetDir) = args
            val target = KotlinTarget.values.singleOrNull { it.name.equals(targetName, ignoreCase = true) } ?: error("Invalid target: $targetName")
            targetBaseDirs[target] = File(targetDir).also { it.requireExistingDir() }
        }
        else -> {
            println("""Parameters:
    <kotlin-base-dir> - generates sources for common, jvm, js, ir-js targets using paths derived from specified base path
    <target> <target-dir> - generates source for the specified target in the specified target directory
""")
            exitProcess(1)
        }
    }

    templateGroups.groupByFileAndWriteTest(targetsToGenerate = targetBaseDirs.keys) { (target, source) ->
        val targetDir = targetBaseDirs[target] ?: error("Target $target directory is not configured")
        val platformSuffix = when (val platform = target.platform) {
            Platform.Common -> ""
            else -> platform.name
        }
        targetDir.resolve("_${source.name.capitalize() + platformSuffix}Test.kt")
    }
}
