package org.jetbrains.kotlin.ide.konan;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;
import org.jetbrains.kotlin.ide.konan.psi.NativeDefinitionsTypes;

%%

%class NativeDefinitionsLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

CRLF=\R
WHITE_SPACE=[\ \v\n\t\f]

SEPARATOR=[:=]
PLATFORM_CHAR=[:jletterdigit:]
KEY_CHAR=[^:=\ \n\t\f\\.] | "\\ "
FIRST_VALUE_CHAR=[^ \n\f\\] | "\\"{CRLF} | "\\".
VALUE_CHAR= [^\n\f\\] | "\\"{CRLF} | "\\".
COMMENT=("#"|"!")[^\r\n]*

%state WAITING_PLATFORM
%state WAITING_VALUE
%state CODE_END

%%

{WHITE_SPACE}+ { return TokenType.WHITE_SPACE; }

<YYINITIAL> {
  ---{CRLF} { yybegin(CODE_END); return NativeDefinitionsTypes.DELIM; }
  compilerOpts
  | excludeDependentModules
  | excludedFunctions
  | headerFilter
  | headers
  | libraryPaths
  | linkerOpts
  | nonStrictEnums
  | noStringConversion
  | package
  | staticLibraries
  | strictEnums
    { yybegin(WAITING_PLATFORM); return NativeDefinitionsTypes.KEY_KNOWN; }
  {KEY_CHAR}+ { yybegin(WAITING_PLATFORM); return NativeDefinitionsTypes.KEY_UNKNOWN; }
  {COMMENT} { return NativeDefinitionsTypes.COMMENT; }
}

<WAITING_PLATFORM> {
  [.]{PLATFORM_CHAR}+ { return NativeDefinitionsTypes.PLATFORM; }
  {SEPARATOR} { yybegin(WAITING_VALUE); return NativeDefinitionsTypes.SEPARATOR; }
}

<WAITING_VALUE> {FIRST_VALUE_CHAR}{VALUE_CHAR}* { yybegin(YYINITIAL); return NativeDefinitionsTypes.VALUE; }

<WAITING_PLATFORM,WAITING_VALUE> {CRLF}({CRLF}|{WHITE_SPACE})+ { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<CODE_END> [^]* { return NativeDefinitionsTypes.CODE_CHARS; }

[^] { return TokenType.BAD_CHARACTER; }