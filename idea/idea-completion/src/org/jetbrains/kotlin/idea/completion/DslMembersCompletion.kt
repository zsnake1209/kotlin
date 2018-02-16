/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.core.KotlinIndicesHelper
import org.jetbrains.kotlin.idea.util.CallType
import org.jetbrains.kotlin.idea.util.CallTypeAndReceiver
import org.jetbrains.kotlin.idea.util.ReceiverType
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.psiUtil.collectAnnotationEntriesFromStubOrPsi
import org.jetbrains.kotlin.resolve.calls.DslMarkerUtils

class DslMembersCompletion(
    private val project: Project,
    private val prefixMatcher: PrefixMatcher,
    private val baseFactory: LookupElementFactory,
    receiverTypes: Collection<ReceiverType>?,
    private val collector: LookupElementsCollector,
    private val indicesHelper: KotlinIndicesHelper,
    private val callTypeAndReceiver: CallTypeAndReceiver<*, *>
) {
    private val dslReceiver = receiverTypes?.lastOrNull()?.takeIf {
        DslMarkerUtils.extractDslMarkerFqNames(it.type).isNotEmpty() && it.implicit
    }
    private val factory = object : AbstractLookupElementFactory {
        override fun createLookupElement(
            descriptor: DeclarationDescriptor,
            useReceiverTypes: Boolean,
            qualifyNestedClasses: Boolean,
            includeClassTypeArguments: Boolean,
            parametersAndTypeGrayed: Boolean
        ): LookupElement? {
            error("Should not be called")
        }

        override fun createStandardLookupElementsForDescriptor(
            descriptor: DeclarationDescriptor,
            useReceiverTypes: Boolean
        ): Collection<LookupElement> {
            return baseFactory.createStandardLookupElementsForDescriptor(descriptor, useReceiverTypes).also {
                it.forEach { element ->
                    element.isDslMember = true
                }
            }
        }
    }


    // TODO: only concrete dsl
    fun completeDslFunctions() {
        dslReceiver ?: return

        val annotationShortNames = project.service<DslMarkerAnnotationsCache>().dslMarkerAnnotationFqNames.mapTo(HashSet()) {
            it.shortName()
        }
        if (annotationShortNames.isEmpty()) return

        indicesHelper.getCallableTopLevelExtensions(
            nameFilter = { prefixMatcher.prefixMatches(it) },
            declarationFilter = {
                (it as KtModifierListOwner).modifierList?.collectAnnotationEntriesFromStubOrPsi()?.any { it.shortName in annotationShortNames }
                        ?: false
            },
            callTypeAndReceiver = callTypeAndReceiver,
            receiverTypes = listOf(dslReceiver.type)
        ).forEach { descriptor: DeclarationDescriptor ->
            collector.addDescriptorElements(descriptor, factory, notImported = true, withReceiverCast = false)
        }

        collector.flushToResultSet()
    }

}
