/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.refactoring

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler
import com.intellij.testFramework.LightPlatformCodeInsightTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import junit.framework.TestCase
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler
import org.jetbrains.kotlin.idea.refactoring.rename.RenameKotlinImplicitLambdaParameter
import org.jetbrains.kotlin.idea.refactoring.rename.findElementForRename
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import kotlin.test.assertTrue

abstract class AbstractInplaceRenameTest : LightPlatformCodeInsightTestCase() {
    override fun isRunInWriteAction(): Boolean = false
    override fun getTestDataPath(): String = ""

    fun doTest(path: String) {
        configureByFile(path)
        val nameDirective = InTextDirectivesUtils.findStringWithPrefixes(myFile.text, "NAME:")
        val newName = (nameDirective ?: error("`NAME:` directive is absent ")).let {
            if (it == "EMPTY_STRING") "" else it
        }

        val isLambdaTest = InTextDirectivesUtils.isDirectiveDefined(myFile.text, "LAMBDA")
        val expectedMessage = InTextDirectivesUtils.findStringWithPrefixes(myFile.text, "// SHOULD_FAIL_WITH: ")
        try {
            if (isLambdaTest) {
                doTestImplicitLambdaParameter(newName)
            } else {
                doTestInplaceRename(newName)
            }
            if (expectedMessage != null) {
                TestCase.fail("Refactoring completed without expected conflicts:\n$expectedMessage")
            }
            checkResultByFile("$path.after")
        } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
            TestCase.assertEquals(expectedMessage, e.messages.joinToString())
        }
    }

    private fun doTestImplicitLambdaParameter(newName: String) {
        // This code is copy-pasted from CodeInsightTestUtil.doInlineRename() and slightly modified.
        // Original method was not suitable because it expects renamed element to be reference to other or referrable

        val file = getFile()!!
        val editor = getEditor()!!
        val element = file.findElementForRename<KtNameReferenceExpression>(editor.caretModel.offset)!!
        assertNotNull(element)

        val dataContext = SimpleDataContext.getSimpleContext(
            CommonDataKeys.PSI_ELEMENT.name, element,
            getCurrentEditorDataContext()
        )
        val handler = RenameKotlinImplicitLambdaParameter()

        assertTrue(handler.isRenaming(dataContext), "In-place rename not allowed for " + element)

        val project = editor.project!!

        TemplateManagerImpl.setTemplateTesting(project, testRootDisposable)

        object : WriteCommandAction.Simple<Any>(project) {
            override fun run() {
                handler.invoke(project, editor, file, dataContext)
            }
        }.execute()

        var state = TemplateManagerImpl.getTemplateState(editor)
        assert(state != null)
        val range = state!!.currentVariableRange
        assert(range != null)
        object : WriteCommandAction.Simple<Any>(project) {
            override fun run() {
                editor.document.replaceString(range!!.startOffset, range.endOffset, newName)
            }
        }.execute().throwException()

        state = TemplateManagerImpl.getTemplateState(editor)
        assert(state != null)
        state!!.gotoEnd(false)
    }

    private fun doTestInplaceRename(newName: String) {
        val element = TargetElementUtil.findTargetElement(
            LightPlatformCodeInsightTestCase.myEditor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED or TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
        )
        assertNotNull(element)
        val dataContext = SimpleDataContext.getSimpleContext(
            CommonDataKeys.PSI_ELEMENT.name, element!!,
            LightPlatformCodeInsightTestCase.getCurrentEditorDataContext()
        )

        val handler = KotlinRenameDispatcherHandler().getRenameHandler(dataContext)!!

        assertTrue(handler.isRenaming(dataContext), "In-place rename not allowed for " + element)
        CodeInsightTestUtil.doInlineRename(
            handler as VariableInplaceRenameHandler,
            newName,
            LightPlatformCodeInsightTestCase.getEditor(), element
        )
    }
}
