/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.actions.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import org.jetbrains.kotlin.idea.formatter.KotlinFormatterUtils

class KotlinFormattingSettingsStatusAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val formatterKind = KotlinFormatterUtils.getKotlinFormatterKind(project)

        Messages.showInfoMessage(
            project,
            "formatterKind = $formatterKind",
            "Kotlin Formatter Settings"
        )
    }
}
