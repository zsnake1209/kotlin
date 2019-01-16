/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ide.konan

import com.intellij.lang.*
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.options.colors.*
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.tree.*
import java.io.Reader
import javax.swing.Icon
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.konan.library.KDEFINITIONS_FILE_EXTENSION
import org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsFile
import org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsTypes


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

class NativeDefinitionsLexerAdapter : FlexAdapter(NativeDefinitionsLexer(null as Reader?))

class NativeDefinitionsParserDefinition : ParserDefinition {
    private val COMMENTS = TokenSet.create(NativeDefinitionsTypes.COMMENT)
    private val STRING_LITERALS = TokenSet.create(NativeDefinitionsTypes.C_STRING_LITERAL)
    private val FILE = IFileElementType(NativeDefinitionsLanguage.INSTANCE)

    override fun getWhitespaceTokens(): TokenSet = TokenSet.WHITE_SPACE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRING_LITERALS

    override fun getFileNodeType(): IFileElementType = FILE

    override fun createLexer(project: Project): Lexer = NativeDefinitionsLexerAdapter()

    override fun createParser(project: Project): PsiParser = NativeDefinitionsParser()

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NativeDefinitionsFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements =
        ParserDefinition.SpaceRequirements.MAY

    override fun createElement(node: ASTNode): PsiElement = NativeDefinitionsTypes.Factory.createElement(node)
}

val SET_OF_OPERATORS = hashSetOf(
    NativeDefinitionsTypes.C_ADD_ASSIGN, NativeDefinitionsTypes.C_AND, NativeDefinitionsTypes.C_AND_ASSIGN,
    NativeDefinitionsTypes.C_AND_OP, NativeDefinitionsTypes.C_CARET, NativeDefinitionsTypes.C_COLON,
    NativeDefinitionsTypes.C_DEC_OP, NativeDefinitionsTypes.C_DIV_ASSIGN, NativeDefinitionsTypes.C_EQ_OP,
    NativeDefinitionsTypes.C_EQ_SIGN, NativeDefinitionsTypes.C_EX_MARK, NativeDefinitionsTypes.C_GE_OP,
    NativeDefinitionsTypes.C_GREATER, NativeDefinitionsTypes.C_INC_OP, NativeDefinitionsTypes.C_LE_OP,
    NativeDefinitionsTypes.C_LEFT_ASSIGN, NativeDefinitionsTypes.C_LEFT_OP, NativeDefinitionsTypes.C_LESS,
    NativeDefinitionsTypes.C_MINUS, NativeDefinitionsTypes.C_MOD_ASSIGN, NativeDefinitionsTypes.C_MUL_ASSIGN,
    NativeDefinitionsTypes.C_MULT, NativeDefinitionsTypes.C_NE_OP, NativeDefinitionsTypes.C_OR_ASSIGN,
    NativeDefinitionsTypes.C_OR_OP, NativeDefinitionsTypes.C_PERCENT, NativeDefinitionsTypes.C_PLUS,
    NativeDefinitionsTypes.C_PTR_OP, NativeDefinitionsTypes.C_QU_MARK, NativeDefinitionsTypes.C_RIGHT_ASSIGN,
    NativeDefinitionsTypes.C_RIGHT_OP, NativeDefinitionsTypes.C_SLASH, NativeDefinitionsTypes.C_SUB_ASSIGN,
    NativeDefinitionsTypes.C_TILDE, NativeDefinitionsTypes.C_VBAR, NativeDefinitionsTypes.C_XOR_ASSIGN,
    NativeDefinitionsTypes.DEF_SEPARATOR
)

val SET_OF_C_KEYWORDS = hashSetOf(
    NativeDefinitionsTypes.C_ALIGNAS, NativeDefinitionsTypes.C_ALIGNOF, NativeDefinitionsTypes.C_ATOMIC,
    NativeDefinitionsTypes.C_AUTO, NativeDefinitionsTypes.C_BOOL, NativeDefinitionsTypes.C_BREAK,
    NativeDefinitionsTypes.C_CASE, NativeDefinitionsTypes.C_CHAR, NativeDefinitionsTypes.C_COMPLEX,
    NativeDefinitionsTypes.C_CONST, NativeDefinitionsTypes.C_CONTINUE, NativeDefinitionsTypes.C_DEFAULT,
    NativeDefinitionsTypes.C_DO, NativeDefinitionsTypes.C_DOUBLE, NativeDefinitionsTypes.C_ELSE,
    NativeDefinitionsTypes.C_ENUM, NativeDefinitionsTypes.C_EXTERN, NativeDefinitionsTypes.C_FLOAT,
    NativeDefinitionsTypes.C_FOR, NativeDefinitionsTypes.C_FUNC_NAME, NativeDefinitionsTypes.C_GENERIC,
    NativeDefinitionsTypes.C_GOTO, NativeDefinitionsTypes.C_IF, NativeDefinitionsTypes.C_IMAGINARY,
    NativeDefinitionsTypes.C_INLINE, NativeDefinitionsTypes.C_INT, NativeDefinitionsTypes.C_LONG,
    NativeDefinitionsTypes.C_NORETURN, NativeDefinitionsTypes.C_REGISTER, NativeDefinitionsTypes.C_RESTRICT,
    NativeDefinitionsTypes.C_RETURN, NativeDefinitionsTypes.C_SHORT, NativeDefinitionsTypes.C_SIGNED,
    NativeDefinitionsTypes.C_SIZEOF, NativeDefinitionsTypes.C_STATIC, NativeDefinitionsTypes.C_STATIC_ASSERT,
    NativeDefinitionsTypes.C_STRUCT, NativeDefinitionsTypes.C_SWITCH, NativeDefinitionsTypes.C_THREAD_LOCAL,
    NativeDefinitionsTypes.C_TYPEDEF, NativeDefinitionsTypes.C_UNION, NativeDefinitionsTypes.C_UNSIGNED,
    NativeDefinitionsTypes.C_VOID, NativeDefinitionsTypes.C_VOLATILE, NativeDefinitionsTypes.C_WHILE
)

class NativeDefinitionsSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        when (tokenType) {
            in SET_OF_OPERATORS -> OPERATOR_KEYS
            NativeDefinitionsTypes.C_DOT -> DOT_KEYS
            NativeDefinitionsTypes.C_SEMICOLON -> SEMICOLON_KEYS
            NativeDefinitionsTypes.C_COMMA -> COMMA_KEYS
            NativeDefinitionsTypes.C_L_CURLY, NativeDefinitionsTypes.C_R_CURLY -> BRACE_KEYS
            NativeDefinitionsTypes.C_L_BRACKET, NativeDefinitionsTypes.C_R_BRACKET -> BRACKET_KEYS
            NativeDefinitionsTypes.C_L_PAREN, NativeDefinitionsTypes.C_R_PAREN -> PAREN_KEYS
            NativeDefinitionsTypes.C_STRING_LITERAL -> STRING_KEYS
            NativeDefinitionsTypes.C_IDENTIFIER -> IDENTIFIER_KEYS
            NativeDefinitionsTypes.COMMENT, NativeDefinitionsTypes.DELIM -> COMMENT_KEYS
            NativeDefinitionsTypes.C_I_CONSTANT, NativeDefinitionsTypes.C_F_CONSTANT -> CONSTANT_KEYS
            in SET_OF_C_KEYWORDS -> C_KEYWORD_KEYS
            NativeDefinitionsTypes.DEF_KEY_KNOWN -> DEF_KEYWORD_KEYS
            NativeDefinitionsTypes.DEF_VALUE -> DEF_VALUE_KEYS
            TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS
            else -> EMPTY_KEYS
        }

    override fun getHighlightingLexer(): Lexer = NativeDefinitionsLexerAdapter()

    companion object {
        private fun createKeys(externalName: String, key: TextAttributesKey): Array<TextAttributesKey> {
            return arrayOf(TextAttributesKey.createTextAttributesKey(externalName, key))
        }

        val BAD_CHAR_KEYS = createKeys("Bad char", HighlighterColors.BAD_CHARACTER)
        val BRACE_KEYS = createKeys("Braces", DefaultLanguageHighlighterColors.BRACES)
        val BRACKET_KEYS = createKeys("Brackets", DefaultLanguageHighlighterColors.BRACKETS)
        val C_KEYWORD_KEYS = createKeys("C keyword", DefaultLanguageHighlighterColors.KEYWORD)
        val CONSTANT_KEYS = createKeys("Constant", DefaultLanguageHighlighterColors.CONSTANT)
        val COMMA_KEYS = createKeys("Comma", DefaultLanguageHighlighterColors.COMMA)
        val COMMENT_KEYS = createKeys("Comment", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val DEF_KEYWORD_KEYS = createKeys("Def keyword", DefaultLanguageHighlighterColors.KEYWORD)
        val DEF_VALUE_KEYS = createKeys("Def value", DefaultLanguageHighlighterColors.STRING)
        val DOT_KEYS = createKeys("Dot", DefaultLanguageHighlighterColors.DOT)
        val EMPTY_KEYS = emptyArray<TextAttributesKey>()
        val IDENTIFIER_KEYS = createKeys("Identifier", DefaultLanguageHighlighterColors.IDENTIFIER)
        val OPERATOR_KEYS = createKeys("Operator", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val PAREN_KEYS = createKeys("Parentheses", DefaultLanguageHighlighterColors.PARENTHESES)
        val SEMICOLON_KEYS = createKeys("Semicolon", DefaultLanguageHighlighterColors.SEMICOLON)
        val STRING_KEYS = createKeys("String literal", DefaultLanguageHighlighterColors.STRING)
    }
}

class NativeDefinitionsSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        NativeDefinitionsSyntaxHighlighter()
}

class NativeDefinitionsColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon? = KotlinIcons.NATIVE

    override fun getHighlighter(): SyntaxHighlighter = NativeDefinitionsSyntaxHighlighter()

    override fun getDemoText(): String {
        return """headers = curl/curl.h
# Unspecified options.
linkerOpts = -DBAR=bar
! Specified options.
compilerOpts.linux = -I/usr/include -I/usr/include/x86_64-linux-gnu

---

struct Hash { // Line comment.
    int data[2];
};

/**********************
 * Multiline comment. *
 **********************/
inline static int strangeSum(const int* buffer, int bufferSize) {
    int result = 20;
    const char * stringLiteral = "This is a string";
    for (int i = 0; i < bufferSize; ++i) {
        result += stringLiteral[i % 10] & 1 == 0 ? (i << 1) : buffer[i / 2];
    }
    return result;
}
"""
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        infix fun String.to(keys: Array<TextAttributesKey>) = AttributesDescriptor(this, keys[0])

        return arrayOf(
            "Braces and Operators//Braces" to NativeDefinitionsSyntaxHighlighter.BRACE_KEYS,
            "Braces and Operators//Brackets" to NativeDefinitionsSyntaxHighlighter.BRACKET_KEYS,
            "Braces and Operators//Comma" to NativeDefinitionsSyntaxHighlighter.COMMA_KEYS,
            "Braces and Operators//Dot" to NativeDefinitionsSyntaxHighlighter.DOT_KEYS,
            "Braces and Operators//Operator sign" to NativeDefinitionsSyntaxHighlighter.OPERATOR_KEYS,
            "Braces and Operators//Parentheses" to NativeDefinitionsSyntaxHighlighter.PAREN_KEYS,
            "Braces and Operators//Semicolon" to NativeDefinitionsSyntaxHighlighter.SEMICOLON_KEYS,
            "C keyword" to NativeDefinitionsSyntaxHighlighter.C_KEYWORD_KEYS,
            "Constant" to NativeDefinitionsSyntaxHighlighter.CONSTANT_KEYS,
            "Comment" to NativeDefinitionsSyntaxHighlighter.COMMENT_KEYS,
            "Def keywords" to NativeDefinitionsSyntaxHighlighter.DEF_KEYWORD_KEYS,
            "Def values" to NativeDefinitionsSyntaxHighlighter.DEF_VALUE_KEYS,
            "Identifier" to NativeDefinitionsSyntaxHighlighter.IDENTIFIER_KEYS,
            "String text" to NativeDefinitionsSyntaxHighlighter.STRING_KEYS
        )
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getDisplayName(): String = NATIVE_DEFINITIONS_NAME
}