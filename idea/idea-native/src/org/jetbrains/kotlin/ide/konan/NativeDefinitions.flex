package org.jetbrains.kotlin.ide.konan;

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

// DEF_BEGIN

D_SEPARATOR=[:=]
D_DOT=[.]
D_PLATFORM_CHAR=[:jletterdigit:]
D_KEY_CHAR=[^:=\ \n\t\f\\.] | "\\ "
D_FIRST_VALUE_CHAR=[^ \n\f\\] | "\\"{CRLF} | "\\".
D_VALUE_CHAR= [^\n\f\\] | "\\"{CRLF} | "\\".
D_COMMENT=("#"|"!")[^\r\n]*

// C_END

C_TRAD_COMMENT="/*" [^*]* ~"*/" | "/*" "*"+ "/"
C_LINEAR_COMMENT="//"[^\r\n]*
C_COMMENT={C_TRAD_COMMENT}|{C_LINEAR_COMMENT}
C_O=[0-7]
C_D=[0-9]
C_NZ=[1-9]
C_L=[a-zA-Z_]
C_A=[a-zA-Z_0-9]
C_H=[a-fA-F0-9]
C_HP=(0[xX])
C_E=([Ee][+-]?{C_D}+)
C_P=([Pp][+-]?{C_D}+)
C_FS=(f|F|l|L)
C_IS=(((u|U)(l|L|ll|LL)?)|((l|L|ll|LL)(u|U)?))
C_CP=(u|U|L)
C_SP=(u8|u|U|L)
C_ES=(\\(['\"\?\\abfnrtv]|{C_O}{1,3}|x{C_H}+))

%state D_WAITING_PLATFORM
%state D_WAITING_VALUE
%state C_END

%%

{WHITE_SPACE}+ { return TokenType.WHITE_SPACE; }

<YYINITIAL> {
  --- { yybegin(C_END); return NativeDefinitionsTypes.DELIM; }
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
    { yybegin(D_WAITING_PLATFORM); return NativeDefinitionsTypes.DEF_KEY_KNOWN; }
  {D_KEY_CHAR}+ { yybegin(D_WAITING_PLATFORM); return NativeDefinitionsTypes.DEF_KEY_UNKNOWN; }
  {D_COMMENT} { return NativeDefinitionsTypes.COMMENT; }
}

<D_WAITING_PLATFORM> {
  {D_DOT} { return NativeDefinitionsTypes.DEF_DOT; }
  {D_PLATFORM_CHAR}+ { return NativeDefinitionsTypes.DEF_PLATFORM; }
  {D_SEPARATOR} { yybegin(D_WAITING_VALUE); return NativeDefinitionsTypes.DEF_SEPARATOR; }
}

<D_WAITING_VALUE> {
  {D_FIRST_VALUE_CHAR}{D_VALUE_CHAR}* { yybegin(YYINITIAL); return NativeDefinitionsTypes.DEF_VALUE; }
}

<D_WAITING_PLATFORM,D_WAITING_VALUE> {CRLF}({CRLF}|{WHITE_SPACE})+ { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

// Based on Lex specification of ANSI C grammar

<C_END> {
  {C_COMMENT} { return NativeDefinitionsTypes.COMMENT; }
  auto { return NativeDefinitionsTypes.C_AUTO; }
  break { return NativeDefinitionsTypes.C_BREAK; }
  case { return NativeDefinitionsTypes.C_CASE; }
  char { return NativeDefinitionsTypes.C_CHAR; }
  const { return NativeDefinitionsTypes.C_CONST; }
  continue { return NativeDefinitionsTypes.C_CONTINUE; }
  default { return NativeDefinitionsTypes.C_DEFAULT; }
  do { return NativeDefinitionsTypes.C_DO; }
  double { return NativeDefinitionsTypes.C_DOUBLE; }
  else { return NativeDefinitionsTypes.C_ELSE; }
  enum { return NativeDefinitionsTypes.C_ENUM; }
  extern { return NativeDefinitionsTypes.C_EXTERN; }
  float { return NativeDefinitionsTypes.C_FLOAT; }
  for { return NativeDefinitionsTypes.C_FOR; }
  goto { return NativeDefinitionsTypes.C_GOTO; }
  if { return NativeDefinitionsTypes.C_IF; }
  inline { return NativeDefinitionsTypes.C_INLINE; }
  int { return NativeDefinitionsTypes.C_INT; }
  long { return NativeDefinitionsTypes.C_LONG; }
  register { return NativeDefinitionsTypes.C_REGISTER; }
  restrict { return NativeDefinitionsTypes.C_RESTRICT; }
  return { return NativeDefinitionsTypes.C_RETURN; }
  short { return NativeDefinitionsTypes.C_SHORT; }
  signed { return NativeDefinitionsTypes.C_SIGNED; }
  sizeof { return NativeDefinitionsTypes.C_SIZEOF; }
  static { return NativeDefinitionsTypes.C_STATIC; }
  struct { return NativeDefinitionsTypes.C_STRUCT; }
  switch { return NativeDefinitionsTypes.C_SWITCH; }
  typedef { return NativeDefinitionsTypes.C_TYPEDEF; }
  union { return NativeDefinitionsTypes.C_UNION; }
  unsigned { return NativeDefinitionsTypes.C_UNSIGNED; }
  void { return NativeDefinitionsTypes.C_VOID; }
  volatile { return NativeDefinitionsTypes.C_VOLATILE; }
  while { return NativeDefinitionsTypes.C_WHILE; }
  _Alignas { return NativeDefinitionsTypes.C_ALIGNAS; }
  _Alignof { return NativeDefinitionsTypes.C_ALIGNOF; }
  _Atomic { return NativeDefinitionsTypes.C_ATOMIC; }
  _Bool { return NativeDefinitionsTypes.C_BOOL; }
  _Complex { return NativeDefinitionsTypes.C_COMPLEX; }
  _Generic { return NativeDefinitionsTypes.C_GENERIC; }
  _Imaginary { return NativeDefinitionsTypes.C_IMAGINARY; }
  _Noreturn { return NativeDefinitionsTypes.C_NORETURN; }
  _Static_assert { return NativeDefinitionsTypes.C_STATIC_ASSERT; }
  _Thread_local { return NativeDefinitionsTypes.C_THREAD_LOCAL; }
  __func__ { return NativeDefinitionsTypes.C_FUNC_NAME; }

  {C_L}{C_A}* { return NativeDefinitionsTypes.C_IDENTIFIER; }

  {C_HP}{C_H}+{C_IS}?
  |	{C_NZ}{C_D}*{C_IS}?
  |	"0"{C_O}*{C_IS}?
  | {C_CP}?"'"([^'\\\n]|{C_ES})+"'"
    { return NativeDefinitionsTypes.C_I_CONSTANT; }

  {C_D}+{C_E}{C_FS}?
  | {C_D}*"."{C_D}+{C_E}?{C_FS}?
  | {C_D}+"."{C_E}?{C_FS}?
  | {C_HP}{C_H}+{C_P}{C_FS}?
  | {C_HP}{C_H}*"."{C_H}+{C_P}{C_FS}?
  | {C_HP}{C_H}+"."{C_P}{C_FS}?
    { return NativeDefinitionsTypes.C_F_CONSTANT; }

  ({C_SP}?\"([^\"\\\n]|{C_ES})*\"{WHITE_SPACE}*)+ { return NativeDefinitionsTypes.C_STRING_LITERAL; }

  "..."	{ return NativeDefinitionsTypes.C_ELLIPSIS; }
  ">>="	{ return NativeDefinitionsTypes.C_RIGHT_ASSIGN; }
  "<<=" { return NativeDefinitionsTypes.C_LEFT_ASSIGN; }
  "+=" { return NativeDefinitionsTypes.C_ADD_ASSIGN; }
  "-=" { return NativeDefinitionsTypes.C_SUB_ASSIGN; }
  "*=" { return NativeDefinitionsTypes.C_MUL_ASSIGN; }
  "/=" { return NativeDefinitionsTypes.C_DIV_ASSIGN; }
  "%=" { return NativeDefinitionsTypes.C_MOD_ASSIGN; }
  "&=" { return NativeDefinitionsTypes.C_AND_ASSIGN; }
  "^=" { return NativeDefinitionsTypes.C_XOR_ASSIGN; }
  "|=" { return NativeDefinitionsTypes.C_OR_ASSIGN; }
  ">>" { return NativeDefinitionsTypes.C_RIGHT_OP; }
  "<<" { return NativeDefinitionsTypes.C_LEFT_OP; }
  "++" { return NativeDefinitionsTypes.C_INC_OP; }
  "--" { return NativeDefinitionsTypes.C_DEC_OP; }
  "->" { return NativeDefinitionsTypes.C_PTR_OP; }
  "&&" { return NativeDefinitionsTypes.C_AND_OP; }
  "||" { return NativeDefinitionsTypes.C_OR_OP; }
  "<=" { return NativeDefinitionsTypes.C_LE_OP; }
  ">=" { return NativeDefinitionsTypes.C_GE_OP; }
  "==" { return NativeDefinitionsTypes.C_EQ_OP; }
  "!=" { return NativeDefinitionsTypes.C_NE_OP; }
  ";" { return NativeDefinitionsTypes.C_SEMICOLON; }
  ("{"|"<%") { return NativeDefinitionsTypes.C_L_CURLY; }
  ("}"|"%>") { return NativeDefinitionsTypes.C_R_CURLY; }
  "," { return NativeDefinitionsTypes.C_COMMA; }
  ":" { return NativeDefinitionsTypes.C_COLON; }
  "=" { return NativeDefinitionsTypes.C_EQ_SIGN; }
  "(" { return NativeDefinitionsTypes.C_L_PAREN; }
  ")" { return NativeDefinitionsTypes.C_R_PAREN; }
  ("["|"<:") { return NativeDefinitionsTypes.C_L_BRACKET; }
  ("]"|":>") { return NativeDefinitionsTypes.C_R_BRACKET; }
  "." { return NativeDefinitionsTypes.C_DOT; }
  "&" { return NativeDefinitionsTypes.C_AND; }
  "!" { return NativeDefinitionsTypes.C_EX_MARK; }
  "~" { return NativeDefinitionsTypes.C_TILDE; }
  "-" { return NativeDefinitionsTypes.C_MINUS; }
  "+" { return NativeDefinitionsTypes.C_PLUS; }
  "*" { return NativeDefinitionsTypes.C_MULT; }
  "/" { return NativeDefinitionsTypes.C_SLASH; }
  "%" { return NativeDefinitionsTypes.C_PERCENT; }
  "<" { return NativeDefinitionsTypes.C_LESS; }
  ">" { return NativeDefinitionsTypes.C_GREATER; }
  "^" { return NativeDefinitionsTypes.C_CARET; }
  "|" { return NativeDefinitionsTypes.C_VBAR; }
  "?" { return NativeDefinitionsTypes.C_QU_MARK; }
}

[^] { return TokenType.BAD_CHARACTER; }