/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.kotlin.descriptors.impl.LocalVariableDescriptor
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
import org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix
import org.jetbrains.kotlin.idea.quickfix.moveCaretToEnd
import org.jetbrains.kotlin.idea.refactoring.isConstructorDeclaredProperty
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.isDotReceiver
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.TypeUtils

class UnsafeNotNullAssertionOnReallyNullableInspection : AbstractKotlinInspection() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return postfixExpressionVisitor(fun(expression) {
            if (expression.operationToken != KtTokens.EXCLEXCL) return
            val context = expression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
            if (context.diagnostics.forElement(expression.operationReference)
                    .any { it.factory == Errors.UNNECESSARY_NOT_NULL_ASSERTION }
            ) return
            val base = expression.baseExpression
            if (base is KtNameReferenceExpression) {
                registerProblemWithFixes(base, context, holder, expression)
            }
        })
    }

    private fun registerProblemWithFixes(
        base: KtNameReferenceExpression,
        context: BindingContext,
        holder: ProblemsHolder,
        expression: KtPostfixExpression
    ) {
        when (val resolvedReference = base.mainReference.resolve()) {
            is KtParameter -> {
                if (resolvedReference.isConstructorDeclaredProperty()) return
                val baseExpressionDescriptor = context[BindingContext.VALUE_PARAMETER, resolvedReference]
                if (baseExpressionDescriptor is ValueParameterDescriptorImpl) {
                    val fix = ChangeParameterTypeFix(
                        resolvedReference,
                        TypeUtils.makeNotNullable(baseExpressionDescriptor.returnType)
                    )
                    holder.registerProblem(
                        expression, inspectionDescription,
                        ProblemHighlightType.WEAK_WARNING,
                        WrapperWithEmbeddedFixBeforeMainFix(fix, expression.createSmartPointer(), resolvedReference.containingFile),
                        AddSaveCallAndElvisFix()
                    )
                }
            }
            is KtProperty -> {
                if (resolvedReference.isMember) return
                val baseRawDescriptor = context[BindingContext.REFERENCE_TARGET, base]
                val basePropertyDescriptor = baseRawDescriptor as? LocalVariableDescriptor ?: return
                val declaration = resolvedReference as KtVariableDeclaration
                val fix = ChangeVariableTypeFix.OnType(
                    declaration,
                    TypeUtils.makeNotNullable(basePropertyDescriptor.returnType)
                )
                holder.registerProblem(
                    expression, inspectionDescription,
                    ProblemHighlightType.WEAK_WARNING,
                    WrapperWithEmbeddedFixBeforeMainFix(fix, expression.createSmartPointer(), resolvedReference.containingFile),
                    AddSaveCallAndElvisFix()
                )
            }
            else -> return
        }
    }

    private class WrapperWithEmbeddedFixBeforeMainFix(
        intention: IntentionAction,
        val postfixPointer: SmartPsiElementPointer<KtPostfixExpression>,
        file: PsiFile
    ) : IntentionWrapper(intention, file) {
        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            runWriteAction {
                if (postfixPointer.element != null)
                    if (FileModificationService.getInstance().prepareFileForWrite(file)) {
                        val expression =
                            KtPsiFactory(project).createExpression(postfixPointer.element!!.baseExpression!!.text)
                        postfixPointer.element!!.replace(expression)
                    }
            }
            super.invoke(project, editor, file)
        }
    }

    companion object {
        const val inspectionDescription: String = "Unsafe using of '!!' operator"
    }
}

private class AddSaveCallAndElvisFix : LocalQuickFix {
    override fun getName() = "Replace !! with safe call and elvis"

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        applyFix(project, descriptor.psiElement as? KtPostfixExpression ?: return)
    }

    private fun applyFix(project: Project, expression: KtPostfixExpression) {
        if (!FileModificationService.getInstance().preparePsiElementForWrite(expression)) return
        val editor = expression.findExistingEditor()
        if (expression.isDotReceiver()) {
            val parentExpression = expression.parent as? KtDotQualifiedExpression ?: return
            parentExpression.replaced(KtPsiFactory(parentExpression).buildExpression {
                appendExpression(expression.baseExpression)
                appendFixedText("?.")
                appendExpression(parentExpression.selectorExpression)
                appendFixedText("?:")
            }).moveCaretToEnd(editor, project)
            return
        }
        expression.replaced(KtPsiFactory(expression).buildExpression {
            appendExpression(expression.baseExpression)
            appendFixedText("?:")
        }).moveCaretToEnd(editor, project)
    }
}