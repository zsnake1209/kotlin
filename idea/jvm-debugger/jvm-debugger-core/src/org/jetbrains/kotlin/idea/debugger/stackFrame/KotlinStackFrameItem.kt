/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.stackFrame

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.xdebugger.frame.XCompositeNode
import com.sun.jdi.Location

class KotlinStackFrameItem(private val location: Location, private val variables: List<JavaValue>) :
    StackFrameItem(location, variables) {

    override fun createFrame(debugProcess: DebugProcessImpl): CapturedStackFrame {
        return CapturedStackFrame(debugProcess, this)
    }

    class CapturedStackFrame(debugProcess: DebugProcessImpl, private val item: KotlinStackFrameItem) :
        StackFrameItem.CapturedStackFrame(debugProcess, item) {

        override fun computeChildren(node: XCompositeNode) {
            // if line is not inside class - it's inline fun
            val insideInline = item.location.declaringType().locationsOfLine(item.line()).isEmpty()
            KotlinVariablesFilter.computeVariables(node, item.variables, insideInline)
        }

        override fun getCaptionAboveOf() = "Coroutine stack trace"
    }
}
