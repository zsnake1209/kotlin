/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XCompositeNode
import org.jetbrains.kotlin.idea.core.util.getLineCount
import org.jetbrains.kotlin.idea.debugger.stackFrame.KotlinVariablesFilter
import org.jetbrains.kotlin.idea.stubindex.KotlinFileFacadeFqNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex

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

    private fun isInsideInlineFun(): Boolean {
        val stackTraceElement = (descriptor as? SuspendStackFrameDescriptor)?.frame
        val outerClassOrFile = stackTraceElement?.className?.substringBefore('$') ?: return true
        val project = descriptor.debugProcess.project
        val scope = GlobalSearchScope.allScope(project)
        // copied from JKResolver
        val classesPsi = KotlinFullClassNameIndex.getInstance()[outerClassOrFile, project, scope]
        val psiClass = classesPsi.firstOrNull()
        val sourceFile = if (psiClass != null) {
            psiClass.containingKtFile
        } else {
            val ktFiles = KotlinFileFacadeFqNameIndex.getInstance()[outerClassOrFile, project, scope]
            ktFiles.asSequence().minWith(Comparator { o1, o2 ->
                GlobalSearchScope.allScope(project).compare(o1.containingFile.virtualFile, o2.containingFile.virtualFile)
            })
        }

        return if (sourceFile != null)
            sourceFile.getLineCount() < stackTraceElement.lineNumber
        else
            true // if it's not inside inline, it still will calculate 0 depth
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