/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.PrefixMatcher
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

// TODO: suppress completion when start of expression only, not inside context, provide completion variants with {}
class DslMembersCompletion(
    private val project: Project,
    private val prefixMatcher: PrefixMatcher,
    private val baseFactory: LookupElementFactory,
    receiverTypes: Collection<ReceiverType>?,
    private val collector: LookupElementsCollector,
    private val indicesHelper: KotlinIndicesHelper,
    private val callTypeAndReceiver: CallTypeAndReceiver<*, *>
) {
    private val dslReceiver = receiverTypes?.last()?.takeIf { DslMarkerUtils.extractDslMarkerFqNames(it.type).isNotEmpty() && it.implicit }

    fun areMostlyDslsExpected() =
        callTypeAndReceiver.callType == CallType.DEFAULT

    fun completeDslFunctions(): Boolean {
        dslReceiver ?: return false

        var hasResults = false
        val processor = { descriptor: DeclarationDescriptor ->
            collector.addElements(baseFactory.createStandardLookupElementsForDescriptor(descriptor, false))
            hasResults = true
        }
        val annotationShortNames = project.service<DslMarkerAnnotationsCache>().dslMarkerAnnotationFqNames.mapTo(HashSet()) {
            it.shortName()
        }
        indicesHelper.getCallableTopLevelExtensions(
            nameFilter = { prefixMatcher.prefixMatches(it) },
            declarationFilter = {
                (it as KtModifierListOwner).modifierList?.collectAnnotationEntriesFromStubOrPsi()?.any { it.shortName in annotationShortNames }
                        ?: false
            },
            callTypeAndReceiver = callTypeAndReceiver,
            receiverTypes = listOf(dslReceiver.type)
        ).forEach(processor)

        collector.flushToResultSet()
        return hasResults
    }

}
