/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.actions.FlipCommaIntention.Flipper
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureConfiguration
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinMethodDescriptor
import org.jetbrains.kotlin.idea.refactoring.changeSignature.modify
import org.jetbrains.kotlin.idea.refactoring.changeSignature.runChangeSignature
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.createSmartPointer
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinFlipper : Flipper {
    override fun flip(left: PsiElement?, right: PsiElement?): Boolean {
        if (left == null || right == null) return false

        val parameter = left.safeAs<KtParameter>() ?: return false
        val leftParameterIndex = parameter.parameterIndex()
        val declarationPointer = left.parent?.parent?.safeAs<KtCallableDeclaration>()?.createSmartPointer() ?: return false
        val parameterPointer = parameter.createSmartPointer()

        ApplicationManager.getApplication().invokeLater {
            val defaultValueContext = parameterPointer.element ?: return@invokeLater
            val descriptor = declarationPointer.element?.resolveToDescriptorIfAny()?.safeAs<CallableDescriptor>() ?: return@invokeLater
            runChangeSignature(
                declarationPointer.project,
                descriptor,
                object : KotlinChangeSignatureConfiguration {
                    override fun configure(originalDescriptor: KotlinMethodDescriptor): KotlinMethodDescriptor {
                        return originalDescriptor.modify { descriptor ->
                            val leftParameter = descriptor.parameters[leftParameterIndex]
                            descriptor.parameters[leftParameterIndex] = descriptor.parameters[leftParameterIndex + 1]
                            descriptor.parameters[leftParameterIndex + 1] = leftParameter
                        }
                    }

                    override fun performSilently(affectedFunctions: Collection<PsiElement>) = true
                },
                defaultValueContext,
                "Swap parameters in '${descriptor.name.asString()}'"
            )
        }

        return true
    }
}