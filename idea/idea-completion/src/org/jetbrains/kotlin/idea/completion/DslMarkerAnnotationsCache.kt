/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.completion

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.jetbrains.kotlin.idea.caches.resolve.LibraryModificationTracker
import org.jetbrains.kotlin.idea.refactoring.fqName.getKotlinFqName
import org.jetbrains.kotlin.idea.search.ideaExtensions.KotlinAnnotatedElementsSearcher
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.calls.DslMarkerUtils
import org.jetbrains.kotlin.utils.addIfNotNull

class DslMarkerAnnotationsCache(private val project: Project) {

    val dslMarkerAnnotationFqNames: Collection<FqName>
        get() = cachedDslMarkers.value

    private val cachedDslMarkers = CachedValuesManager.getManager(project).createCachedValue(
        {
            CachedValueProvider.Result(
                computeMarkerAnnotationFqNames(),
                LibraryModificationTracker.getInstance(project),
                ProjectRootModificationTracker.getInstance(project)
            )
        }, false
    )

    private fun computeMarkerAnnotationFqNames(): Collection<FqName> {
        val everythingGlobalScope = EverythingGlobalScope(project)
        val dslMarkerClass = JavaPsiFacade.getInstance(project)
            .findClass(DslMarkerUtils.DSL_MARKER_FQ_NAME.asString(), everythingGlobalScope) ?: return emptyList()
        val result = mutableSetOf<FqName>()
        KotlinAnnotatedElementsSearcher.processAnnotatedMembers(
            dslMarkerClass,
            everythingGlobalScope,
            preFilter = {
                it.annotatesAnnotationClass()
            },
            consumer = { declaration ->
                declaration.takeIf { it is KtClassOrObject && it.isAnnotation() }?.let {
                    result.addIfNotNull(it.getKotlinFqName())
                }
                true
            }
        )
        return result
    }

    private fun KtAnnotationEntry.annotatesAnnotationClass() = getNonStrictParentOfType<KtClassOrObject>()?.isAnnotation() ?: false
}