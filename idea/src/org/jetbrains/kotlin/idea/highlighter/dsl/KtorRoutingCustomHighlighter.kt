/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter.dsl

import com.intellij.openapi.editor.XmlHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.highlighter.HighlighterExtension
import org.jetbrains.kotlin.idea.highlighter.KotlinHighlightingColors
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.calls.checkers.DslScopeViolationCallChecker.extractDslMarkerFqNames
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.utils.keysToMap

class KtorRoutingCustomHighlighter : HighlighterExtension() {
    override fun highlightDeclaration(elementToHighlight: PsiElement, descriptor: DeclarationDescriptor): TextAttributesKey? {
        return null
    }

    override fun highlightCall(elementToHighlight: PsiElement, resolvedCall: ResolvedCall<*>): TextAttributesKey? {
        val isDslCall = listOfNotNull(
                resolvedCall.extensionReceiver,
                resolvedCall.dispatchReceiver
        ).any { KTOR_CONTEXT_MARKER in it.type.extractDslMarkerFqNames() }

        if (!isDslCall) {
            return null
        }

        return verbsToKeys[resolvedCall.resultingDescriptor.name.asString()]
    }

    companion object {
        private val HTML_DSL_ATTRIBUTES by lazy {
            TextAttributesKey.createTextAttributesKey(
                    "KOTLIN_DSL::KOTLINX_HTML",
                    TextAttributes.merge(
                            XmlHighlighterColors.HTML_TAG.defaultAttributes,
                            XmlHighlighterColors.HTML_TAG_NAME.defaultAttributes
                    )
            )
        }

        private val verbs = listOf(
            "get", "put", "patch", "delete", "post", "head", "options"
        )

        private fun verbDescription(verb: String) = "Dsl//Ktor//${verb.capitalize()}"

        private val verbKey = TextAttributesKey.createTextAttributesKey("KOTLIN_DSL::KTOR::VERB", KotlinHighlightingColors.KEYWORD)

        private val verbsToKeys = verbs.keysToMap {
            verbName ->
            TextAttributesKey.createTextAttributesKey("KOTLIN_DSL::KTOR::$verbName", verbKey)
        }

        val descriptionsToKeys = verbsToKeys.mapKeys { verbDescription(it.key) } + (verbDescription("verb") to verbKey)

        private val KTOR_CONTEXT_MARKER = FqName("io.ktor.pipeline.ContextDsl")
    }
}