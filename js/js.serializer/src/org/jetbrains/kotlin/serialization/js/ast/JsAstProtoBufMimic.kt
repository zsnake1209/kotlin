/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.serialization.js.ast.protoMimic

class Location(
    val startLine: Int,
    val startChar: Int
) {

    fun hasStartLine(): Boolean = true

    fun hasStartChar(): Boolean = true
}

enum class SideEffects {
    AFFECTS_STATE,
    DEPENDS_ON_STATE,
    PURE;

    companion object {
        fun fromIndex(index: Int): SideEffects {
            return when (index) {
                1 -> AFFECTS_STATE
                2 -> DEPENDS_ON_STATE
                3 -> PURE
                else -> error("Unexpected enum value '$index' for enum 'SideEffects'")
            }
        }
    }
}

class JsImportedModule(
    val externalName: Int,
    val internalName: Int,
    private val fieldPlainReference: Expression?
) {

    val plainReference: Expression
        get() = fieldPlainReference!!

    fun hasExternalName(): Boolean = true

    fun hasInternalName(): Boolean = true

    fun hasPlainReference(): Boolean = fieldPlainReference != null
}

class Expression(
    private val fieldFileId: Int?,
    private val fieldLocation: Location?,
    val synthetic: Boolean,
    val sideEffects: SideEffects,
    private val fieldLocalAlias: JsImportedModule?,
    val expressionCase: ExpressionCase,
    private val fieldSimpleNameReference: Int?,
    private val fieldThisLiteral: ThisLiteral?,
    private val fieldNullLiteral: NullLiteral?,
    private val fieldTrueLiteral: TrueLiteral?,
    private val fieldFalseLiteral: FalseLiteral?,
    private val fieldStringLiteral: Int?,
    private val fieldRegExpLiteral: RegExpLiteral?,
    private val fieldIntLiteral: Int?,
    private val fieldDoubleLiteral: Double?,
    private val fieldArrayLiteral: ArrayLiteral?,
    private val fieldObjectLiteral: ObjectLiteral?,
    private val fieldFunction: Function?,
    private val fieldDocComment: DocComment?,
    private val fieldBinary: BinaryOperation?,
    private val fieldUnary: UnaryOperation?,
    private val fieldConditional: Conditional?,
    private val fieldArrayAccess: ArrayAccess?,
    private val fieldNameReference: NameReference?,
    private val fieldPropertyReference: PropertyReference?,
    private val fieldInvocation: Invocation?,
    private val fieldInstantiation: Instantiation?
) {
    enum class ExpressionCase {
        SIMPLE_NAME_REFERENCE,
        THIS_LITERAL,
        NULL_LITERAL,
        TRUE_LITERAL,
        FALSE_LITERAL,
        STRING_LITERAL,
        REG_EXP_LITERAL,
        INT_LITERAL,
        DOUBLE_LITERAL,
        ARRAY_LITERAL,
        OBJECT_LITERAL,
        FUNCTION,
        DOC_COMMENT,
        BINARY,
        UNARY,
        CONDITIONAL,
        ARRAY_ACCESS,
        NAME_REFERENCE,
        PROPERTY_REFERENCE,
        INVOCATION,
        INSTANTIATION,
        EXPRESSION_NOT_SET
    }

    val fileId: Int
        get() = fieldFileId!!

    val location: Location
        get() = fieldLocation!!

    val localAlias: JsImportedModule
        get() = fieldLocalAlias!!

    val simpleNameReference: Int
        get() = fieldSimpleNameReference!!

    val thisLiteral: ThisLiteral
        get() = fieldThisLiteral!!

    val nullLiteral: NullLiteral
        get() = fieldNullLiteral!!

    val trueLiteral: TrueLiteral
        get() = fieldTrueLiteral!!

    val falseLiteral: FalseLiteral
        get() = fieldFalseLiteral!!

    val stringLiteral: Int
        get() = fieldStringLiteral!!

    val regExpLiteral: RegExpLiteral
        get() = fieldRegExpLiteral!!

    val intLiteral: Int
        get() = fieldIntLiteral!!

    val doubleLiteral: Double
        get() = fieldDoubleLiteral!!

    val arrayLiteral: ArrayLiteral
        get() = fieldArrayLiteral!!

    val objectLiteral: ObjectLiteral
        get() = fieldObjectLiteral!!

    val function: Function
        get() = fieldFunction!!

    val docComment: DocComment
        get() = fieldDocComment!!

    val binary: BinaryOperation
        get() = fieldBinary!!

    val unary: UnaryOperation
        get() = fieldUnary!!

    val conditional: Conditional
        get() = fieldConditional!!

    val arrayAccess: ArrayAccess
        get() = fieldArrayAccess!!

    val nameReference: NameReference
        get() = fieldNameReference!!

    val propertyReference: PropertyReference
        get() = fieldPropertyReference!!

    val invocation: Invocation
        get() = fieldInvocation!!

    val instantiation: Instantiation
        get() = fieldInstantiation!!

    fun hasFileId(): Boolean = fieldFileId != null

    fun hasLocation(): Boolean = fieldLocation != null

    fun hasSynthetic(): Boolean = true

    fun hasSideEffects(): Boolean = true

    fun hasLocalAlias(): Boolean = fieldLocalAlias != null

    fun hasSimpleNameReference(): Boolean = fieldSimpleNameReference != null

    fun hasThisLiteral(): Boolean = fieldThisLiteral != null

    fun hasNullLiteral(): Boolean = fieldNullLiteral != null

    fun hasTrueLiteral(): Boolean = fieldTrueLiteral != null

    fun hasFalseLiteral(): Boolean = fieldFalseLiteral != null

    fun hasStringLiteral(): Boolean = fieldStringLiteral != null

    fun hasRegExpLiteral(): Boolean = fieldRegExpLiteral != null

    fun hasIntLiteral(): Boolean = fieldIntLiteral != null

    fun hasDoubleLiteral(): Boolean = fieldDoubleLiteral != null

    fun hasArrayLiteral(): Boolean = fieldArrayLiteral != null

    fun hasObjectLiteral(): Boolean = fieldObjectLiteral != null

    fun hasFunction(): Boolean = fieldFunction != null

    fun hasDocComment(): Boolean = fieldDocComment != null

    fun hasBinary(): Boolean = fieldBinary != null

    fun hasUnary(): Boolean = fieldUnary != null

    fun hasConditional(): Boolean = fieldConditional != null

    fun hasArrayAccess(): Boolean = fieldArrayAccess != null

    fun hasNameReference(): Boolean = fieldNameReference != null

    fun hasPropertyReference(): Boolean = fieldPropertyReference != null

    fun hasInvocation(): Boolean = fieldInvocation != null

    fun hasInstantiation(): Boolean = fieldInstantiation != null
}

class ThisLiteral(

) {
}

class NullLiteral(

) {
}

class TrueLiteral(

) {
}

class FalseLiteral(

) {
}

class RegExpLiteral(
    val patternStringId: Int,
    private val fieldFlagsStringId: Int?
) {

    val flagsStringId: Int
        get() = fieldFlagsStringId!!

    fun hasPatternStringId(): Boolean = true

    fun hasFlagsStringId(): Boolean = fieldFlagsStringId != null
}

class ArrayLiteral(
    val elementList: List<Expression>
) {

    fun hasElement(): Boolean = true

    val elementCount: Int
        get() = elementList.size
}

class ObjectLiteral(
    val entryList: List<ObjectLiteralEntry>,
    val multiline: Boolean
) {

    fun hasEntry(): Boolean = true

    val entryCount: Int
        get() = entryList.size

    fun hasMultiline(): Boolean = true
}

class ObjectLiteralEntry(
    val key: Expression,
    val value: Expression
) {

    fun hasKey(): Boolean = true

    fun hasValue(): Boolean = true
}

class Function(
    val parameterList: List<Parameter>,
    private val fieldNameId: Int?,
    val body: Statement,
    val local: Boolean
) {

    val nameId: Int
        get() = fieldNameId!!

    fun hasParameter(): Boolean = true

    val parameterCount: Int
        get() = parameterList.size

    fun hasNameId(): Boolean = fieldNameId != null

    fun hasBody(): Boolean = true

    fun hasLocal(): Boolean = true
}

class Parameter(
    val nameId: Int,
    val hasDefaultValue: Boolean
) {

    fun hasNameId(): Boolean = true

    fun hasHasDefaultValue(): Boolean = true
}

class DocComment(
    val tagList: List<DocCommentTag>
) {

    fun hasTag(): Boolean = true

    val tagCount: Int
        get() = tagList.size
}

class DocCommentTag(
    val nameId: Int,
    val valueCase: ValueCase,
    private val fieldValueStringId: Int?,
    private val fieldExpression: Expression?
) {
    enum class ValueCase {
        VALUE_STRING_ID,
        EXPRESSION,
        VALUE_NOT_SET
    }

    val valueStringId: Int
        get() = fieldValueStringId!!

    val expression: Expression
        get() = fieldExpression!!

    fun hasNameId(): Boolean = true

    fun hasValueStringId(): Boolean = fieldValueStringId != null

    fun hasExpression(): Boolean = fieldExpression != null
}

class BinaryOperation(
    val left: Expression,
    val right: Expression,
    val type: Type
) {
    enum class Type {
        MUL,
        DIV,
        MOD,
        ADD,
        SUB,
        SHL,
        SHR,
        SHRU,
        LT,
        LTE,
        GT,
        GTE,
        INSTANCEOF,
        IN,
        EQ,
        NEQ,
        REF_EQ,
        REF_NEQ,
        BIT_AND,
        BIT_XOR,
        BIT_OR,
        AND,
        OR,
        ASG,
        ASG_ADD,
        ASG_SUB,
        ASG_MUL,
        ASG_DIV,
        ASG_MOD,
        ASG_SHL,
        ASG_SHR,
        ASG_SHRU,
        ASG_BIT_AND,
        ASG_BIT_OR,
        ASG_BIT_XOR,
        COMMA    ;

        companion object {
            fun fromIndex(index: Int): Type {
                return when (index) {
                    1 -> MUL
                    2 -> DIV
                    3 -> MOD
                    4 -> ADD
                    5 -> SUB
                    6 -> SHL
                    7 -> SHR
                    8 -> SHRU
                    9 -> LT
                    10 -> LTE
                    11 -> GT
                    12 -> GTE
                    13 -> INSTANCEOF
                    14 -> IN
                    15 -> EQ
                    16 -> NEQ
                    17 -> REF_EQ
                    18 -> REF_NEQ
                    19 -> BIT_AND
                    20 -> BIT_XOR
                    21 -> BIT_OR
                    22 -> AND
                    23 -> OR
                    24 -> ASG
                    25 -> ASG_ADD
                    26 -> ASG_SUB
                    27 -> ASG_MUL
                    28 -> ASG_DIV
                    29 -> ASG_MOD
                    30 -> ASG_SHL
                    31 -> ASG_SHR
                    32 -> ASG_SHRU
                    33 -> ASG_BIT_AND
                    34 -> ASG_BIT_OR
                    35 -> ASG_BIT_XOR
                    36 -> COMMA
                    else -> error("Unexpected enum value '$index' for enum 'Type'")
                }
            }
        }
    }


    fun hasLeft(): Boolean = true

    fun hasRight(): Boolean = true

    fun hasType(): Boolean = true
}

class UnaryOperation(
    val operand: Expression,
    val type: Type,
    val postfix: Boolean
) {
    enum class Type {
        BIT_NOT,
        DEC,
        DELETE,
        INC,
        NEG,
        POS,
        NOT,
        TYPEOF,
        VOID    ;

        companion object {
            fun fromIndex(index: Int): Type {
                return when (index) {
                    1 -> BIT_NOT
                    2 -> DEC
                    3 -> DELETE
                    4 -> INC
                    5 -> NEG
                    6 -> POS
                    7 -> NOT
                    8 -> TYPEOF
                    9 -> VOID
                    else -> error("Unexpected enum value '$index' for enum 'Type'")
                }
            }
        }
    }


    fun hasOperand(): Boolean = true

    fun hasType(): Boolean = true

    fun hasPostfix(): Boolean = true
}

class Conditional(
    val testExpression: Expression,
    val thenExpression: Expression,
    val elseExpression: Expression
) {

    fun hasTestExpression(): Boolean = true

    fun hasThenExpression(): Boolean = true

    fun hasElseExpression(): Boolean = true
}

class ArrayAccess(
    val array: Expression,
    val index: Expression
) {

    fun hasArray(): Boolean = true

    fun hasIndex(): Boolean = true
}

class NameReference(
    val nameId: Int,
    private val fieldQualifier: Expression?,
    val inlineStrategy: InlineStrategy
) {

    val qualifier: Expression
        get() = fieldQualifier!!

    fun hasNameId(): Boolean = true

    fun hasQualifier(): Boolean = fieldQualifier != null

    fun hasInlineStrategy(): Boolean = true
}

class PropertyReference(
    val stringId: Int,
    private val fieldQualifier: Expression?,
    val inlineStrategy: InlineStrategy
) {

    val qualifier: Expression
        get() = fieldQualifier!!

    fun hasStringId(): Boolean = true

    fun hasQualifier(): Boolean = fieldQualifier != null

    fun hasInlineStrategy(): Boolean = true
}

class Invocation(
    val qualifier: Expression,
    val argumentList: List<Expression>,
    val inlineStrategy: InlineStrategy
) {

    fun hasQualifier(): Boolean = true

    fun hasArgument(): Boolean = true

    val argumentCount: Int
        get() = argumentList.size

    fun hasInlineStrategy(): Boolean = true
}

class Instantiation(
    val qualifier: Expression,
    val argumentList: List<Expression>
) {

    fun hasQualifier(): Boolean = true

    fun hasArgument(): Boolean = true

    val argumentCount: Int
        get() = argumentList.size
}

class Statement(
    private val fieldFileId: Int?,
    private val fieldLocation: Location?,
    val synthetic: Boolean,
    val statementCase: StatementCase,
    private val fieldReturnStatement: Return?,
    private val fieldThrowStatement: Throw?,
    private val fieldBreakStatement: Break?,
    private val fieldContinueStatement: Continue?,
    private val fieldDebugger: Debugger?,
    private val fieldExpression: ExpressionStatement?,
    private val fieldVars: Vars?,
    private val fieldBlock: Block?,
    private val fieldGlobalBlock: GlobalBlock?,
    private val fieldLabel: Label?,
    private val fieldIfStatement: If?,
    private val fieldSwitchStatement: Switch?,
    private val fieldWhileStatement: While?,
    private val fieldDoWhileStatement: DoWhile?,
    private val fieldForStatement: For?,
    private val fieldForInStatement: ForIn?,
    private val fieldTryStatement: Try?,
    private val fieldEmpty: Empty?
) {
    enum class StatementCase {
        RETURN_STATEMENT,
        THROW_STATEMENT,
        BREAK_STATEMENT,
        CONTINUE_STATEMENT,
        DEBUGGER,
        EXPRESSION,
        VARS,
        BLOCK,
        GLOBAL_BLOCK,
        LABEL,
        IF_STATEMENT,
        SWITCH_STATEMENT,
        WHILE_STATEMENT,
        DO_WHILE_STATEMENT,
        FOR_STATEMENT,
        FOR_IN_STATEMENT,
        TRY_STATEMENT,
        EMPTY,
        STATEMENT_NOT_SET
    }

    val fileId: Int
        get() = fieldFileId!!

    val location: Location
        get() = fieldLocation!!

    val returnStatement: Return
        get() = fieldReturnStatement!!

    val throwStatement: Throw
        get() = fieldThrowStatement!!

    val breakStatement: Break
        get() = fieldBreakStatement!!

    val continueStatement: Continue
        get() = fieldContinueStatement!!

    val debugger: Debugger
        get() = fieldDebugger!!

    val expression: ExpressionStatement
        get() = fieldExpression!!

    val vars: Vars
        get() = fieldVars!!

    val block: Block
        get() = fieldBlock!!

    val globalBlock: GlobalBlock
        get() = fieldGlobalBlock!!

    val label: Label
        get() = fieldLabel!!

    val ifStatement: If
        get() = fieldIfStatement!!

    val switchStatement: Switch
        get() = fieldSwitchStatement!!

    val whileStatement: While
        get() = fieldWhileStatement!!

    val doWhileStatement: DoWhile
        get() = fieldDoWhileStatement!!

    val forStatement: For
        get() = fieldForStatement!!

    val forInStatement: ForIn
        get() = fieldForInStatement!!

    val tryStatement: Try
        get() = fieldTryStatement!!

    val empty: Empty
        get() = fieldEmpty!!

    fun hasFileId(): Boolean = fieldFileId != null

    fun hasLocation(): Boolean = fieldLocation != null

    fun hasSynthetic(): Boolean = true

    fun hasReturnStatement(): Boolean = fieldReturnStatement != null

    fun hasThrowStatement(): Boolean = fieldThrowStatement != null

    fun hasBreakStatement(): Boolean = fieldBreakStatement != null

    fun hasContinueStatement(): Boolean = fieldContinueStatement != null

    fun hasDebugger(): Boolean = fieldDebugger != null

    fun hasExpression(): Boolean = fieldExpression != null

    fun hasVars(): Boolean = fieldVars != null

    fun hasBlock(): Boolean = fieldBlock != null

    fun hasGlobalBlock(): Boolean = fieldGlobalBlock != null

    fun hasLabel(): Boolean = fieldLabel != null

    fun hasIfStatement(): Boolean = fieldIfStatement != null

    fun hasSwitchStatement(): Boolean = fieldSwitchStatement != null

    fun hasWhileStatement(): Boolean = fieldWhileStatement != null

    fun hasDoWhileStatement(): Boolean = fieldDoWhileStatement != null

    fun hasForStatement(): Boolean = fieldForStatement != null

    fun hasForInStatement(): Boolean = fieldForInStatement != null

    fun hasTryStatement(): Boolean = fieldTryStatement != null

    fun hasEmpty(): Boolean = fieldEmpty != null
}

class Return(
    private val fieldValue: Expression?
) {

    val value: Expression
        get() = fieldValue!!

    fun hasValue(): Boolean = fieldValue != null
}

class Throw(
    val exception: Expression
) {

    fun hasException(): Boolean = true
}

class Break(
    private val fieldLabelId: Int?
) {

    val labelId: Int
        get() = fieldLabelId!!

    fun hasLabelId(): Boolean = fieldLabelId != null
}

class Continue(
    private val fieldLabelId: Int?
) {

    val labelId: Int
        get() = fieldLabelId!!

    fun hasLabelId(): Boolean = fieldLabelId != null
}

class Debugger(

) {
}

class ExpressionStatement(
    val expression: Expression,
    private val fieldExportedTagId: Int?
) {

    val exportedTagId: Int
        get() = fieldExportedTagId!!

    fun hasExpression(): Boolean = true

    fun hasExportedTagId(): Boolean = fieldExportedTagId != null
}

class Vars(
    val declarationList: List<VarDeclaration>,
    val multiline: Boolean,
    private val fieldExportedPackageId: Int?
) {

    val exportedPackageId: Int
        get() = fieldExportedPackageId!!

    fun hasDeclaration(): Boolean = true

    val declarationCount: Int
        get() = declarationList.size

    fun hasMultiline(): Boolean = true

    fun hasExportedPackageId(): Boolean = fieldExportedPackageId != null
}

class VarDeclaration(
    val nameId: Int,
    private val fieldInitialValue: Expression?,
    private val fieldFileId: Int?,
    private val fieldLocation: Location?
) {

    val initialValue: Expression
        get() = fieldInitialValue!!

    val fileId: Int
        get() = fieldFileId!!

    val location: Location
        get() = fieldLocation!!

    fun hasNameId(): Boolean = true

    fun hasInitialValue(): Boolean = fieldInitialValue != null

    fun hasFileId(): Boolean = fieldFileId != null

    fun hasLocation(): Boolean = fieldLocation != null
}

class Block(
    val statementList: List<Statement>
) {

    fun hasStatement(): Boolean = true

    val statementCount: Int
        get() = statementList.size
}

class GlobalBlock(
    val statementList: List<Statement>
) {

    fun hasStatement(): Boolean = true

    val statementCount: Int
        get() = statementList.size
}

class Label(
    val nameId: Int,
    val innerStatement: Statement
) {

    fun hasNameId(): Boolean = true

    fun hasInnerStatement(): Boolean = true
}

class If(
    val condition: Expression,
    val thenStatement: Statement,
    private val fieldElseStatement: Statement?
) {

    val elseStatement: Statement
        get() = fieldElseStatement!!

    fun hasCondition(): Boolean = true

    fun hasThenStatement(): Boolean = true

    fun hasElseStatement(): Boolean = fieldElseStatement != null
}

class Switch(
    val expression: Expression,
    val entryList: List<SwitchEntry>
) {

    fun hasExpression(): Boolean = true

    fun hasEntry(): Boolean = true

    val entryCount: Int
        get() = entryList.size
}

class SwitchEntry(
    private val fieldLabel: Expression?,
    val statementList: List<Statement>,
    private val fieldFileId: Int?,
    private val fieldLocation: Location?
) {

    val label: Expression
        get() = fieldLabel!!

    val fileId: Int
        get() = fieldFileId!!

    val location: Location
        get() = fieldLocation!!

    fun hasLabel(): Boolean = fieldLabel != null

    fun hasStatement(): Boolean = true

    val statementCount: Int
        get() = statementList.size

    fun hasFileId(): Boolean = fieldFileId != null

    fun hasLocation(): Boolean = fieldLocation != null
}

class While(
    val condition: Expression,
    val body: Statement
) {

    fun hasCondition(): Boolean = true

    fun hasBody(): Boolean = true
}

class DoWhile(
    val condition: Expression,
    val body: Statement
) {

    fun hasCondition(): Boolean = true

    fun hasBody(): Boolean = true
}

class For(
    val initCase: InitCase,
    private val fieldVariables: Statement?,
    private val fieldExpression: Expression?,
    private val fieldEmpty: EmptyInit?,
    private val fieldCondition: Expression?,
    private val fieldIncrement: Expression?,
    val body: Statement
) {
    enum class InitCase {
        VARIABLES,
        EXPRESSION,
        EMPTY,
        INIT_NOT_SET
    }

    val variables: Statement
        get() = fieldVariables!!

    val expression: Expression
        get() = fieldExpression!!

    val empty: EmptyInit
        get() = fieldEmpty!!

    val condition: Expression
        get() = fieldCondition!!

    val increment: Expression
        get() = fieldIncrement!!

    fun hasVariables(): Boolean = fieldVariables != null

    fun hasExpression(): Boolean = fieldExpression != null

    fun hasEmpty(): Boolean = fieldEmpty != null

    fun hasCondition(): Boolean = fieldCondition != null

    fun hasIncrement(): Boolean = fieldIncrement != null

    fun hasBody(): Boolean = true
}

class EmptyInit(

) {
}

class ForIn(
    val valueCase: ValueCase,
    private val fieldNameId: Int?,
    private val fieldExpression: Expression?,
    val iterable: Expression,
    val body: Statement
) {
    enum class ValueCase {
        NAMEID,
        EXPRESSION,
        VALUE_NOT_SET
    }

    val nameId: Int
        get() = fieldNameId!!

    val expression: Expression
        get() = fieldExpression!!

    fun hasNameId(): Boolean = fieldNameId != null

    fun hasExpression(): Boolean = fieldExpression != null

    fun hasIterable(): Boolean = true

    fun hasBody(): Boolean = true
}

class Try(
    val tryBlock: Statement,
    private val fieldCatchBlock: Catch?,
    private val fieldFinallyBlock: Statement?
) {

    val catchBlock: Catch
        get() = fieldCatchBlock!!

    val finallyBlock: Statement
        get() = fieldFinallyBlock!!

    fun hasTryBlock(): Boolean = true

    fun hasCatchBlock(): Boolean = fieldCatchBlock != null

    fun hasFinallyBlock(): Boolean = fieldFinallyBlock != null
}

class Catch(
    val parameter: Parameter,
    val body: Statement
) {

    fun hasParameter(): Boolean = true

    fun hasBody(): Boolean = true
}

class Empty(

) {
}

enum class InlineStrategy {
    AS_FUNCTION,
    IN_PLACE,
    NOT_INLINE;

    companion object {
        fun fromIndex(index: Int): InlineStrategy {
            return when (index) {
                0 -> AS_FUNCTION
                1 -> IN_PLACE
                2 -> NOT_INLINE
                else -> error("Unexpected enum value '$index' for enum 'InlineStrategy'")
            }
        }
    }
}

class Fragment(
    val importedModuleList: List<ImportedModule>,
    val importEntryList: List<Import>,
    private val fieldDeclarationBlock: GlobalBlock?,
    private val fieldExportBlock: GlobalBlock?,
    private val fieldInitializerBlock: GlobalBlock?,
    val nameBindingList: List<NameBinding>,
    val classModelList: List<ClassModel>,
    val moduleExpressionList: List<Expression>,
    val inlineModuleList: List<InlineModule>,
    private val fieldPackageFqn: String?,
    private val fieldTestsInvocation: Statement?,
    private val fieldMainInvocation: Statement?,
    val inlinedLocalDeclarationsList: List<InlinedLocalDeclarations>
) {

    val declarationBlock: GlobalBlock
        get() = fieldDeclarationBlock!!

    val exportBlock: GlobalBlock
        get() = fieldExportBlock!!

    val initializerBlock: GlobalBlock
        get() = fieldInitializerBlock!!

    val packageFqn: String
        get() = fieldPackageFqn!!

    val testsInvocation: Statement
        get() = fieldTestsInvocation!!

    val mainInvocation: Statement
        get() = fieldMainInvocation!!

    fun hasImportedModule(): Boolean = true

    val importedModuleCount: Int
        get() = importedModuleList.size

    fun hasImportEntry(): Boolean = true

    val importEntryCount: Int
        get() = importEntryList.size

    fun hasDeclarationBlock(): Boolean = fieldDeclarationBlock != null

    fun hasExportBlock(): Boolean = fieldExportBlock != null

    fun hasInitializerBlock(): Boolean = fieldInitializerBlock != null

    fun hasNameBinding(): Boolean = true

    val nameBindingCount: Int
        get() = nameBindingList.size

    fun hasClassModel(): Boolean = true

    val classModelCount: Int
        get() = classModelList.size

    fun hasModuleExpression(): Boolean = true

    val moduleExpressionCount: Int
        get() = moduleExpressionList.size

    fun hasInlineModule(): Boolean = true

    val inlineModuleCount: Int
        get() = inlineModuleList.size

    fun hasPackageFqn(): Boolean = fieldPackageFqn != null

    fun hasTestsInvocation(): Boolean = fieldTestsInvocation != null

    fun hasMainInvocation(): Boolean = fieldMainInvocation != null

    fun hasInlinedLocalDeclarations(): Boolean = true

    val inlinedLocalDeclarationsCount: Int
        get() = inlinedLocalDeclarationsList.size
}

class InlinedLocalDeclarations(
    val tag: Int,
    val block: GlobalBlock
) {

    fun hasTag(): Boolean = true

    fun hasBlock(): Boolean = true
}

class ImportedModule(
    val externalNameId: Int,
    val internalNameId: Int,
    private val fieldPlainReference: Expression?
) {

    val plainReference: Expression
        get() = fieldPlainReference!!

    fun hasExternalNameId(): Boolean = true

    fun hasInternalNameId(): Boolean = true

    fun hasPlainReference(): Boolean = fieldPlainReference != null
}

class Import(
    val signatureId: Int,
    val expression: Expression
) {

    fun hasSignatureId(): Boolean = true

    fun hasExpression(): Boolean = true
}

class NameBinding(
    val signatureId: Int,
    val nameId: Int
) {

    fun hasSignatureId(): Boolean = true

    fun hasNameId(): Boolean = true
}

class ClassModel(
    val nameId: Int,
    private val fieldSuperNameId: Int?,
    val interfaceNameIdList: List<Int>,
    private val fieldPostDeclarationBlock: GlobalBlock?
) {

    val superNameId: Int
        get() = fieldSuperNameId!!

    val postDeclarationBlock: GlobalBlock
        get() = fieldPostDeclarationBlock!!

    fun hasNameId(): Boolean = true

    fun hasSuperNameId(): Boolean = fieldSuperNameId != null

    fun hasInterfaceNameId(): Boolean = true

    val interfaceNameIdCount: Int
        get() = interfaceNameIdList.size

    fun hasPostDeclarationBlock(): Boolean = fieldPostDeclarationBlock != null
}

class InlineModule(
    val signatureId: Int,
    val expressionId: Int
) {

    fun hasSignatureId(): Boolean = true

    fun hasExpressionId(): Boolean = true
}

class StringTable(
    val entryList: List<String>
) {

    fun hasEntry(): Boolean = true

    val entryCount: Int
        get() = entryList.size
}

class NameTable(
    val entryList: List<Name>
) {

    fun hasEntry(): Boolean = true

    val entryCount: Int
        get() = entryList.size
}

class Name(
    val temporary: Boolean,
    private val fieldIdentifier: Int?,
    private val fieldLocalNameId: LocalAlias?,
    val imported: Boolean,
    private val fieldSpecialFunction: SpecialFunction?
) {

    val identifier: Int
        get() = fieldIdentifier!!

    val localNameId: LocalAlias
        get() = fieldLocalNameId!!

    val specialFunction: SpecialFunction
        get() = fieldSpecialFunction!!

    fun hasTemporary(): Boolean = true

    fun hasIdentifier(): Boolean = fieldIdentifier != null

    fun hasLocalNameId(): Boolean = fieldLocalNameId != null

    fun hasImported(): Boolean = true

    fun hasSpecialFunction(): Boolean = fieldSpecialFunction != null
}

class LocalAlias(
    val localNameId: Int,
    private val fieldTag: Int?
) {

    val tag: Int
        get() = fieldTag!!

    fun hasLocalNameId(): Boolean = true

    fun hasTag(): Boolean = fieldTag != null
}

enum class SpecialFunction {
    DEFINE_INLINE_FUNCTION,
    WRAP_FUNCTION,
    TO_BOXED_CHAR,
    UNBOX_CHAR,
    SUSPEND_CALL,
    COROUTINE_RESULT,
    COROUTINE_CONTROLLER,
    COROUTINE_RECEIVER,
    SET_COROUTINE_RESULT,
    GET_KCLASS,
    GET_REIFIED_TYPE_PARAMETER_KTYPE;

    companion object {
        fun fromIndex(index: Int): SpecialFunction {
            return when (index) {
                1 -> DEFINE_INLINE_FUNCTION
                2 -> WRAP_FUNCTION
                3 -> TO_BOXED_CHAR
                4 -> UNBOX_CHAR
                5 -> SUSPEND_CALL
                6 -> COROUTINE_RESULT
                7 -> COROUTINE_CONTROLLER
                8 -> COROUTINE_RECEIVER
                9 -> SET_COROUTINE_RESULT
                10 -> GET_KCLASS
                11 -> GET_REIFIED_TYPE_PARAMETER_KTYPE
                else -> error("Unexpected enum value '$index' for enum 'SpecialFunction'")
            }
        }
    }
}

class Chunk(
    val stringTable: StringTable,
    val nameTable: NameTable,
    val fragment: Fragment
) {

    fun hasStringTable(): Boolean = true

    fun hasNameTable(): Boolean = true

    fun hasFragment(): Boolean = true
}

class InlineData(
    val inlineFunctionTagsList: List<String>
) {

    fun hasInlineFunctionTags(): Boolean = true

    val inlineFunctionTagsCount: Int
        get() = inlineFunctionTagsList.size
}

class JsAstProtoReaderMimic(private val source: ByteArray) {


    private var offset = 0

    private var currentEnd = source.size

    private val hasData: Boolean
        get() = offset < currentEnd

    private inline fun <T> readWithLength(block: () -> T): T {
        val length = readInt32()
        val oldEnd = currentEnd
        currentEnd = offset + length
        try {
            return block()
        } finally {
            currentEnd = oldEnd
        }
    }

    private fun nextByte(): Byte {
        if (!hasData) error("Oops")
        return source[offset++]
    }

    private fun readVarint64(): Long {
        var result = 0L

        var shift = 0
        while (true) {
            val b = nextByte().toInt()

            result = result or ((b and 0x7F).toLong() shl shift)
            shift += 7

            if ((b and 0x80) == 0) break
        }

        if (shift > 70) {
            error("int64 overflow $shift")
        }

        return result
    }

    private fun readVarint32(): Int {
        var result = 0

        var shift = 0
        while (true) {
            val b = nextByte().toInt()

            result = result or ((b and 0x7F) shl shift)
            shift += 7

            if ((b and 0x80) == 0) break
        }

        if (shift > 70) {
            error("int32 overflow $shift")
        }

        return result
    }

    private fun readInt32(): Int = readVarint32()

    private fun readInt64(): Long = readVarint64()

    private fun readBool(): Boolean = readVarint32() != 0

    private fun readFloat(): Float {
        var bits = nextByte().toInt()
        bits = (bits shl 8) or nextByte().toInt()
        bits = (bits shl 8) or nextByte().toInt()
        bits = (bits shl 8) or nextByte().toInt()

        return Float.fromBits(bits)
    }

    private fun readDouble(): Double {
        var bits = nextByte().toLong()
        bits = (bits shl 8) or nextByte().toLong()
        bits = (bits shl 8) or nextByte().toLong()
        bits = (bits shl 8) or nextByte().toLong()
        bits = (bits shl 8) or nextByte().toLong()
        bits = (bits shl 8) or nextByte().toLong()
        bits = (bits shl 8) or nextByte().toLong()
        bits = (bits shl 8) or nextByte().toLong()

        return Double.fromBits(bits)
    }

    private fun readString(): String {
        val length = readInt32()
        val result = String(source, offset, length)
        offset += length
        return result
    }

    private fun skip(type: Int) {
        when (type) {
            0 -> readInt64()
            1 -> offset += 8
            2 -> {
                val len = readInt32()
                offset += len
            }
            3, 4 -> error("groups")
            5 -> offset += 4
        }
    }


    fun readLocation(): Location {
        var startLine: Int = 0
        var startChar: Int = 0
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> startLine = readInt32()
                16 -> startChar = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return Location(startLine, startChar)
    }

    fun readJsImportedModule(): JsImportedModule {
        var externalName: Int = 0
        var internalName: Int = 0
        var plainReference: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> externalName = readInt32()
                16 -> internalName = readInt32()
                26 -> plainReference = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return JsImportedModule(externalName, internalName, plainReference)
    }

    fun readExpression(): Expression {
        var fileId: Int? = null
        var location: Location? = null
        var synthetic: Boolean = false
        var sideEffects: SideEffects = SideEffects.AFFECTS_STATE
        var localAlias: JsImportedModule? = null
        var simpleNameReference: Int? = null
        var thisLiteral: ThisLiteral? = null
        var nullLiteral: NullLiteral? = null
        var trueLiteral: TrueLiteral? = null
        var falseLiteral: FalseLiteral? = null
        var stringLiteral: Int? = null
        var regExpLiteral: RegExpLiteral? = null
        var intLiteral: Int? = null
        var doubleLiteral: Double? = null
        var arrayLiteral: ArrayLiteral? = null
        var objectLiteral: ObjectLiteral? = null
        var function: Function? = null
        var docComment: DocComment? = null
        var binary: BinaryOperation? = null
        var unary: UnaryOperation? = null
        var conditional: Conditional? = null
        var arrayAccess: ArrayAccess? = null
        var nameReference: NameReference? = null
        var propertyReference: PropertyReference? = null
        var invocation: Invocation? = null
        var instantiation: Instantiation? = null
        var oneOfCase: Expression.ExpressionCase = Expression.ExpressionCase.EXPRESSION_NOT_SET
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> fileId = readInt32()
                18 -> location = readWithLength { readLocation() }
                24 -> synthetic = readBool()
                32 -> sideEffects = SideEffects.fromIndex(readInt32())
                42 -> localAlias = readWithLength { readJsImportedModule() }
                176 -> {
                    simpleNameReference = readInt32()
                    oneOfCase = Expression.ExpressionCase.SIMPLE_NAME_REFERENCE
                }
                186 -> {
                    thisLiteral = readWithLength { readThisLiteral() }
                    oneOfCase = Expression.ExpressionCase.THIS_LITERAL
                }
                194 -> {
                    nullLiteral = readWithLength { readNullLiteral() }
                    oneOfCase = Expression.ExpressionCase.NULL_LITERAL
                }
                202 -> {
                    trueLiteral = readWithLength { readTrueLiteral() }
                    oneOfCase = Expression.ExpressionCase.TRUE_LITERAL
                }
                210 -> {
                    falseLiteral = readWithLength { readFalseLiteral() }
                    oneOfCase = Expression.ExpressionCase.FALSE_LITERAL
                }
                216 -> {
                    stringLiteral = readInt32()
                    oneOfCase = Expression.ExpressionCase.STRING_LITERAL
                }
                226 -> {
                    regExpLiteral = readWithLength { readRegExpLiteral() }
                    oneOfCase = Expression.ExpressionCase.REG_EXP_LITERAL
                }
                232 -> {
                    intLiteral = readInt32()
                    oneOfCase = Expression.ExpressionCase.INT_LITERAL
                }
                241 -> {
                    doubleLiteral = readDouble()
                    oneOfCase = Expression.ExpressionCase.DOUBLE_LITERAL
                }
                250 -> {
                    arrayLiteral = readWithLength { readArrayLiteral() }
                    oneOfCase = Expression.ExpressionCase.ARRAY_LITERAL
                }
                258 -> {
                    objectLiteral = readWithLength { readObjectLiteral() }
                    oneOfCase = Expression.ExpressionCase.OBJECT_LITERAL
                }
                266 -> {
                    function = readWithLength { readFunction() }
                    oneOfCase = Expression.ExpressionCase.FUNCTION
                }
                274 -> {
                    docComment = readWithLength { readDocComment() }
                    oneOfCase = Expression.ExpressionCase.DOC_COMMENT
                }
                282 -> {
                    binary = readWithLength { readBinaryOperation() }
                    oneOfCase = Expression.ExpressionCase.BINARY
                }
                290 -> {
                    unary = readWithLength { readUnaryOperation() }
                    oneOfCase = Expression.ExpressionCase.UNARY
                }
                298 -> {
                    conditional = readWithLength { readConditional() }
                    oneOfCase = Expression.ExpressionCase.CONDITIONAL
                }
                306 -> {
                    arrayAccess = readWithLength { readArrayAccess() }
                    oneOfCase = Expression.ExpressionCase.ARRAY_ACCESS
                }
                314 -> {
                    nameReference = readWithLength { readNameReference() }
                    oneOfCase = Expression.ExpressionCase.NAME_REFERENCE
                }
                322 -> {
                    propertyReference = readWithLength { readPropertyReference() }
                    oneOfCase = Expression.ExpressionCase.PROPERTY_REFERENCE
                }
                330 -> {
                    invocation = readWithLength { readInvocation() }
                    oneOfCase = Expression.ExpressionCase.INVOCATION
                }
                338 -> {
                    instantiation = readWithLength { readInstantiation() }
                    oneOfCase = Expression.ExpressionCase.INSTANTIATION
                }
                else -> skip(fieldHeader and 7)
            }
        }
        return Expression(fileId, location, synthetic, sideEffects, localAlias, oneOfCase!!, simpleNameReference, thisLiteral, nullLiteral, trueLiteral, falseLiteral, stringLiteral, regExpLiteral, intLiteral, doubleLiteral, arrayLiteral, objectLiteral, function, docComment, binary, unary, conditional, arrayAccess, nameReference, propertyReference, invocation, instantiation)
    }

    fun readThisLiteral(): ThisLiteral {
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                else -> skip(fieldHeader and 7)
            }
        }
        return ThisLiteral()
    }

    fun readNullLiteral(): NullLiteral {
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                else -> skip(fieldHeader and 7)
            }
        }
        return NullLiteral()
    }

    fun readTrueLiteral(): TrueLiteral {
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                else -> skip(fieldHeader and 7)
            }
        }
        return TrueLiteral()
    }

    fun readFalseLiteral(): FalseLiteral {
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                else -> skip(fieldHeader and 7)
            }
        }
        return FalseLiteral()
    }

    fun readRegExpLiteral(): RegExpLiteral {
        var patternStringId: Int = 0
        var flagsStringId: Int? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> patternStringId = readInt32()
                16 -> flagsStringId = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return RegExpLiteral(patternStringId, flagsStringId)
    }

    fun readArrayLiteral(): ArrayLiteral {
        var element: MutableList<Expression> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> element.add(readWithLength { readExpression() })
                else -> skip(fieldHeader and 7)
            }
        }
        return ArrayLiteral(element)
    }

    fun readObjectLiteral(): ObjectLiteral {
        var entry: MutableList<ObjectLiteralEntry> = mutableListOf()
        var multiline: Boolean = true
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> entry.add(readWithLength { readObjectLiteralEntry() })
                16 -> multiline = readBool()
                else -> skip(fieldHeader and 7)
            }
        }
        return ObjectLiteral(entry, multiline)
    }

    fun readObjectLiteralEntry(): ObjectLiteralEntry {
        var key: Expression? = null
        var value: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> key = readWithLength { readExpression() }
                18 -> value = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return ObjectLiteralEntry(key!!, value!!)
    }

    fun readFunction(): Function {
        var parameter: MutableList<Parameter> = mutableListOf()
        var nameId: Int? = null
        var body: Statement? = null
        var local: Boolean = false
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> parameter.add(readWithLength { readParameter() })
                16 -> nameId = readInt32()
                26 -> body = readWithLength { readStatement() }
                32 -> local = readBool()
                else -> skip(fieldHeader and 7)
            }
        }
        return Function(parameter, nameId, body!!, local)
    }

    fun readParameter(): Parameter {
        var nameId: Int = 0
        var hasDefaultValue: Boolean = false
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> nameId = readInt32()
                16 -> hasDefaultValue = readBool()
                else -> skip(fieldHeader and 7)
            }
        }
        return Parameter(nameId, hasDefaultValue)
    }

    fun readDocComment(): DocComment {
        var tag: MutableList<DocCommentTag> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> tag.add(readWithLength { readDocCommentTag() })
                else -> skip(fieldHeader and 7)
            }
        }
        return DocComment(tag)
    }

    fun readDocCommentTag(): DocCommentTag {
        var nameId: Int = 0
        var valueStringId: Int? = null
        var expression: Expression? = null
        var oneOfCase: DocCommentTag.ValueCase = DocCommentTag.ValueCase.VALUE_NOT_SET
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> nameId = readInt32()
                16 -> {
                    valueStringId = readInt32()
                    oneOfCase = DocCommentTag.ValueCase.VALUE_STRING_ID
                }
                26 -> {
                    expression = readWithLength { readExpression() }
                    oneOfCase = DocCommentTag.ValueCase.EXPRESSION
                }
                else -> skip(fieldHeader and 7)
            }
        }
        return DocCommentTag(nameId, oneOfCase!!, valueStringId, expression)
    }

    fun readBinaryOperation(): BinaryOperation {
        var left: Expression? = null
        var right: Expression? = null
        var type: BinaryOperation.Type? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> left = readWithLength { readExpression() }
                18 -> right = readWithLength { readExpression() }
                24 -> type = BinaryOperation.Type.fromIndex(readInt32())
                else -> skip(fieldHeader and 7)
            }
        }
        return BinaryOperation(left!!, right!!, type!!)
    }

    fun readUnaryOperation(): UnaryOperation {
        var operand: Expression? = null
        var type: UnaryOperation.Type? = null
        var postfix: Boolean = false
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> operand = readWithLength { readExpression() }
                16 -> type = UnaryOperation.Type.fromIndex(readInt32())
                24 -> postfix = readBool()
                else -> skip(fieldHeader and 7)
            }
        }
        return UnaryOperation(operand!!, type!!, postfix)
    }

    fun readConditional(): Conditional {
        var testExpression: Expression? = null
        var thenExpression: Expression? = null
        var elseExpression: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> testExpression = readWithLength { readExpression() }
                18 -> thenExpression = readWithLength { readExpression() }
                26 -> elseExpression = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Conditional(testExpression!!, thenExpression!!, elseExpression!!)
    }

    fun readArrayAccess(): ArrayAccess {
        var array: Expression? = null
        var index: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> array = readWithLength { readExpression() }
                18 -> index = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return ArrayAccess(array!!, index!!)
    }

    fun readNameReference(): NameReference {
        var nameId: Int = 0
        var qualifier: Expression? = null
        var inlineStrategy: InlineStrategy = InlineStrategy.NOT_INLINE
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> nameId = readInt32()
                18 -> qualifier = readWithLength { readExpression() }
                24 -> inlineStrategy = InlineStrategy.fromIndex(readInt32())
                else -> skip(fieldHeader and 7)
            }
        }
        return NameReference(nameId, qualifier, inlineStrategy)
    }

    fun readPropertyReference(): PropertyReference {
        var stringId: Int = 0
        var qualifier: Expression? = null
        var inlineStrategy: InlineStrategy = InlineStrategy.NOT_INLINE
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> stringId = readInt32()
                18 -> qualifier = readWithLength { readExpression() }
                24 -> inlineStrategy = InlineStrategy.fromIndex(readInt32())
                else -> skip(fieldHeader and 7)
            }
        }
        return PropertyReference(stringId, qualifier, inlineStrategy)
    }

    fun readInvocation(): Invocation {
        var qualifier: Expression? = null
        var argument: MutableList<Expression> = mutableListOf()
        var inlineStrategy: InlineStrategy = InlineStrategy.NOT_INLINE
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> qualifier = readWithLength { readExpression() }
                18 -> argument.add(readWithLength { readExpression() })
                24 -> inlineStrategy = InlineStrategy.fromIndex(readInt32())
                else -> skip(fieldHeader and 7)
            }
        }
        return Invocation(qualifier!!, argument, inlineStrategy)
    }

    fun readInstantiation(): Instantiation {
        var qualifier: Expression? = null
        var argument: MutableList<Expression> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> qualifier = readWithLength { readExpression() }
                18 -> argument.add(readWithLength { readExpression() })
                else -> skip(fieldHeader and 7)
            }
        }
        return Instantiation(qualifier!!, argument)
    }

    fun readStatement(): Statement {
        var fileId: Int? = null
        var location: Location? = null
        var synthetic: Boolean = false
        var returnStatement: Return? = null
        var throwStatement: Throw? = null
        var breakStatement: Break? = null
        var continueStatement: Continue? = null
        var debugger: Debugger? = null
        var expression: ExpressionStatement? = null
        var vars: Vars? = null
        var block: Block? = null
        var globalBlock: GlobalBlock? = null
        var label: Label? = null
        var ifStatement: If? = null
        var switchStatement: Switch? = null
        var whileStatement: While? = null
        var doWhileStatement: DoWhile? = null
        var forStatement: For? = null
        var forInStatement: ForIn? = null
        var tryStatement: Try? = null
        var empty: Empty? = null
        var oneOfCase: Statement.StatementCase = Statement.StatementCase.STATEMENT_NOT_SET
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> fileId = readInt32()
                18 -> location = readWithLength { readLocation() }
                24 -> synthetic = readBool()
                170 -> {
                    returnStatement = readWithLength { readReturn() }
                    oneOfCase = Statement.StatementCase.RETURN_STATEMENT
                }
                178 -> {
                    throwStatement = readWithLength { readThrow() }
                    oneOfCase = Statement.StatementCase.THROW_STATEMENT
                }
                186 -> {
                    breakStatement = readWithLength { readBreak() }
                    oneOfCase = Statement.StatementCase.BREAK_STATEMENT
                }
                194 -> {
                    continueStatement = readWithLength { readContinue() }
                    oneOfCase = Statement.StatementCase.CONTINUE_STATEMENT
                }
                202 -> {
                    debugger = readWithLength { readDebugger() }
                    oneOfCase = Statement.StatementCase.DEBUGGER
                }
                210 -> {
                    expression = readWithLength { readExpressionStatement() }
                    oneOfCase = Statement.StatementCase.EXPRESSION
                }
                218 -> {
                    vars = readWithLength { readVars() }
                    oneOfCase = Statement.StatementCase.VARS
                }
                226 -> {
                    block = readWithLength { readBlock() }
                    oneOfCase = Statement.StatementCase.BLOCK
                }
                234 -> {
                    globalBlock = readWithLength { readGlobalBlock() }
                    oneOfCase = Statement.StatementCase.GLOBAL_BLOCK
                }
                242 -> {
                    label = readWithLength { readLabel() }
                    oneOfCase = Statement.StatementCase.LABEL
                }
                250 -> {
                    ifStatement = readWithLength { readIf() }
                    oneOfCase = Statement.StatementCase.IF_STATEMENT
                }
                258 -> {
                    switchStatement = readWithLength { readSwitch() }
                    oneOfCase = Statement.StatementCase.SWITCH_STATEMENT
                }
                266 -> {
                    whileStatement = readWithLength { readWhile() }
                    oneOfCase = Statement.StatementCase.WHILE_STATEMENT
                }
                274 -> {
                    doWhileStatement = readWithLength { readDoWhile() }
                    oneOfCase = Statement.StatementCase.DO_WHILE_STATEMENT
                }
                282 -> {
                    forStatement = readWithLength { readFor() }
                    oneOfCase = Statement.StatementCase.FOR_STATEMENT
                }
                290 -> {
                    forInStatement = readWithLength { readForIn() }
                    oneOfCase = Statement.StatementCase.FOR_IN_STATEMENT
                }
                298 -> {
                    tryStatement = readWithLength { readTry() }
                    oneOfCase = Statement.StatementCase.TRY_STATEMENT
                }
                306 -> {
                    empty = readWithLength { readEmpty() }
                    oneOfCase = Statement.StatementCase.EMPTY
                }
                else -> skip(fieldHeader and 7)
            }
        }
        return Statement(fileId, location, synthetic, oneOfCase!!, returnStatement, throwStatement, breakStatement, continueStatement, debugger, expression, vars, block, globalBlock, label, ifStatement, switchStatement, whileStatement, doWhileStatement, forStatement, forInStatement, tryStatement, empty)
    }

    fun readReturn(): Return {
        var value: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> value = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Return(value)
    }

    fun readThrow(): Throw {
        var exception: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> exception = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Throw(exception!!)
    }

    fun readBreak(): Break {
        var labelId: Int? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> labelId = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return Break(labelId)
    }

    fun readContinue(): Continue {
        var labelId: Int? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> labelId = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return Continue(labelId)
    }

    fun readDebugger(): Debugger {
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                else -> skip(fieldHeader and 7)
            }
        }
        return Debugger()
    }

    fun readExpressionStatement(): ExpressionStatement {
        var expression: Expression? = null
        var exportedTagId: Int? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> expression = readWithLength { readExpression() }
                16 -> exportedTagId = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return ExpressionStatement(expression!!, exportedTagId)
    }

    fun readVars(): Vars {
        var declaration: MutableList<VarDeclaration> = mutableListOf()
        var multiline: Boolean = false
        var exportedPackageId: Int? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> declaration.add(readWithLength { readVarDeclaration() })
                16 -> multiline = readBool()
                24 -> exportedPackageId = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return Vars(declaration, multiline, exportedPackageId)
    }

    fun readVarDeclaration(): VarDeclaration {
        var nameId: Int = 0
        var initialValue: Expression? = null
        var fileId: Int? = null
        var location: Location? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> nameId = readInt32()
                18 -> initialValue = readWithLength { readExpression() }
                24 -> fileId = readInt32()
                34 -> location = readWithLength { readLocation() }
                else -> skip(fieldHeader and 7)
            }
        }
        return VarDeclaration(nameId, initialValue, fileId, location)
    }

    fun readBlock(): Block {
        var statement: MutableList<Statement> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> statement.add(readWithLength { readStatement() })
                else -> skip(fieldHeader and 7)
            }
        }
        return Block(statement)
    }

    fun readGlobalBlock(): GlobalBlock {
        var statement: MutableList<Statement> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> statement.add(readWithLength { readStatement() })
                else -> skip(fieldHeader and 7)
            }
        }
        return GlobalBlock(statement)
    }

    fun readLabel(): Label {
        var nameId: Int = 0
        var innerStatement: Statement? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> nameId = readInt32()
                18 -> innerStatement = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Label(nameId, innerStatement!!)
    }

    fun readIf(): If {
        var condition: Expression? = null
        var thenStatement: Statement? = null
        var elseStatement: Statement? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> condition = readWithLength { readExpression() }
                18 -> thenStatement = readWithLength { readStatement() }
                26 -> elseStatement = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return If(condition!!, thenStatement!!, elseStatement)
    }

    fun readSwitch(): Switch {
        var expression: Expression? = null
        var entry: MutableList<SwitchEntry> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> expression = readWithLength { readExpression() }
                18 -> entry.add(readWithLength { readSwitchEntry() })
                else -> skip(fieldHeader and 7)
            }
        }
        return Switch(expression!!, entry)
    }

    fun readSwitchEntry(): SwitchEntry {
        var label: Expression? = null
        var statement: MutableList<Statement> = mutableListOf()
        var fileId: Int? = null
        var location: Location? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> label = readWithLength { readExpression() }
                18 -> statement.add(readWithLength { readStatement() })
                24 -> fileId = readInt32()
                34 -> location = readWithLength { readLocation() }
                else -> skip(fieldHeader and 7)
            }
        }
        return SwitchEntry(label, statement, fileId, location)
    }

    fun readWhile(): While {
        var condition: Expression? = null
        var body: Statement? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> condition = readWithLength { readExpression() }
                18 -> body = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return While(condition!!, body!!)
    }

    fun readDoWhile(): DoWhile {
        var condition: Expression? = null
        var body: Statement? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> condition = readWithLength { readExpression() }
                18 -> body = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return DoWhile(condition!!, body!!)
    }

    fun readFor(): For {
        var variables: Statement? = null
        var expression: Expression? = null
        var empty: EmptyInit? = null
        var condition: Expression? = null
        var increment: Expression? = null
        var body: Statement? = null
        var oneOfCase: For.InitCase = For.InitCase.INIT_NOT_SET
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> {
                    variables = readWithLength { readStatement() }
                    oneOfCase = For.InitCase.VARIABLES
                }
                18 -> {
                    expression = readWithLength { readExpression() }
                    oneOfCase = For.InitCase.EXPRESSION
                }
                26 -> {
                    empty = readWithLength { readEmptyInit() }
                    oneOfCase = For.InitCase.EMPTY
                }
                34 -> condition = readWithLength { readExpression() }
                42 -> increment = readWithLength { readExpression() }
                50 -> body = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return For(oneOfCase!!, variables, expression, empty, condition, increment, body!!)
    }

    fun readEmptyInit(): EmptyInit {
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                else -> skip(fieldHeader and 7)
            }
        }
        return EmptyInit()
    }

    fun readForIn(): ForIn {
        var nameId: Int? = null
        var expression: Expression? = null
        var iterable: Expression? = null
        var body: Statement? = null
        var oneOfCase: ForIn.ValueCase = ForIn.ValueCase.VALUE_NOT_SET
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> {
                    nameId = readInt32()
                    oneOfCase = ForIn.ValueCase.NAMEID
                }
                18 -> {
                    expression = readWithLength { readExpression() }
                    oneOfCase = ForIn.ValueCase.EXPRESSION
                }
                26 -> iterable = readWithLength { readExpression() }
                34 -> body = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return ForIn(oneOfCase!!, nameId, expression, iterable!!, body!!)
    }

    fun readTry(): Try {
        var tryBlock: Statement? = null
        var catchBlock: Catch? = null
        var finallyBlock: Statement? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> tryBlock = readWithLength { readStatement() }
                18 -> catchBlock = readWithLength { readCatch() }
                26 -> finallyBlock = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Try(tryBlock!!, catchBlock, finallyBlock)
    }

    fun readCatch(): Catch {
        var parameter: Parameter? = null
        var body: Statement? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> parameter = readWithLength { readParameter() }
                18 -> body = readWithLength { readStatement() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Catch(parameter!!, body!!)
    }

    fun readEmpty(): Empty {
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                else -> skip(fieldHeader and 7)
            }
        }
        return Empty()
    }

    fun readFragment(): Fragment {
        var importedModule: MutableList<ImportedModule> = mutableListOf()
        var importEntry: MutableList<Import> = mutableListOf()
        var declarationBlock: GlobalBlock? = null
        var exportBlock: GlobalBlock? = null
        var initializerBlock: GlobalBlock? = null
        var nameBinding: MutableList<NameBinding> = mutableListOf()
        var classModel: MutableList<ClassModel> = mutableListOf()
        var moduleExpression: MutableList<Expression> = mutableListOf()
        var inlineModule: MutableList<InlineModule> = mutableListOf()
        var packageFqn: String? = null
        var testsInvocation: Statement? = null
        var mainInvocation: Statement? = null
        var inlinedLocalDeclarations: MutableList<InlinedLocalDeclarations> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> importedModule.add(readWithLength { readImportedModule() })
                18 -> importEntry.add(readWithLength { readImport() })
                26 -> declarationBlock = readWithLength { readGlobalBlock() }
                34 -> exportBlock = readWithLength { readGlobalBlock() }
                42 -> initializerBlock = readWithLength { readGlobalBlock() }
                50 -> nameBinding.add(readWithLength { readNameBinding() })
                58 -> classModel.add(readWithLength { readClassModel() })
                66 -> moduleExpression.add(readWithLength { readExpression() })
                74 -> inlineModule.add(readWithLength { readInlineModule() })
                82 -> packageFqn = readString()
                90 -> testsInvocation = readWithLength { readStatement() }
                98 -> mainInvocation = readWithLength { readStatement() }
                106 -> inlinedLocalDeclarations.add(readWithLength { readInlinedLocalDeclarations() })
                else -> skip(fieldHeader and 7)
            }
        }
        return Fragment(importedModule, importEntry, declarationBlock, exportBlock, initializerBlock, nameBinding, classModel, moduleExpression, inlineModule, packageFqn, testsInvocation, mainInvocation, inlinedLocalDeclarations)
    }

    fun readInlinedLocalDeclarations(): InlinedLocalDeclarations {
        var tag: Int = 0
        var block: GlobalBlock? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> tag = readInt32()
                18 -> block = readWithLength { readGlobalBlock() }
                else -> skip(fieldHeader and 7)
            }
        }
        return InlinedLocalDeclarations(tag, block!!)
    }

    fun readImportedModule(): ImportedModule {
        var externalNameId: Int = 0
        var internalNameId: Int = 0
        var plainReference: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> externalNameId = readInt32()
                16 -> internalNameId = readInt32()
                26 -> plainReference = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return ImportedModule(externalNameId, internalNameId, plainReference)
    }

    fun readImport(): Import {
        var signatureId: Int = 0
        var expression: Expression? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> signatureId = readInt32()
                18 -> expression = readWithLength { readExpression() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Import(signatureId, expression!!)
    }

    fun readNameBinding(): NameBinding {
        var signatureId: Int = 0
        var nameId: Int = 0
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> signatureId = readInt32()
                16 -> nameId = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return NameBinding(signatureId, nameId)
    }

    fun readClassModel(): ClassModel {
        var nameId: Int = 0
        var superNameId: Int? = null
        var interfaceNameId: MutableList<Int> = mutableListOf()
        var postDeclarationBlock: GlobalBlock? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> nameId = readInt32()
                16 -> superNameId = readInt32()
                32 -> interfaceNameId.add(readInt32())
                26 -> postDeclarationBlock = readWithLength { readGlobalBlock() }
                else -> skip(fieldHeader and 7)
            }
        }
        return ClassModel(nameId, superNameId, interfaceNameId, postDeclarationBlock)
    }

    fun readInlineModule(): InlineModule {
        var signatureId: Int = 0
        var expressionId: Int = 0
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> signatureId = readInt32()
                16 -> expressionId = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return InlineModule(signatureId, expressionId)
    }

    fun readStringTable(): StringTable {
        var entry: MutableList<String> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> entry.add(readString())
                else -> skip(fieldHeader and 7)
            }
        }
        return StringTable(entry)
    }

    fun readNameTable(): NameTable {
        var entry: MutableList<Name> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> entry.add(readWithLength { readName() })
                else -> skip(fieldHeader and 7)
            }
        }
        return NameTable(entry)
    }

    fun readName(): Name {
        var temporary: Boolean = false
        var identifier: Int? = null
        var localNameId: LocalAlias? = null
        var imported: Boolean = false
        var specialFunction: SpecialFunction? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> temporary = readBool()
                16 -> identifier = readInt32()
                26 -> localNameId = readWithLength { readLocalAlias() }
                32 -> imported = readBool()
                40 -> specialFunction = SpecialFunction.fromIndex(readInt32())
                else -> skip(fieldHeader and 7)
            }
        }
        return Name(temporary, identifier, localNameId, imported, specialFunction)
    }

    fun readLocalAlias(): LocalAlias {
        var localNameId: Int = 0
        var tag: Int? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                8 -> localNameId = readInt32()
                16 -> tag = readInt32()
                else -> skip(fieldHeader and 7)
            }
        }
        return LocalAlias(localNameId, tag)
    }

    fun readChunk(): Chunk {
        var stringTable: StringTable? = null
        var nameTable: NameTable? = null
        var fragment: Fragment? = null
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> stringTable = readWithLength { readStringTable() }
                18 -> nameTable = readWithLength { readNameTable() }
                26 -> fragment = readWithLength { readFragment() }
                else -> skip(fieldHeader and 7)
            }
        }
        return Chunk(stringTable!!, nameTable!!, fragment!!)
    }

    fun readInlineData(): InlineData {
        var inlineFunctionTags: MutableList<String> = mutableListOf()
        while (hasData) {
            when (val fieldHeader = readInt32()) {
                10 -> inlineFunctionTags.add(readString())
                else -> skip(fieldHeader and 7)
            }
        }
        return InlineData(inlineFunctionTags)
    }

}

