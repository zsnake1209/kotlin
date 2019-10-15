/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.execution.process.ProcessOutputTypes
import org.jetbrains.kotlin.idea.debugger.coroutines.CoroutineState
import org.jetbrains.kotlin.idea.debugger.coroutines.CoroutinesDebugProbesProxy
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import org.jetbrains.kotlin.idea.debugger.test.preference.DebuggerPreferences

abstract class AbstractCoroutineDumpTest : KotlinDescriptorTestCaseWithStepping() {


    override fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences) {

        doOnBreakpoint {
            val evalContext = EvaluationContextImpl(this, frameProxy)
            val execContext = ExecutionContext(evalContext, frameProxy ?: return@doOnBreakpoint)
            val either = CoroutinesDebugProbesProxy.dumpCoroutines(execContext)
            try {
                if (either.isRight)
                    try {
                        val states = either.get()
                        print(stringDump(states), ProcessOutputTypes.SYSTEM)
                    } catch (ignored: Throwable) {
                    }
                else
                    throw AssertionError("Dump failed", either.left)
            } finally {
                resume(this)
            }
        }

        doOnBreakpoint {
            val evalContext = EvaluationContextImpl(this, frameProxy)
            val execContext = ExecutionContext(evalContext, frameProxy ?: return@doOnBreakpoint)
            val either = CoroutinesDebugProbesProxy.dumpCoroutines(execContext)
            try {
                if (either.isRight)
                    try {
                        val states = either.get()
                        print(stringDump(states), ProcessOutputTypes.SYSTEM)
                    } catch (ignored: Throwable) {
                    }
                else
                    throw AssertionError("Dump failed", either.left)
            } finally {
                resume(this)
            }
        }
    }

    private fun stringDump(states: List<CoroutineState>) = buildString {
        states.forEach {
            appendln("\"${it.name}\", state: ${it.state}")
        }
    }

    // TODO: decide how to properly add dependency into JVM
}