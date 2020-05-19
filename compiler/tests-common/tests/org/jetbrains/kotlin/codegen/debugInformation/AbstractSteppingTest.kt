/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.debugInformation

import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.Event
import com.sun.jdi.event.LocatableEvent
import junit.framework.TestCase
import org.jetbrains.kotlin.test.TargetBackend
import org.junit.AfterClass
import org.junit.BeforeClass
import java.io.File

abstract class AbstractSteppingTest : AbstractDebugTest() {

    override val virtualMachine: VirtualMachine = Companion.virtualMachine
    override val proxyPort: Int = Companion.proxyPort

    companion object {
        const val LINENUMBER_PREFIX = "// LINENUMBERS"
        const val JVM_LINENUMBER_PREFIX = "$LINENUMBER_PREFIX JVM"
        const val JVM_IR_LINENUMBER_PREFIX = "$LINENUMBER_PREFIX JVM_IR"
        var proxyPort = 0
        lateinit var process: Process
        lateinit var virtualMachine: VirtualMachine

        @BeforeClass
        @JvmStatic
        fun setUpTest() {
            val (process, port) = startDebuggeeProcess()
            this.process = process
            virtualMachine = attachDebugger(port)
            setUpVM(virtualMachine)

            proxyPort = getProxyPort(process)
        }

        @AfterClass
        @JvmStatic
        fun tearDownTest() {
            process.destroy()
        }
    }

    override fun storeStep(loggedItems: ArrayList<Any>, event: Event) {
        assert(event is LocatableEvent)
        loggedItems.add(event)
    }

    private fun readExpectations(wholeFile: File): String {
        val expected = mutableListOf<String>()
        val lines = wholeFile.readLines().dropWhile { !it.startsWith(LINENUMBER_PREFIX) }
        var currentBackend = TargetBackend.ANY
        for (line in lines) {
            if (line.startsWith(LINENUMBER_PREFIX)) {
                currentBackend = when (line) {
                    LINENUMBER_PREFIX -> TargetBackend.ANY
                    JVM_LINENUMBER_PREFIX -> TargetBackend.JVM
                    JVM_IR_LINENUMBER_PREFIX -> TargetBackend.JVM_IR
                    else -> error("Expected JVM backend")
                }
                continue
            }
            if (currentBackend == TargetBackend.ANY || currentBackend == backend) {
                expected.add(line.drop(3).trim())
            }
        }
        return expected.joinToString("\n")
    }

    override fun checkResult(wholeFile: File, loggedItems: List<Any>) {
        val expectedLineNumbers = readExpectations(wholeFile)
        val actualLineNumbers = loggedItems
            .filter {
                val location = (it as LocatableEvent).location()
                !location.method().isSynthetic
            }
            .map { event ->
                val location = (event as LocatableEvent).location()
                "${location.sourceName()}:${location.lineNumber()} ${location.method().name()}"
            }
        TestCase.assertEquals(expectedLineNumbers, actualLineNumbers.joinToString("\n"))
    }
}

