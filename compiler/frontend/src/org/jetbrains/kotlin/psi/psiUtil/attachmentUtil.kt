/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file: JvmName("AttachmentUtil")

package org.jetbrains.kotlin.psi.psiUtil

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

fun KotlinExceptionWithAttachments.attachFiles(vararg files: PsiFile) = attachFiles(files.toList())

fun KotlinExceptionWithAttachments.attachFiles(files: Collection<PsiFile>) =
    files.fold(this) { e, file ->
        e.withAttachment(file.name, file.text)
    }

fun KotlinExceptionWithAttachments.attachElement(element: PsiElement?) =
    when {
        element != null -> {
            val attachmentName = "${element.javaClass.simpleName}${if (element is PsiNamedElement) "(name=${element.name})" else ""}"
            withAttachment(
                name = attachmentName,
                content = element.getElementTextWithContext()
            )
        }
        else -> withAttachment("element is null", "")
    }

