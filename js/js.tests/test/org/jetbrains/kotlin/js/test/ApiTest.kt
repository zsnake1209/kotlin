/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.test

import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.js.messageCollectorLogger
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptorWithVisibility
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageViewDescriptor
import org.jetbrains.kotlin.ir.backend.js.MainModule
import org.jetbrains.kotlin.ir.backend.js.jsResolveLibraries
import org.jetbrains.kotlin.ir.backend.js.loadIr
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.config.JsConfig
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.KotlinTestWithEnvironment
import org.jetbrains.kotlin.test.util.RecursiveDescriptorComparator
import org.jetbrains.kotlin.test.util.RecursiveDescriptorComparator.RECURSIVE_ALL
import java.io.File
import java.io.PrintWriter

class ApiTest : KotlinTestWithEnvironment() {

    fun testStdlib() {
        val project = environment.project
        val configuration = environment.configuration

        configuration.put(CommonConfigurationKeys.MODULE_NAME, "test")
        configuration.put(JSConfigurationKeys.LIBRARIES, JsConfig.JS_STDLIB)

        val config = JsConfig(project, configuration)

        config.moduleDescriptors.single().checkRecursively("js/js.translator/testData/api/stdlib")
    }

    fun testIrStdlib() {
        val fullRuntimeKlib: String = System.getProperty("kotlin.js.full.stdlib.path")

        val resolvedLibraries =
            jsResolveLibraries(listOf(File(fullRuntimeKlib).absolutePath), messageCollectorLogger(MessageCollector.NONE))

        val project = environment.project
        val configuration = environment.configuration

        val moduleDescriptor = loadIr(
            project,
            MainModule.Klib(resolvedLibraries.getFullList().single()),
            AnalyzerWithCompilerReport(configuration),
            configuration,
            resolvedLibraries,
            listOf()
        ).module.descriptor

        moduleDescriptor.checkRecursively("js/js.translator/testData/api/stdlib-ir")
    }

    private fun ModuleDescriptor.checkRecursively(dir: String) {
        val dirFile = File(dir)
        assert(dirFile.exists()) { "Directory does not exist: ${dirFile.absolutePath}" }
        assert(dirFile.isDirectory) { "Not a directory: ${dirFile.absolutePath}" }
        val files = File(dir).listFiles()!!.map { it.name }.toMutableSet()
        allPackages().forEach { fqName ->
            getPackage(fqName).serialize()?.let { serialized ->
                serialized.forEachIndexed { index, part ->

                    val fileName =
                        (if (fqName.isRoot) "ROOT" else fqName.asString()) + (if (serialized.size == 1) "" else "-$index") + ".kt"
                    files -= fileName

                    // Uncomment to overwrite the test data
//                    PrintWriter("$dir/$fileName").use { it.print(part) }

                    KotlinTestUtils.assertEqualsToFile(File("$dir/$fileName"), part)
                }
            }
        }

        assert(files.isEmpty()) { "Extra files found: ${files}" }
    }

    private fun ModuleDescriptor.allPackages(): Collection<FqName> {
        val result = mutableListOf<FqName>()

        fun impl(pkg: FqName) {
            result += pkg

            getSubPackagesOf(pkg) { true }.forEach { impl(it) }
        }

        impl(FqName.ROOT)

        return result
    }

    private fun PackageViewDescriptor.serialize(): List<String>? {
        val comparator = RecursiveDescriptorComparator(RECURSIVE_ALL.filterRecursion {
            when {
                it is DeclarationDescriptorWithVisibility && !it.visibility.isPublicAPI -> false
                it is CallableMemberDescriptor && !it.kind.isReal -> false
                it is PackageViewDescriptor -> false
                else -> true
            }
        })

        val serialized = comparator.serializeRecursively(this).trim()

        if (serialized.count { it == '\n' } <= 1) return null

        if (serialized.lines().size > LINES_PER_FILE_CUTOFF) {
            val result = mutableListOf<String>()

            val sb = StringBuilder()
            var cnt = 0
            var bracketBalance = 0

            for (d in serialized.lines()) {
                if (cnt > LINES_PER_FILE_CUTOFF && bracketBalance == 0 && (d.isBlank() || !d[0].isWhitespace())) {
                    result += sb.toString()
                    sb.clear()
                    cnt = 0
                }

                sb.append(d).append('\n')
                ++cnt
                bracketBalance += d.count { it == '{' } - d.count { it == '}' }
            }

            if (sb.isNotBlank()) {
                result += sb.toString()
            }

            return result
        }

        return listOf(serialized)
    }

    override fun createEnvironment(): KotlinCoreEnvironment {
        return KotlinCoreEnvironment.createForTests(TestDisposable(), CompilerConfiguration(), EnvironmentConfigFiles.JS_CONFIG_FILES)
    }
}

// IDEA isn't able to show diff for files that are too big
private val LINES_PER_FILE_CUTOFF = 1000