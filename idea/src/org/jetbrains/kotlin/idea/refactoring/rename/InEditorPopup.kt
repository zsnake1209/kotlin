/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import com.intellij.util.ui.PositionTracker
import java.awt.Color
import java.awt.Point
import javax.swing.Icon

class InEditorPopup(
    val message: String,
    val type: Type
) {
    enum class Type(val icon: Icon, val background: Color) {
        WARNING(AllIcons.General.BalloonWarning, MessageType.WARNING.popupBackground),
        ERROR(AllIcons.General.BalloonError, MessageType.WARNING.popupBackground)
    }

    fun show(project: Project, editor: Editor) {
        if (ApplicationManager.getApplication().isUnitTestMode)
            throw ConflictsInTestsExceptionWithPopup(this)
        if (ApplicationManager.getApplication().isHeadlessEnvironment)
            return

        val balloonBuilder = JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(
            message, type.icon,
            type.background, null
        )

        balloonBuilder
            .setHideOnClickOutside(true)
            .setHideOnKeyOutside(true)

        val conflictsBalloon = balloonBuilder.createBalloon()
        Disposer.register(project, conflictsBalloon)
        EditorUtil.disposeWithEditor(editor, conflictsBalloon)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        val popupFactory = JBPopupFactory.getInstance()
        conflictsBalloon.show(object : PositionTracker<Balloon>(editor.contentComponent) {
            override fun recalculateLocation(`object`: Balloon): RelativePoint {
                if (!popupFactory.isBestPopupLocationVisible(editor)) {
                    conflictsBalloon.hide()
                }
                val target = popupFactory.guessBestPopupLocation(editor)
                val screenPoint = target.screenPoint
                var y = screenPoint.y
                if (target.point.getY() > editor.lineHeight + conflictsBalloon.preferredSize.getHeight()) {
                    y -= editor.lineHeight
                }
                return RelativePoint(Point(screenPoint.x, y))
            }
        }, Balloon.Position.above)

        Alarm(Alarm.ThreadToUse.SWING_THREAD).addRequest(
            {
                conflictsBalloon.hide()
            }, 4 * 1000
        )

    }
}

class ConflictsInTestsExceptionWithPopup(
    val popup: InEditorPopup
) : BaseRefactoringProcessor.ConflictsInTestsException(listOf(popup.message))

