/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ide.konan

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.konan.library.KDEFINITIONS_FILE_EXTENSION
import java.io.Reader
import javax.swing.Icon
import org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsTypes
import org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsFile



const val NATIVE_DEFINITIONS_NAME = "Kotlin/Native Definitions"
const val NATIVE_DEFINITIONS_DESCRIPTION = "Definition file for Kotlin/Native C interop"

class NativeDefinitionsFileType : LanguageFileType(NativeDefinitionsLanguage.INSTANCE) {
    companion object {
        val INSTANCE = NativeDefinitionsFileType()
    }

    override fun getName(): String = NATIVE_DEFINITIONS_NAME

    override fun getDescription(): String = NATIVE_DEFINITIONS_DESCRIPTION

    override fun getDefaultExtension(): String = KDEFINITIONS_FILE_EXTENSION

    override fun getIcon(): Icon = KotlinIcons.NATIVE
}

class NativeDefinitionsLanguage private constructor() : Language(NATIVE_DEFINITIONS_NAME) {
    companion object {
        val INSTANCE = NativeDefinitionsLanguage()
    }
}

class NativeDefinitionsLexerAdapter : FlexAdapter(NativeDefinitionsLexer(null as Reader?))

class NativeDefinitionsParserDefinition : ParserDefinition {

    override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRING_LITERALS

    override fun getFileNodeType(): IFileElementType = FILE

    override fun createLexer(project: Project): Lexer {
        return NativeDefinitionsLexerAdapter()
    }

    override fun createParser(project: Project): PsiParser {
        return NativeDefinitionsParser()
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return NativeDefinitionsFile(viewProvider)
    }

    override fun spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements {
        return ParserDefinition.SpaceRequirements.MAY
    }

    override fun createElement(node: ASTNode): PsiElement {
        return NativeDefinitionsTypes.Factory.createElement(node)
    }

    companion object {
        val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
        val COMMENTS = TokenSet.create(NativeDefinitionsTypes.COMMENT)
        val STRING_LITERALS = TokenSet.create(NativeDefinitionsTypes.C_STRING_LITERAL)

        val FILE = IFileElementType(NativeDefinitionsLanguage.INSTANCE)
    }
}