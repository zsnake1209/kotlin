/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.js.config.JSConfigurationKeys
import org.jetbrains.kotlin.js.test.BasicBoxTest
import org.jetbrains.kotlin.js.test.BasicIrBoxTest
import org.jetbrains.kotlin.serialization.js.ModuleKind
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.TargetBackend
import org.jetbrains.kotlinx.atomicfu.compiler.extensions.AtomicfuComponentRegistrar
import org.junit.Test
import java.io.File

private val atomicfuCompileLibraryPath = File("plugins/atomicfu/atomicfu-compiler/atomicfu-js-0.14.2-1.4-M1-jsir.klib")
private val kotlinKlibPath = File("libraries/stdlib/js-ir/build/fullRuntime/klib")

abstract class AtomicfuBaseTest(relativePath: String) : BasicIrBoxTest(
    "plugins/atomicfu/atomicfu-compiler/testData/$relativePath",
    "plugins/atomicfu/atomicfu-compiler/testData/$relativePath"
) {
    override fun createEnvironment(): KotlinCoreEnvironment {
        return super.createEnvironment().also { environment ->
            AtomicfuComponentRegistrar.registerExtensions(environment.project)
            val libraries = listOf<String>(atomicfuCompileLibraryPath.absolutePath, kotlinKlibPath.path)
            environment.configuration.put(JSConfigurationKeys.LIBRARIES, libraries)
            environment.configuration.put(JSConfigurationKeys.MODULE_KIND, ModuleKind.PLAIN)
        }
    }
}

abstract class AbstractBasicAtomicfuTest : AtomicfuBaseTest("basic/")
abstract class AbstractLocksAtomicfuTest : AtomicfuBaseTest("locks/")