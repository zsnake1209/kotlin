/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scratch.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.idea.scratch.output.highlightLines

abstract class ScratchEditorSyncHighlighter(private val sourceEditor: EditorEx, private val previewEditor: EditorEx) : Disposable {
    private val sourceHighlighter = EditorLinesHighlighter(sourceEditor)
    private val previewEditorHighlighter = EditorLinesHighlighter(previewEditor)

    init {
        configureExclusiveCaretRowHighlighting()
        configureHighlightUpdateOnDocumentChange()
        configureSourceToPreviewHighlighting()
        configurePreviewToSourceHighlighting()
    }

    override fun dispose() {}

    abstract fun translatePreviewLineToSourceLines(line: Int): Pair<Int, Int>?
    abstract fun translateSourceLineToPreviewLines(line: Int): Pair<Int, Int>?

    /**
     * Configures editors such that only one of them have caret row highlighting enabled.
     */
    private fun configureExclusiveCaretRowHighlighting() {
        val exclusiveCaretHighlightingListener = object : FocusChangeListener {
            override fun focusLost(editor: Editor) {}

            override fun focusGained(editor: Editor) {
                sourceEditor.settings.isCaretRowShown = false
                previewEditor.settings.isCaretRowShown = false

                editor.settings.isCaretRowShown = true
            }
        }

        sourceEditor.addFocusListener(exclusiveCaretHighlightingListener, this)
        previewEditor.addFocusListener(exclusiveCaretHighlightingListener, this)
    }

    /**
     * When source or preview documents change, we need to update highlighting, because
     * expression output may become bigger.
     *
     * We can do that only when document is fully committed, so [ScratchFile.getExpressions] will return correct expressions
     * with correct PSIs.
     */
    private fun configureHighlightUpdateOnDocumentChange() {
        val focusedEditorKeeper = object : FocusChangeListener {
            /**
             * Read and write only from EDT, no synchronization or volatile is needed.
             */
            var lastFocusedEditor: Editor = sourceEditor

            override fun focusLost(editor: Editor) {}

            override fun focusGained(editor: Editor) {
                lastFocusedEditor = editor
            }
        }

        sourceEditor.addFocusListener(focusedEditorKeeper, this)
        previewEditor.addFocusListener(focusedEditorKeeper, this)

        val updateHighlightOnDocumentChangeListener = object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                clearAllHighlights()

                val lastFocusedEditor = focusedEditorKeeper.lastFocusedEditor
                val singleCaret = lastFocusedEditor.caretModel.allCarets.singleOrNull() ?: return

                PsiDocumentManager.getInstance(sourceEditor.project!!).performWhenAllCommitted {
                    if (lastFocusedEditor === sourceEditor) {
                        highlightPreviewBySourceLine(singleCaret.logicalPosition.line)
                    } else {
                        highlightSourceByPreviewLine(singleCaret.logicalPosition.line)
                    }
                }
            }
        }

        previewEditor.document.addDocumentListener(updateHighlightOnDocumentChangeListener, this)
        sourceEditor.document.addDocumentListener(updateHighlightOnDocumentChangeListener, this)
    }

    /**
     * When caret in [sourceEditor] is moved, highlight is recalculated.
     *
     * When focus is switched to the [sourceEditor], highlight is recalculated,
     * because it is possible to switch focus without changing cursor position,
     * which would lead to the outdated highlighting.
     */
    private fun configureSourceToPreviewHighlighting() {
        sourceEditor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                clearAllHighlights()

                highlightPreviewBySourceLine(event.newPosition.line)
            }
        }, this)

        sourceEditor.addFocusListener(object : FocusChangeListener {
            override fun focusLost(editor: Editor) {}

            override fun focusGained(editor: Editor) {
                clearAllHighlights()

                val singleCaret = sourceEditor.caretModel.allCarets.singleOrNull() ?: return
                highlightPreviewBySourceLine(singleCaret.logicalPosition.line)
            }
        }, this)
    }

    /**
     * When caret in [previewEditor] is moved, highlight is recalculated.
     *
     * When focus is switched to the [previewEditor], highlight is recalculated,
     * because it is possible to switch focus without changing cursor position,
     * which would lead to the outdated highlighting.
     */
    private fun configurePreviewToSourceHighlighting() {
        previewEditor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                clearAllHighlights()

                highlightSourceByPreviewLine(event.newPosition.line)
            }
        }, this)

        previewEditor.addFocusListener(object : FocusChangeListener {
            override fun focusLost(editor: Editor) {}

            override fun focusGained(editor: Editor) {
                clearAllHighlights()

                val singleCaret = previewEditor.caretModel.allCarets.singleOrNull() ?: return
                highlightSourceByPreviewLine(singleCaret.logicalPosition.line)
            }
        }, this)
    }

    private fun highlightSourceByPreviewLine(selectedPreviewLine: Int) {
        val (from, to) = translateSourceLineToPreviewLines(selectedPreviewLine) ?: return

        sourceHighlighter.highlightLines(from, to)
    }

    private fun highlightPreviewBySourceLine(selectedSourceLine: Int) {
        val (from, to) = translatePreviewLineToSourceLines(selectedSourceLine) ?: return

        previewEditorHighlighter.highlightLines(from, to)
    }

    private fun clearAllHighlights() {
        sourceHighlighter.clearHighlights()
        previewEditorHighlighter.clearHighlights()
    }
}

private class EditorLinesHighlighter(private val targetEditor: Editor) {
    private var activeHighlight: RangeHighlighter? = null

    fun clearHighlights() {
        activeHighlight?.let(targetEditor.markupModel::removeHighlighter)
        activeHighlight = null
    }

    fun highlightLines(lineStart: Int, lineEnd: Int) {
        clearHighlights()

        val highlightColor = targetEditor.colorsScheme.getColor(EditorColors.CARET_ROW_COLOR) ?: return

        activeHighlight = targetEditor.markupModel.highlightLines(
            lineStart,
            lineEnd,
            TextAttributes().apply { backgroundColor = highlightColor },
            HighlighterTargetArea.LINES_IN_RANGE
        )
    }
}
