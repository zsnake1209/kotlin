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

package org.jetbrains.kotlin.idea.actions.internal.benchmark

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.uiDesigner.core.GridLayoutManager
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import org.jetbrains.kotlin.idea.actions.internal.KotlinInternalMode
import org.jetbrains.kotlin.idea.actions.internal.benchmark.AbstractCompletionBenchmarkAction.Companion.addBoxWithLabel
import org.jetbrains.kotlin.idea.actions.internal.benchmark.AbstractCompletionBenchmarkAction.Companion.collectSuitableKotlinFiles
import org.jetbrains.kotlin.idea.actions.internal.benchmark.AbstractCompletionBenchmarkAction.Companion.shuffledSequence
import org.jetbrains.kotlin.idea.core.util.EDT
import org.jetbrains.kotlin.idea.refactoring.getLineCount
import org.jetbrains.kotlin.psi.KtFile
import java.util.*
import javax.swing.JFileChooser
import kotlin.properties.Delegates

class HighlightingBenchmarkAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent?) {
        val project = e?.project ?: return

        val settings = showSettingsDialog() ?: return

        val random = Random(settings.seed)

        fun collectFiles(): List<KtFile>? {

            val ktFiles = collectSuitableKotlinFiles(project, { it.getLineCount() >= settings.lines })

            if (ktFiles.size < settings.files) {
                AbstractCompletionBenchmarkAction.showPopup(project, "Number of attempts > then files in project, ${ktFiles.size}")
                return null
            }

            return ktFiles
        }


        val ktFiles = collectFiles() ?: return

        val results = mutableListOf<Result>()
        launch(EDT) {
            ktFiles
                    .shuffledSequence(random)
                    .take(settings.files)
                    .forEach { file ->
                        results += openFileAndMeasureTimeToHighlight(file, project)
                    }

            saveResults(results, project)
        }
    }

    private data class Settings(val seed: Long, val files: Int, val lines: Int)

    private fun showSettingsDialog(): Settings? {
        var cSeed: JBTextField by Delegates.notNull()
        var cFiles: JBTextField by Delegates.notNull()
        var cLines: JBTextField by Delegates.notNull()
        val dialogBuilder = DialogBuilder()


        val jPanel = JBPanel<JBPanel<*>>(GridLayoutManager(3, 2)).apply {
            var i = 0
            cSeed = addBoxWithLabel("Random seed", default = "0", i = i++)
            cFiles = addBoxWithLabel("Files to visit", default = "20", i = i++)
            cLines = addBoxWithLabel("Minimal line count", default = "100", i = i)
        }
        dialogBuilder.centerPanel(jPanel)
        if (!dialogBuilder.showAndGet()) return null

        return Settings(cSeed.text.toLong(), cFiles.text.toInt(), cLines.text.toInt())
    }

    private sealed class Result(val location: String) {
        abstract fun toCSV(builder: StringBuilder)

        class Success(location: String, val pendingTime: Long, val analysisTime: Long) : Result(location) {
            override fun toCSV(builder: StringBuilder): Unit = with(builder) {
                append(location)
                append(", ")
                append(pendingTime)
                append(", ")
                append(analysisTime)
            }

        }

        class Error(location: String) : Result(location) {
            override fun toCSV(builder: StringBuilder): Unit = with(builder) {
                append(", ")
                append(", ")
            }
        }
    }

    private suspend fun openFileAndMeasureTimeToHighlight(file: KtFile, project: Project): Result {

        NavigationUtil.openFileWithPsiElement(file.navigationElement, false, true)
        val location = file.virtualFile.path

        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return Result.Error(location)

        val editor = EditorFactory.getInstance().getEditors(document, project).firstOrNull() ?: return Result.Error(location)

        val daemon = DaemonCodeAnalyzer.getInstance(project) as DaemonCodeAnalyzerImpl

        if (!daemon.isHighlightingAvailable(file)) return Result.Error(location)

        if (!daemon.isRunningOrPending) return Result.Error(location)

        fun DaemonCodeAnalyzerImpl.isPending() = !isRunning && isRunningOrPending

        val start = System.currentTimeMillis()
        while (daemon.isPending()) {
            delay(1)
        }

        val analysisStart = System.currentTimeMillis()

        while (daemon.isRunning) {
            delay(1)
        }

        val pendingTime = analysisStart - start
        val analysisTime = System.currentTimeMillis() - analysisStart
        return Result.Success(location, pendingTime, analysisTime)
    }


    private fun saveResults(allResults: List<Result>, project: Project) {
        val jfc = JFileChooser()
        val result = jfc.showSaveDialog(null)
        if (result == JFileChooser.APPROVE_OPTION) {
            val file = jfc.selectedFile
            file.writeText(buildString {
                appendln("n, file, pending, full")
                var i = 0
                allResults.forEach {
                    append(i++)
                    append(", ")
                    it.toCSV(this)
                    appendln()
                }
            })
        }
        AbstractCompletionBenchmarkAction.showPopup(project, "Done")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = KotlinInternalMode.enabled
    }
}


