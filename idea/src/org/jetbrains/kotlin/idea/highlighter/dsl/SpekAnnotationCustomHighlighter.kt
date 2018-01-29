/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter.dsl

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.highlighter.HighlighterExtension
import org.jetbrains.kotlin.resolve.annotations.argumentValue
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.constants.IntValue
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult

class SpekAnnotationCustomHighlighter : HighlighterExtension() {
    override fun highlightDeclaration(elementToHighlight: PsiElement, descriptor: DeclarationDescriptor): TextAttributesKey? {
        return null
    }

    override fun highlightCall(elementToHighlight: PsiElement, resolvedCall: ResolvedCall<*>): TextAttributesKey? {
        val style = styleNumberByCall(resolvedCall) ?: return null

        return keys[style]
    }

    private fun styleNumberByCall(resolvedCall: ResolvedCall<*>): Int? {
        val dslStyleAnnotation = resolvedCall.resultingDescriptor.annotations.firstNotNullResult { annotation ->
            annotation.annotationClass?.annotations?.find {
                it.annotationClass?.fqNameSafe?.shortName()?.asString() == "DslStyle"
            }
        } ?: return null

        val valueFromAnnotation = (dslStyleAnnotation.argumentValue("style") as? IntValue)?.value

        if (valueFromAnnotation in 0 until STYLES) {
            return valueFromAnnotation
        }

        return null
    }

    companion object {
        private val STYLES = 16

        val keys = (0 until STYLES).map { styleNum -> TextAttributesKey.createTextAttributesKey("KOTLIN::DSL::CUSTOM$styleNum") }

        val descriptionsToKeys: Map<String, TextAttributesKey> = keys.withIndex().associate { (styleNum, style) ->
            "Dsl//Custom//Style$styleNum" to style
        }
    }
}