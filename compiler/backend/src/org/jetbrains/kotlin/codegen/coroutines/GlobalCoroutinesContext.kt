/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.coroutines

import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm.SUSPENSION_POINT_INSIDE_MONITOR

class GlobalCoroutinesContext(private val diagnostics: DiagnosticSink) {
    private val inlineLambdaInsideMonitorSourceArgumentIndexes = arrayListOf<Set<Int>>()
    private val monitorStates = arrayListOf<Boolean>()

    fun pushArgumentIndexes(indexes: Set<Int>) {
        inlineLambdaInsideMonitorSourceArgumentIndexes.add(indexes)
    }

    fun popArgumentIndexes() {
        inlineLambdaInsideMonitorSourceArgumentIndexes.pop()
    }

    fun enterScope(monitorEnabled: Boolean) {
        monitorStates.push(monitorEnabled)
    }

    fun enterMonitorIfNeeded(index: Int?) {
        if (index == null) return
        if (inlineLambdaInsideMonitorSourceArgumentIndexes.peek()?.contains(index) != true) return
        enterScope(true)
    }

    fun exitScope() {
        assert(monitorStates.isNotEmpty()) {
            "exitScope without corresponding enterScope"
        }
        monitorStates.pop()
    }

    fun exitMonitorIfNeeded(index: Int?) {
        if (index == null) return
        if (inlineLambdaInsideMonitorSourceArgumentIndexes.peek()?.contains(index) != true) return
        exitScope()
    }

    fun checkSuspendCall(call: ResolvedCall<*>) {
        if (monitorStates.peek() == true) {
            diagnostics.report(SUSPENSION_POINT_INSIDE_MONITOR.on(call.call.callElement, call.resultingDescriptor))
        }
    }
}