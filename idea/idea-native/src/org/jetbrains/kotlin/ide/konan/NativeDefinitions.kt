/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ide.konan

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.*
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.tree.*
import org.jetbrains.kotlin.ide.konan.psi.*
import javax.swing.Icon
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.konan.library.KDEFINITIONS_FILE_EXTENSION
import java.io.Reader


const val NATIVE_DEFINITIONS_NAME = "KND"
const val NATIVE_DEFINITIONS_DESCRIPTION = "Definitions file for Kotlin/Native C interop"

class NativeDefinitionsFileType : LanguageFileType(NativeDefinitionsLanguage.INSTANCE) {

    override fun getName(): String = NATIVE_DEFINITIONS_NAME

    override fun getDescription(): String = NATIVE_DEFINITIONS_DESCRIPTION

    override fun getDefaultExtension(): String = KDEFINITIONS_FILE_EXTENSION

    override fun getIcon(): Icon = KotlinIcons.NATIVE

    companion object {
        val INSTANCE = NativeDefinitionsFileType()
    }
}

class NativeDefinitionsLanguage private constructor() : Language(NATIVE_DEFINITIONS_NAME) {
    companion object {
        val INSTANCE = NativeDefinitionsLanguage()
    }
}

class NativeDefinitionsFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NativeDefinitionsLanguage.INSTANCE) {

    override fun getFileType(): FileType = NativeDefinitionsFileType.INSTANCE

    override fun toString(): String = NATIVE_DEFINITIONS_DESCRIPTION

    override fun getIcon(flags: Int): Icon? = super.getIcon(flags)
}

class NativeDefinitionsLexerAdapter : FlexAdapter(NativeDefinitionsLexer(null as Reader?))

class NativeDefinitionsParserDefinition : ParserDefinition {
    private val COMMENTS = TokenSet.create(NativeDefinitionsTypes.COMMENT)
    private val FILE = IFileElementType(NativeDefinitionsLanguage.INSTANCE)

    override fun getWhitespaceTokens(): TokenSet = TokenSet.WHITE_SPACE
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY
    override fun getFileNodeType(): IFileElementType = FILE

    override fun createLexer(project: Project): Lexer = NativeDefinitionsLexerAdapter()
    override fun createParser(project: Project): PsiParser = NativeDefinitionsParser()

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NativeDefinitionsFile(viewProvider)
    override fun createElement(node: ASTNode): PsiElement = NativeDefinitionsTypes.Factory.createElement(node)

    @Suppress("DEPRECATE") // Just switch to correctly named function, when old one is removed.
    override fun spaceExistanceTypeBetweenTokens(left: ASTNode?, right: ASTNode?): ParserDefinition.SpaceRequirements =
        ParserDefinition.SpaceRequirements.MAY
}

class CLanguageInjector : LanguageInjector {
    val cLanguage = Language.findLanguageByID("ObjectiveC")

    override fun getLanguagesToInject(host: PsiLanguageInjectionHost, registrar: InjectedLanguagePlaces) {
        if (!host.isValid) return

        if (host is NativeDefinitionsCodeImpl && cLanguage != null) {
            val range = host.getTextRange().shiftLeft(host.startOffsetInParent)
            registrar.addPlace(cLanguage, range, null, null)
        }
    }
}

class NativeDefinitionsSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when (tokenType) {
            TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS
            NativeDefinitionsTypes.COMMENT -> COMMENT_KEYS
            NativeDefinitionsTypes.DELIM -> COMMENT_KEYS
            NativeDefinitionsTypes.KEY_KNOWN -> KEYWORD_KEYS
            NativeDefinitionsTypes.KEY_UNKNOWN -> BAD_CHAR_KEYS
            NativeDefinitionsTypes.SEPARATOR -> OPERATOR_KEYS
            NativeDefinitionsTypes.VALUE -> VALUE_KEYS
            else -> EMPTY_KEYS
        }

    override fun getHighlightingLexer(): Lexer = NativeDefinitionsLexerAdapter()

    companion object {
        private fun createKeys(externalName: String, key: TextAttributesKey): Array<TextAttributesKey> {
            return arrayOf(TextAttributesKey.createTextAttributesKey(externalName, key))
        }

        val BAD_CHAR_KEYS = createKeys("Unknown key", HighlighterColors.BAD_CHARACTER)
        val COMMENT_KEYS = createKeys("Comment", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val EMPTY_KEYS = emptyArray<TextAttributesKey>()
        val KEYWORD_KEYS = createKeys("Known key", DefaultLanguageHighlighterColors.KEYWORD)
        val OPERATOR_KEYS = createKeys("Operator", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val VALUE_KEYS = createKeys("Value", DefaultLanguageHighlighterColors.STRING)
    }
}

class NativeDefinitionsSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        NativeDefinitionsSyntaxHighlighter()
}