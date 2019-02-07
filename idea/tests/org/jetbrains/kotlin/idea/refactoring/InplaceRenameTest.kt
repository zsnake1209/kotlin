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
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.test.InTextDirectivesUtils
import kotlin.test.assertTrue

class InplaceRenameTest : LightPlatformCodeInsightTestCase() {
    override fun isRunInWriteAction(): Boolean = false
    override fun getTestDataPath(): String = PluginTestCaseBase.getTestDataPathBase() + "/refactoring/rename/inplace/"

    fun testLocalVal() {
        doTestInplaceRename("y")
    }

    fun testForLoop() {
        doTestInplaceRename("j")
    }

    fun testTryCatch() {
        doTestInplaceRename("e1")
    }

    fun testFunctionLiteral() {
        doTestInplaceRename("y")
    }

    fun testFunctionLiteralIt() {
        doTestImplicitLambdaParameter("y")
    }

    fun testFunctionLiteralItEndCaret() {
        doTestImplicitLambdaParameter("y")
    }

    fun testFunctionLiteralParenthesis() {
        doTestInplaceRename("y")
    }

    fun testLocalFunction() {
        doTestInplaceRename("bar")
    }

    fun testLabelFromFunction() {
        doTestInplaceRename("foo")
    }

    fun testMultiDeclaration() {
        doTestInplaceRename("foo")
    }

    fun testLocalVarShadowingMemberProperty() {
        doTestInplaceRename("name1")
    }

    fun testLocalRenameNoOptimizeImports() {
        doTestInplaceRename("y")
    }

    fun testMemberRenameOptimizesImports() {
        doTestInplaceRename("y")
    }

    fun testNoReformat() {
        doTestInplaceRename("subject2")
    }

    fun testInvokeToFoo() {
        doTestInplaceRename("foo")
    }

    fun testInvokeToGet() {
        doTestInplaceRename("get")
    }

    fun testInvokeToGetWithQualifiedExpr() {
        doTestInplaceRename("get")
    }

    fun testInvokeToGetWithSafeQualifiedExpr() {
        doTestInplaceRename("get")
    }

    fun testInvokeToPlus() {
        doTestInplaceRename("plus")
    }

    fun testGetToFoo() {
        doTestInplaceRename("foo")
    }

    fun testGetToInvoke() {
        doTestInplaceRename("invoke")
    }

    fun testGetToInvokeWithQualifiedExpr() {
        doTestInplaceRename("invoke")
    }

    fun testGetToInvokeWithSafeQualifiedExpr() {
        doTestInplaceRename("invoke")
    }

    fun testGetToPlus() {
        doTestInplaceRename("plus")
    }

    fun testAddQuotes() {
        doTestInplaceRename("is")
    }

    fun testAddThis() {
        doTestInplaceRename("foo")
    }

    fun testExtensionAndNoReceiver() {
        doTestInplaceRename("b")
    }

    fun testTwoExtensions() {
        doTestInplaceRename("example")
    }

    fun testQuotedLocalVar() {
        doTestInplaceRename("x")
    }

    fun testQuotedParameter() {
        doTestInplaceRename("x")
    }

    fun testEraseCompanionName() {
        doTestInplaceRename("")
    }

    fun testLocalVarRedeclaration() {
        doTestInplaceRename("localValB")
    }

    fun testLocalFunRedeclaration() {
        doTestInplaceRename("localFunB")
    }

    fun testLocalClassRedeclaration() {
        doTestInplaceRename("LocalClassB")
    }

    fun testBacktickedWithAccessors() {
        doTestInplaceRename("`object`")
    }

    fun testNoTextUsagesForLocalVar() {
        doTestInplaceRename("w")
    }

    private fun doTestImplicitLambdaParameter(newName: String) {
        configureByFile(getTestName(false) + ".kt")

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

        checkResultByFile(getTestName(false) + ".kt.after")
    }

    private fun doTestInplaceRename(newName: String) {
        configureByFile(getTestName(false) + ".kt")
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

        val expectedMessage = InTextDirectivesUtils.findStringWithPrefixes(myFile.text, "// SHOULD_FAIL_WITH: ")
        try {
            assertTrue(handler.isRenaming(dataContext), "In-place rename not allowed for " + element)
            CodeInsightTestUtil.doInlineRename(
                handler as VariableInplaceRenameHandler,
                newName,
                LightPlatformCodeInsightTestCase.getEditor(), element
            )
            if (expectedMessage != null) {
                TestCase.fail("Refactoring completed without expected conflicts:\n$expectedMessage")
            }
            checkResultByFile(getTestName(false) + ".kt.after")
        } catch (e: BaseRefactoringProcessor.ConflictsInTestsException) {
            TestCase.assertEquals(expectedMessage, e.messages.joinToString())
        }
    }
}
