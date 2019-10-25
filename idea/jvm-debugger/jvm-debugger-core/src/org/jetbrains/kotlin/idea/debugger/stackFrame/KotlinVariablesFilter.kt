/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.stackFrame

import com.intellij.debugger.engine.JavaValue
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.idea.debugger.*

// methods are copied from KotlinStackFrame and changed for JavaValue
internal object KotlinVariablesFilter {
    private val kotlinVariableViewService = ToggleKotlinVariablesState.getService()

    fun computeVariables(node: XCompositeNode, vars: List<JavaValue>, isInline: IsInline) {
        if (!kotlinVariableViewService.kotlinVariableView) { // show raw
            val list = XValueChildrenList()
            vars.forEach { list.add(it) }
            node.addChildren(list, true)
            return
        }
        val inlineDepth = isInline.getInlineDepth(vars)

        val (thisVariables, otherVariables) = vars.asSequence()
            .filter { inlineDepth < 0 || !isHidden(it.name, inlineDepth) }
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

    private fun JavaValue.remapThisVariableIfNeeded(customName: String? = null): JavaValue {
        val name = dropInlineSuffix(this.name)

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

enum class IsInline {
    TRUE {
        override fun getInlineDepth(vars: List<JavaValue>): Int {
            for (variable in vars.reversed()) {
                val name = variable.name
                val depth = getInlineDepth(name)
                if (depth > 0)
                    return depth
            }
            return 0
        }
    },
    FALSE {
        override fun getInlineDepth(vars: List<JavaValue>) = 0
    },
    UNSURE {
        override fun getInlineDepth(vars: List<JavaValue>) = -1
    };

    abstract fun getInlineDepth(vars: List<JavaValue>): Int

    companion object {
        fun valueOf(boolean: Boolean?): IsInline {
            return when (boolean) {
                true -> TRUE
                false -> FALSE
                else -> UNSURE
            }
        }
    }
}
