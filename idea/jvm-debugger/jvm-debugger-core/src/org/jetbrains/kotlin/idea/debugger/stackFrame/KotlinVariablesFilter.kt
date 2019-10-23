/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.stackFrame

import com.intellij.debugger.engine.JavaValue
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.DESTRUCTURED_LAMBDA_ARGUMENT_VARIABLE_PREFIX
import org.jetbrains.kotlin.codegen.coroutines.CONTINUATION_VARIABLE_NAME
import org.jetbrains.kotlin.codegen.coroutines.SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME
import org.jetbrains.kotlin.codegen.inline.isFakeLocalVariableForInline
import org.jetbrains.kotlin.idea.debugger.INLINED_THIS_REGEX
import org.jetbrains.kotlin.idea.debugger.ToggleKotlinVariablesState
import org.jetbrains.kotlin.idea.debugger.dropInlineSuffix
import org.jetbrains.kotlin.idea.debugger.getInlineDepth

// methods are copied from KotlinStackFrame and changed for JavaValue
object KotlinVariablesFilter {
    private val kotlinVariableViewService = ToggleKotlinVariablesState.getService()

    fun computeVariables(node: XCompositeNode, vars: List<JavaValue>, insideInline: Boolean) {
        if (!kotlinVariableViewService.kotlinVariableView) { // show raw
            val list = XValueChildrenList()
            vars.forEach { list.add(it) }
            node.addChildren(list, true)
            return
        }
        val inlineDepth = if (insideInline) getInlineDepth(vars) else 0

        val (thisVariables, otherVariables) = vars.asSequence()
            .filter { !isHidden(it, inlineDepth) }
            .partition {
                it.name == AsmUtil.THIS
                        || it.name == AsmUtil.THIS_IN_DEFAULT_IMPLS
                        || it.name.startsWith(AsmUtil.LABELED_THIS_PARAMETER)
                        || (INLINED_THIS_REGEX.matches(it.name))
            }

        val (mainThis, otherThis) = thisVariables
            .reversed()
            .let { it.firstOrNull() to it.drop(1) }

        val remappedMainThis = mainThis?.remapThisVariableIfNeeded(AsmUtil.THIS)
        val remappedOther = (otherThis + otherVariables).map { it.remapThisVariableIfNeeded() }
        val children = XValueChildrenList((if (remappedMainThis != null) 1 else 0) + remappedOther.size)
        (listOfNotNull(remappedMainThis) + remappedOther).forEach {
            children.add(it.toString(), it) // XNamedValue's toString returns name, clone's toString returns remapped name
        }
        node.addChildren(children, true)
    }

    private fun getInlineDepth(vars: List<JavaValue>): Int {
        for (variable in vars.reversed()) {
            val name = variable.name
            val depth = getInlineDepth(name)
            if (depth > 0)
                return depth
        }

        return 0
    }

    private fun isHidden(variable: JavaValue, inlineDepth: Int): Boolean {
        val name = variable.name
        return isFakeLocalVariableForInline(name)
                || name.startsWith(DESTRUCTURED_LAMBDA_ARGUMENT_VARIABLE_PREFIX)
                || name.startsWith(AsmUtil.LOCAL_FUNCTION_VARIABLE_PREFIX)
                || name == CONTINUATION_VARIABLE_NAME
                || getInlineDepth(variable.name) != inlineDepth
                || name == SUSPEND_FUNCTION_COMPLETION_PARAMETER_NAME
    }

    private fun JavaValue.remapThisVariableIfNeeded(customName: String? = null): JavaValue {
        val name = dropInlineSuffix(this.name)

        @Suppress("ConvertToStringTemplate")
        return when {
            isLabeledThisReference() -> {
                val label = name.drop(AsmUtil.LABELED_THIS_PARAMETER.length)
                clone(customName ?: getThisName(label))
            }
            name == AsmUtil.THIS_IN_DEFAULT_IMPLS -> clone(customName ?: AsmUtil.THIS + " (outer)")
            name == AsmUtil.RECEIVER_PARAMETER_NAME -> clone(customName ?: AsmUtil.THIS + " (receiver)")
            INLINED_THIS_REGEX.matches(name) -> clone(customName ?: AsmUtil.THIS + " (inlined)") // cannot calculate type here
            name != this.name -> clone(name)
            else -> this@remapThisVariableIfNeeded
        }
    }

    private fun JavaValue.isLabeledThisReference(): Boolean {
        return name.startsWith(AsmUtil.LABELED_THIS_PARAMETER)
    }

    private fun JavaValue.clone(name: String): JavaValue {
        return object : JavaValue(null, descriptor, evaluationContext, nodeManager, false) {
            override fun toString(): String {
                return name
            }
        }
    }
}