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

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import com.intellij.usageView.UsageInfo
import org.jetbrains.kotlin.asJava.namedUnwrappedElement
import org.jetbrains.kotlin.descriptors.VariableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.core.unquote
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.utils.SmartList

class KotlinMemberInplaceRenameHandler : MemberInplaceRenameHandler() {
    private class RenamerImpl(
        elementToRename: PsiNamedElement,
        substitutedElement: PsiElement?,
        editor: Editor,
        currentName: String,
        oldName: String
    ) : MemberInplaceRenamer(elementToRename, substitutedElement, editor, currentName, oldName) {
        override fun isIdentifier(newName: String?, language: Language?): Boolean {
            if (newName == "" && (variable as? KtObjectDeclaration)?.isCompanion() == true) return true
            return super.isIdentifier(newName, language)
        }

        override fun acceptReference(reference: PsiReference): Boolean {
            val refElement = reference.element
            val textRange = reference.rangeInElement
            val referenceText = refElement.text.substring(textRange.startOffset, textRange.endOffset).unquote()
            return referenceText == myElementToRename.name
        }

        override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement?): Boolean {
            return variable == element && (handler is MemberInplaceRenameHandler || handler is KotlinRenameDispatcherHandler)
        }

        override fun createInplaceRenamerToRestart(variable: PsiNamedElement, editor: Editor, initialName: String): VariableInplaceRenamer {
            return RenamerImpl(variable, substituted, editor, initialName, myOldName)
        }

        override fun createRenameProcessor(element: PsiElement, newName: String): RenameProcessor =
            if (element is KtProperty && element.isLocal)
                LocalVariableRenameProcessor(element, newName)
            else
                MyRenameProcessor(element, newName)

        // TODO: report bug about bad diagnostic without inner
        protected inner class LocalVariableRenameProcessor(
            private val element: PsiElement,
            private val newName: String
        ) : MyRenameProcessor(element, newName) {
            override fun performRefactoring(usages: Array<out UsageInfo>) {
                val hasRedeclations = hasRedeclarations()

                super.performRefactoring(usages)

                if (hasRedeclations) {
                    reportProblems()
                }
            }

            private fun reportProblems() {
                InEditorPopup("Name conflicts with other variable", InEditorPopup.Type.ERROR).show(element.project, myEditor)
            }


            private fun hasRedeclarations(): Boolean {
                val declaration = element.namedUnwrappedElement as? KtNamedDeclaration ?: return false
                val descriptor = declaration.unsafeResolveToDescriptor() as VariableDescriptor
                val collisions = SmartList<UsageInfo>()
                checkRedeclarations(descriptor, newName, collisions)
                return collisions.isNotEmpty()
            }
        }
    }

    private fun PsiElement.substitute(): PsiElement {
        if (this is KtPrimaryConstructor) return getContainingClassOrObject()
        return this
    }

    override fun createMemberRenamer(element: PsiElement, elementToRename: PsiNameIdentifierOwner, editor: Editor): MemberInplaceRenamer {
        val currentElementToRename = elementToRename.substitute() as PsiNameIdentifierOwner
        val nameIdentifier = currentElementToRename.nameIdentifier

        // Move caret if constructor range doesn't intersect with the one of the containing class
        val offset = editor.caretModel.offset
        val editorPsiFile = PsiDocumentManager.getInstance(element.project).getPsiFile(editor.document)
        if (nameIdentifier != null
            && editorPsiFile == elementToRename.containingFile
            && elementToRename is KtPrimaryConstructor
            && offset !in nameIdentifier.textRange
            && offset in elementToRename.textRange
        ) {
            editor.caretModel.moveToOffset(nameIdentifier.textOffset)
        }

        val currentName = nameIdentifier?.text ?: ""
        return RenamerImpl(currentElementToRename, element, editor, currentName, currentName)
    }

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        if (!editor.settings.isVariableInplaceRenameEnabled) return false
        val currentElement = element?.substitute() as? KtNamedDeclaration ?: return false
        return currentElement.nameIdentifier != null && !KotlinVariableInplaceRenameHandler.isInplaceRenameAvailable(currentElement)
    }
}
