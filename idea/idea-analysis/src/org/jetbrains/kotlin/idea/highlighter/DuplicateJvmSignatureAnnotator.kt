/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.FilteredJvmDiagnostics
import org.jetbrains.kotlin.asJava.builder.InvalidLightClassDataHolder
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.caches.project.getModuleInfo
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector
import org.jetbrains.kotlin.idea.util.ProjectRootsUtil
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform

class DuplicateJvmSignatureAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is KtFile && element !is KtDeclaration) return
        if (!ProjectRootsUtil.isInProjectSource(element)) return

        val file = element.containingFile
        if (file !is KtFile || TargetPlatformDetector.getPlatform(file) !== JvmPlatform) return

        val otherDiagnostics = when (element) {
            is KtDeclaration -> element.analyzeWithContent()
            is KtFile -> element.analyzeWithContent()
            else -> throw AssertionError("DuplicateJvmSignatureAnnotator: should not get here! Element: ${element.text}")
        }.diagnostics

        val moduleScope = element.getModuleInfo().contentScope()
        val diagnostics = getJvmSignatureDiagnostics(element, otherDiagnostics, moduleScope) ?: return

        KotlinPsiChecker().annotateElement(element, holder, diagnostics)
    }
}

private fun getJvmSignatureDiagnostics(element: PsiElement, otherDiagnostics: Diagnostics, moduleScope: GlobalSearchScope): Diagnostics? {
    fun getDiagnosticsForFileFacade(file: KtFile): Diagnostics? {
        val project = file.project
        val cache = KtLightClassForFacade.FacadeStubCache.getInstance(project)
        val facadeFqName = JvmFileClassUtil.getFileClassInfoNoResolve(file).facadeClassFqName
        return cache[facadeFqName, moduleScope].value?.extraDiagnostics
    }

    fun getDiagnosticsForClass(ktClassOrObject: KtClassOrObject): Diagnostics {
        val lightClassDataHolder = KtLightClassForSourceDeclaration.getLightClassDataHolder(ktClassOrObject)
        if (lightClassDataHolder is InvalidLightClassDataHolder) {
            return Diagnostics.EMPTY
        }
        return lightClassDataHolder.extraDiagnostics
    }

    fun doGetDiagnostics(): Diagnostics? {
        //TODO: enable this diagnostic when light classes for scripts are ready
        if ((element.containingFile as? KtFile)?.isScript() == true) return null

        var parent = element.parent
        if (element is KtPropertyAccessor) {
            parent = parent?.parent
        }
        if (element is KtParameter && element.hasValOrVar()) {
            // property declared in constructor
            val parentClass = (parent?.parent?.parent as? KtClass)
            if (parentClass != null) {
                return getDiagnosticsForClass(parentClass)
            }
        }
        if (element is KtClassOrObject) {
            return getDiagnosticsForClass(element)
        }

        when (parent) {
            is KtFile -> {
                return getDiagnosticsForFileFacade(parent)
            }
            is KtClassBody -> {
                val parentsParent = parent.getParent()

                if (parentsParent is KtClassOrObject) {
                    return getDiagnosticsForClass(parentsParent)
                }
            }
        }
        return null
    }

    val result = doGetDiagnostics()
    if (result == null) return null

    return FilteredJvmDiagnostics(result, otherDiagnostics)
}