/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.parsing

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl.Companion.DEFAULT
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.stubs.elements.StubIndexService
import kotlin.reflect.KFunction1

class KotlinParser : PsiParser {
    override fun parse(iElementType: IElementType, psiBuilder: PsiBuilder) = throw IllegalStateException("use another parse")

    // we need this method because we need psiFile
    fun parse(iElementType: IElementType?, psiBuilder: PsiBuilder, chameleon: ASTNode, psiFile: PsiFile): ASTNode {
        val languageVersionSettings = getLanguageVersionSettings(chameleon.psi.containingFile as? KtFile)
        val ktParsing = KotlinParsing.createForTopLevel(SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder), languageVersionSettings)
        val extension = FileUtilRt.getExtension(psiFile.name)

        if (extension.isEmpty() || extension == KotlinFileType.EXTENSION || psiFile is KtFile && psiFile.isCompiled) {
            ktParsing.parseFile()
        } else {
            ktParsing.parseScript()
        }

        return psiBuilder.treeBuilt
    }

    companion object {
        private fun getLanguageVersionSettings(ktFile: KtFile?): LanguageVersionSettings {
            if (ktFile == null) return DEFAULT

            return StubIndexService.getInstance().getLanguageVersionSettings(ktFile) ?: DEFAULT
        }

        private fun parseFragment(psiBuilder: PsiBuilder, chameleon: ASTNode, parse: KFunction1<KotlinParsing, Unit>): ASTNode {
            val languageVersionSettings = getLanguageVersionSettings(chameleon.psi.containingFile as? KtFile)
            val kotlinParsing = KotlinParsing.createForTopLevel(SemanticWhitespaceAwarePsiBuilderImpl(psiBuilder), languageVersionSettings)

            parse(kotlinParsing)

            return psiBuilder.treeBuilt
        }

        @JvmStatic
        fun parseTypeCodeFragment(psiBuilder: PsiBuilder, chameleon: ASTNode) = parseFragment(
            psiBuilder,
            chameleon,
            KotlinParsing::parseTypeCodeFragment
        )

        @JvmStatic
        fun parseExpressionCodeFragment(psiBuilder: PsiBuilder, chameleon: ASTNode) = parseFragment(
            psiBuilder,
            chameleon,
            KotlinParsing::parseExpressionCodeFragment
        )

        @JvmStatic
        fun parseBlockCodeFragment(psiBuilder: PsiBuilder, chameleon: ASTNode) = parseFragment(
            psiBuilder,
            chameleon,
            KotlinParsing::parseBlockCodeFragment
        )

        @JvmStatic
        fun parseLambdaExpression(psiBuilder: PsiBuilder, chameleon: ASTNode) = parseFragment(
            psiBuilder,
            chameleon,
            KotlinParsing::parseLambdaExpression
        )

        @JvmStatic
        fun parseBlockExpression(psiBuilder: PsiBuilder, chameleon: ASTNode) = parseFragment(
            psiBuilder,
            chameleon,
            KotlinParsing::parseBlockExpression
        )
    }
}