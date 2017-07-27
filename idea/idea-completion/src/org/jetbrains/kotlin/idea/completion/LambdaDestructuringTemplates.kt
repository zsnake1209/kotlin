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

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.*
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.completion.handlers.isCharAt
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.DescriptorRendererModifier
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter

object LambdaDestructuringTemplates {


    fun presentation(destructuringInformation: List<Pair<Name, KotlinType>>,
                     explicitTypes: Boolean): String {

        return buildString {
            append("(")
            destructuringInformation.joinTo(this) { (name, type) ->
                var component = name.render()
                if (explicitTypes) {
                    component += ": " + IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_IN_TYPES.renderType(type)
                }
                component
            }
            append(")")
            append(" ->")
        }
    }

    fun insertTemplate(
            context: InsertionContext,
            placeholderRange: TextRange,
            destructuringInformation: List<Pair<Name, KotlinType>>,
            explicitTypes: Boolean
    ) {
        // we start template later to not interfere with insertion of tail type
        val commandProcessor = CommandProcessor.getInstance()
        val commandName = commandProcessor.currentCommandName ?: "Insert lambda template"
        val commandGroupId = commandProcessor.currentCommandGroupId

        val rangeMarker = context.document.createRangeMarker(placeholderRange)

        context.setLaterRunnable {
            context.project.executeWriteCommand(commandName, groupId = commandGroupId) {
                try {
                    if (rangeMarker.isValid) {
                        val startOffset = rangeMarker.startOffset
                        context.document.deleteString(startOffset, rangeMarker.endOffset)

                        val spaceAhead = context.document.charsSequence.isCharAt(startOffset, ' ')
                        if (!spaceAhead) {
                            context.document.insertString(startOffset, " ")
                        }

                        context.editor.caretModel.moveToOffset(startOffset)
                        val template = LambdaDestructuringTemplates.buildTemplate(context.project, destructuringInformation, explicitTypes)
                        TemplateManager.getInstance(context.project).startTemplate(context.editor, template)
                    }
                }
                finally {
                    rangeMarker.dispose()
                }
            }
        }
    }

    private fun buildTemplate(project: Project,
                              destructuringInformation: List<Pair<Name, KotlinType>>,
                              explicitTypes: Boolean): Template {
        assert(destructuringInformation.isNotEmpty())
        val templateManager = TemplateManager.getInstance(project)
        val template = templateManager.createTemplate("", "")

        template.isToShortenLongNames = true
        template.isToReformat = true
        template.addTextSegment("(")
        destructuringInformation.forEachIndexed { i, (name, type) ->
            val nameResult = TextResult(name.render())
            val nameExpression = object : Expression() {
                override fun calculateQuickResult(context: ExpressionContext?) = nameResult

                override fun calculateLookupItems(context: ExpressionContext?) = emptyArray<LookupElement>()

                override fun calculateResult(context: ExpressionContext?) = nameResult
            }

            template.addVariable(nameExpression, true)
            if (explicitTypes) {
                template.addTextSegment(": ")
                if (type.isTypeParameter()) {
                    val typeParameterNameResult = TextResult(TYPE_RENDERER.renderType(type))
                    val typeExpression = object : Expression() {
                        override fun calculateQuickResult(context: ExpressionContext?) = typeParameterNameResult

                        override fun calculateLookupItems(context: ExpressionContext?) = emptyArray<LookupElement>()

                        override fun calculateResult(context: ExpressionContext?) = typeParameterNameResult
                    }
                    template.addVariable(typeExpression, false)
                }
                else {
                    template.addTextSegment(TYPE_RENDERER.renderType(type))
                }
            }
            if (i < destructuringInformation.lastIndex) {
                template.addTextSegment(", ")
            }
        }
        template.addTextSegment(")")
        template.addTextSegment(" -> ")
        template.addEndVariable()

        return template
    }

    private val TYPE_RENDERER = IdeDescriptorRenderers.SOURCE_CODE.withOptions {
        modifiers -= DescriptorRendererModifier.ANNOTATIONS
    }
}