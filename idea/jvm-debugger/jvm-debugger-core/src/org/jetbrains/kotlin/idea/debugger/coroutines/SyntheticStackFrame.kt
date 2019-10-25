/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import org.jetbrains.kotlin.idea.core.util.getLineCount
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.debugger.stackFrame.IsInline
import org.jetbrains.kotlin.idea.debugger.stackFrame.KotlinVariablesFilter

/**
 * Puts the frameProxy into JavaStackFrame just to instantiate. SyntheticStackFrame provides it's own data for variables view.
 */
class SyntheticStackFrame(
    descriptor: StackFrameDescriptorImpl,
    private val vars: List<JavaValue>,
    private val position: XSourcePosition
) :
    JavaStackFrame(descriptor, true) {

    override fun computeChildren(node: XCompositeNode) {
        if (descriptor is EmptyStackFrameDescriptor) return // no variables any way
        KotlinVariablesFilter.computeVariables(node, vars, isInsideInlineFun())
    }

    private fun isInsideInlineFun(): IsInline {
        val project = descriptor.debugProcess.project
        val sourceFile = position.file.toPsiFile(project)

        return IsInline.valueOf(sourceFile?.let { sourceFile.getLineCount() < position.line })
    }

    override fun getSourcePosition(): XSourcePosition? {
        return position
    }

    // non-symmetric equals to avoid exceptions from frames view,
    // when it tries to find this frame in frames view after selecting it in coroutines view
    // (API trouble)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        val frame = other as? JavaStackFrame ?: return false

        return descriptor.frameProxy == frame.descriptor.frameProxy
    }

    override fun hashCode(): Int {
        return descriptor.frameProxy.hashCode()
    }
}