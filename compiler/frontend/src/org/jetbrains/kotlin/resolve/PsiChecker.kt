/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.DiagnosticFactoryByParserErrors
import org.jetbrains.kotlin.diagnostics.ParserErrors
import org.jetbrains.kotlin.lexer.KtTokens.EXCL
import org.jetbrains.kotlin.parsing.KotlinParser
import org.jetbrains.kotlin.parsing.KotlinParsing
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.allChildren

class PsiChecker(val trace: BindingTrace) {
    companion object {
        private fun traversePsi(trace: BindingTrace, element: PsiElement) {
//            val j = element.containingFile.getCopyableUserData(KotlinParser.errorsSetKey2)
//
//            if (j != null) {
//                val el = element.containingFile.findElementAt(j.second)
//                if (el != null) {
//                    DiagnosticFactoryByParserErrors.getDiagnostic(j.first)?.let { diagnosticFactory ->
//                        trace.report(diagnosticFactory.on(el))
//                    }
//                }
//            } else {
//
//            }
//
//            element.containingFile.putCopyableUserData(KotlinParser.errorsSetKey2, null)

            element.allChildren.forEach { child ->
                val parserError = child.getUserData(KotlinParser.errorsSetKey)

                if (child.node.elementType == EXCL) {
                    println(1)
                }

                if (parserError != null) {
                    DiagnosticFactoryByParserErrors.getDiagnostic(parserError)?.let { diagnosticFactory ->
                        trace.report(diagnosticFactory.on(child))
                    }
                }

                traversePsi(trace, child)
            }
        }

        fun check(trace: BindingTrace?, element: KtElement) {
            traversePsi(trace ?: return, element)
        }
    }

    private fun traversePsi(element: PsiElement) = traversePsi(trace, element)

    fun check(context: TopDownAnalysisContext) {
        context.files.forEach { traversePsi(it) }
    }
}