/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter.dsl

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import kotlinx.colorScheme.Call
import kotlinx.colorScheme.TextStyle
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.highlighter.HighlighterExtension
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

class ScriptBasedHighlighter : HighlighterExtension() {
    override fun highlightDeclaration(elementToHighlight: PsiElement, descriptor: DeclarationDescriptor): TextAttributesKey? {
        return null
    }

    override fun highlightCall(elementToHighlight: PsiElement, resolvedCall: ResolvedCall<*>): TextAttributesKey? {
        val call = CallImpl(resolvedCall)
        val project = elementToHighlight.project
        val script = project.service<HighlightingScriptsCache>().getScript() ?: return null
        val textStyle = kotlinx.colorScheme.highlightCall(script, call)
        return textStyle?.toAttributeKey()
    }

    private fun TextStyle.toAttributeKey(): TextAttributesKey? {
        return when (this) {
            TextStyle.Preset.Keyword -> KotlinHighlightingColors.KEYWORD
            TextStyle.Preset.Label -> KotlinHighlightingColors.LABEL
            TextStyle.Preset.HtmlTag -> DefaultLanguageHighlighterColors.MARKUP_TAG
            TextStyle.Custom.Custom0 -> SpekAnnotationCustomHighlighter.keys[0]
            TextStyle.Custom.Custom1 -> SpekAnnotationCustomHighlighter.keys[1]
            TextStyle.Custom.Custom2 -> SpekAnnotationCustomHighlighter.keys[2]
            TextStyle.Custom.Custom15 -> SpekAnnotationCustomHighlighter.keys[15]
            else -> null
        }
    }
}


