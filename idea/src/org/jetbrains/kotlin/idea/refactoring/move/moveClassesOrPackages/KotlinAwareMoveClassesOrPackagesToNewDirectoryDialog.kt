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

package org.jetbrains.kotlin.idea.refactoring.move.moveClassesOrPackages

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.MoveDestination
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesToNewDirectoryDialog
import org.jetbrains.kotlin.idea.refactoring.move.logMoveEventToFus
import org.jetbrains.kotlin.psi.KtClassOrObject

class KotlinAwareMoveClassesOrPackagesToNewDirectoryDialog(
        directory: PsiDirectory,
        elementsToMove: Array<out PsiElement>,
        moveCallback: MoveCallback?
) : MoveClassesOrPackagesToNewDirectoryDialog(directory, elementsToMove, moveCallback) {

    lateinit var x: KtClassOrObject

    val logInfo = if (elementsToMove[0] is PsiClass)
        "MoveClassesToNewDirectory"
    else
        "MovePackagesToNewDirectory"

    override fun createDestination(aPackage: PsiPackage, directory: PsiDirectory): MoveDestination? {
        val delegate = super.createDestination(aPackage, directory) ?: return null
        return KotlinAwareDelegatingMoveDestination(delegate, aPackage, directory)
    }

    override fun invokeRefactoring(processor: BaseRefactoringProcessor?) {
        logMoveEventToFus(logInfo)
        super.invokeRefactoring(processor)
    }
}