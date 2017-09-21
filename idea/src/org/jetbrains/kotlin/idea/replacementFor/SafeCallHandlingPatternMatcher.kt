/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.replacementFor

import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.quickfix.replacement.PatternAnnotationData
import org.jetbrains.kotlin.idea.quickfix.replacement.ResolvablePattern
import org.jetbrains.kotlin.idea.quickfix.replacement.analyzeAsExpression
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext

class SafeCallHandlingPatternMatcher(
        private val patternData: PatternAnnotationData,
        private val callable: FunctionDescriptor,
        private val resolutionFacade: ResolutionFacade
) {
    private data class ResolvablePatternWithBindingContext(private val resolvablePattern: ResolvablePattern) {
        val expression = resolvablePattern.expression
        val bindingContext by lazy(resolvablePattern.analyze)
    }

    private fun ResolvablePattern.withBindingContext() = ResolvablePatternWithBindingContext(this)

    private val resolvablePattern = patternData.analyzeAsExpression(callable, resolutionFacade)?.withBindingContext()
    private val matcher = resolvablePattern?.let { PatternMatcher(callable, it.expression, { it.bindingContext }) }
    private val safeMatcher by lazy { buildSafeCallPattern()?.withBindingContext()?.let { PatternMatcher(callable, it.expression, { it.bindingContext }) } }

    private fun buildSafeCallPattern(): ResolvablePattern? {
        if (resolvablePattern == null) return null
        val expression = resolvablePattern.expression
        when (expression) {
            is KtSafeQualifiedExpression -> return null

            is KtDotQualifiedExpression -> {
                //TODO: check that qualifier part is receiver
                val safeCallExpression = KtPsiFactory(expression).createExpressionByPattern(
                        "$0?.$1",
                        expression.receiverExpression,
                        expression.selectorExpression ?: return null
                )
                return PatternAnnotationData(safeCallExpression.text, patternData.imports).analyzeAsExpression(callable, resolutionFacade)
            }

            else -> {
                val receiver = expressionForImplicitReceiver(expression, resolvablePattern.bindingContext) ?: return null
                val safeCallExpression = KtPsiFactory(expression).createExpressionByPattern(
                        "$0?.$1",
                        receiver,
                        expression
                )
                return PatternAnnotationData(safeCallExpression.text, patternData.imports).analyzeAsExpression(callable, resolutionFacade)
            }
        }
    }

    fun matchExpression(expression: KtExpression, bindingContext: BindingContext, annotationData: PatternAnnotationData): ReplacementForPatternMatch? {
        if (resolvablePattern == null) return null
        if (expression is KtSafeQualifiedExpression && resolvablePattern.expression !is KtSafeQualifiedExpression) {
            return safeMatcher
                    ?.matchExpression(expression, bindingContext, annotationData)
                    ?.copy(safeCallReceiver = expression.receiverExpression)
        }
        else {
            return matcher!!.matchExpression(expression, bindingContext, annotationData)
        }
    }
}