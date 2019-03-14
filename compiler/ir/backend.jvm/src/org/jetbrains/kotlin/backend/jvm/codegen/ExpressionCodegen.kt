/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.codegen

import org.jetbrains.kotlin.backend.common.ir.ir2string
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.backend.jvm.intrinsics.ComparisonIntrinsic
import org.jetbrains.kotlin.backend.jvm.intrinsics.IrIntrinsicFunction
import org.jetbrains.kotlin.backend.jvm.intrinsics.IrIntrinsicMethods
import org.jetbrains.kotlin.backend.jvm.lower.CrIrType
import org.jetbrains.kotlin.backend.jvm.lower.JvmBuiltinOptimizationLowering.Companion.isNegation
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.AsmUtil.*
import org.jetbrains.kotlin.codegen.ExpressionCodegen.putReifiedOperationMarkerIfTypeIsReifiedParameter
import org.jetbrains.kotlin.codegen.StackValue.*
import org.jetbrains.kotlin.codegen.inline.*
import org.jetbrains.kotlin.codegen.intrinsics.JavaClassProperty
import org.jetbrains.kotlin.codegen.pseudoInsns.fakeAlwaysFalseIfeq
import org.jetbrains.kotlin.codegen.pseudoInsns.fakeAlwaysTrueIfeq
import org.jetbrains.kotlin.codegen.pseudoInsns.fixStackAndJump
import org.jetbrains.kotlin.codegen.signature.BothSignatureWriter
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.isReleaseCoroutines
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.impl.IrSimpleTypeImpl
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.util.isNullConst
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.AsmTypes.JAVA_THROWABLE_TYPE
import org.jetbrains.kotlin.resolve.jvm.AsmTypes.OBJECT_TYPE
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.keysToMap
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import java.util.*

open class ExpressionInfo(val expression: IrExpression)

class LoopInfo(val loop: IrLoop, val continueLabel: Label, val breakLabel: Label) : ExpressionInfo(loop)

class TryInfo(val tryBlock: IrTry) : ExpressionInfo(tryBlock) {
    val gaps = mutableListOf<Label>()
}

class BlockInfo private constructor(val parent: BlockInfo?) {
    val variables: MutableList<VariableInfo> = mutableListOf()

    private val infos = Stack<ExpressionInfo>()

    fun create() = BlockInfo(this).apply {
        this@apply.infos.addAll(this@BlockInfo.infos)
    }

    fun addInfo(loop: ExpressionInfo) {
        infos.add(loop)
    }

    fun removeInfo(info: ExpressionInfo) {
        assert(peek() == info)
        pop()
    }

    fun pop(): ExpressionInfo = infos.pop()

    fun peek(): ExpressionInfo = infos.peek()

    fun isEmpty(): Boolean = infos.isEmpty()

    fun hasFinallyBlocks(): Boolean = infos.firstIsInstanceOrNull<TryInfo>() != null

    companion object {
        fun create() = BlockInfo(null)
    }
}

class VariableInfo(val declaration: IrVariable, val index: Int, val type: Type, val startLabel: Label)

@Suppress("IMPLICIT_CAST_TO_ANY")
class ExpressionCodegen(
    val irFunction: IrFunction,
    val frame: IrFrameMap,
    val mv: InstructionAdapter,
    val classCodegen: ClassCodegen
) : IrElementVisitor<StackValue, BlockInfo>, BaseExpressionCodegen {

    /*TODO*/
    val context = classCodegen.context

    val intrinsics = IrIntrinsicMethods(context.irBuiltIns)

    val typeMapper = classCodegen.typeMapper

    val returnType = typeMapper.mapReturnType(irFunction)

    val state = classCodegen.state

    private val sourceManager = classCodegen.context.psiSourceManager

    private val fileEntry = sourceManager.getFileEntry(irFunction.fileParent)

    fun generate() {
        mv.visitCode()
        val startLabel = markNewLabel()
        val info = BlockInfo.create()
        val result = irFunction.body!!.accept(this, info)
        markFunctionLineNumber()
        val returnType = typeMapper.mapReturnType(irFunction)
        val body = irFunction.body!!
        // If this function has an expression body, return the result of that expression.
        // Otherwise, if it does not end in a return statement, it must be void-returning,
        // and an explicit return instruction at the end is still required to pass validation.
        if (body !is IrStatementContainer || body.statements.lastOrNull() !is IrReturn) {
            coerce(result.type, returnType, mv)
            mv.areturn(returnType)
        }
        writeLocalVariablesInTable(info)
        writeParameterInLocalVariableTable(startLabel)
        mv.visitEnd()
    }

    private fun markFunctionLineNumber() {
        if (irFunction.origin == JvmLoweredDeclarationOrigin.CLASS_STATIC_INITIALIZER) {
            return
        }
        if (irFunction is IrConstructor && irFunction.isPrimary) {
            irFunction.markLineNumber(startOffset = true)
            return
        }
        val lastElement = irFunction.body!!.getLastElement()
        if (lastElement !is IrReturn) {
            irFunction.markLineNumber(startOffset = false)
        }
    }

    private fun IrElement.getLastElement(): IrElement {
        return when (this) {
            is IrStatementContainer -> if (this.statements.isEmpty()) this else this.statements[this.statements.size - 1].getLastElement()
            is IrExpressionBody -> this.expression.getLastElement()
            else -> this
        }
    }

    private fun writeParameterInLocalVariableTable(startLabel: Label) {
        if (!irFunction.isStatic) {
            mv.visitLocalVariable("this", classCodegen.type.descriptor, null, startLabel, markNewLabel(), 0)
        }
        val extensionReceiverParameter = irFunction.extensionReceiverParameter
        if (extensionReceiverParameter != null) {
            writeValueParameterInLocalVariableTable(extensionReceiverParameter, startLabel)
        }
        for (param in irFunction.valueParameters) {
            writeValueParameterInLocalVariableTable(param, startLabel)
        }
    }

    private fun writeValueParameterInLocalVariableTable(param: IrValueParameter, startLabel: Label) {
        // TODO: old code has a special treatment for destructuring lambda parameters.
        // There is no (easy) way to reproduce it with IR structures.
        // Does not show up in tests, but might come to bite us at some point.
        val name = param.name.asString()

        val type = typeMapper.mapType(param)
        // NOTE: we expect all value parameters to be present in the frame.
        mv.visitLocalVariable(
            name, type.descriptor, null, startLabel, markNewLabel(), findLocalIndex(param.symbol)
        )
    }

    private fun StackValue.discard(): StackValue {
        coerce(type, Type.VOID_TYPE, mv)
        return none()
    }

    override fun visitBlock(expression: IrBlock, data: BlockInfo): StackValue {
        val info = data.create()
        return super.visitBlock(expression, info).apply {
            if (!expression.isTransparentScope) {
                writeLocalVariablesInTable(info)
            } else {
                info.variables.forEach {
                    data.variables.add(it)
                }
            }
        }
    }

    private fun writeLocalVariablesInTable(info: BlockInfo) {
        val endLabel = markNewLabel()
        info.variables.forEach {
            when (it.declaration.origin) {
                IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                IrDeclarationOrigin.FOR_LOOP_ITERATOR,
                IrDeclarationOrigin.FOR_LOOP_IMPLICIT_VARIABLE -> {
                    // Ignore implicitly created variables
                }
                else -> {
                    mv.visitLocalVariable(
                        it.declaration.name.asString(), it.type.descriptor, null, it.startLabel, endLabel, it.index
                    )
                }
            }
        }

        info.variables.reversed().forEach {
            frame.leave(it.declaration.symbol)
        }
    }

    private fun visitStatementContainer(container: IrStatementContainer, data: BlockInfo): StackValue {
        return container.statements.fold(none()) { prev, exp ->
            prev.discard()
            gen(exp, data).also {
                (exp as? IrExpression)?.markEndOfStatementIfNeeded()
            }
        }
    }

    override fun visitBlockBody(body: IrBlockBody, data: BlockInfo): StackValue {
        return visitStatementContainer(body, data).discard()
    }

    override fun visitContainerExpression(expression: IrContainerExpression, data: BlockInfo): StackValue {
        val result = visitStatementContainer(expression, data)
        return coerceNotToUnit(result.type, result.kotlinType?.toIrType(), expression.type)
    }

    override fun visitMemberAccess(expression: IrMemberAccessExpression, data: BlockInfo): StackValue {
        assert(expression is IrFunctionAccessExpression) { "No callable references should survive at this point, got ${ir2string(expression)}" }
        expression.markLineNumber(startOffset = true)
        return generateCall(expression as IrFunctionAccessExpression, null, data)
    }

    override fun visitCall(expression: IrCall, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        if (expression.symbol.owner is IrConstructor) {
            return generateNewCall(expression, data)
        }
        return generateCall(expression, expression.superQualifierSymbol, data)
    }

    private fun generateNewCall(expression: IrCall, data: BlockInfo): StackValue {
        val type = expression.asmType
        if (type.sort == Type.ARRAY) {
            //noinspection ConstantConditions
            return generateNewArray(expression, data)
        }

        mv.anew(expression.asmType)
        mv.dup()
        generateCall(expression, expression.superQualifierSymbol, data)
        return expression.onStack
    }

    fun generateNewArray(
        expression: IrCall, data: BlockInfo
    ): StackValue {
        val args = expression.symbol.owner.valueParameters
        assert(args.size == 1 || args.size == 2) { "Unknown constructor called: " + args.size + " arguments" }

        if (args.size == 1) {
            val sizeExpression = expression.getValueArgument(0)!!
            gen(sizeExpression, Type.INT_TYPE, data)
            newArrayInstruction(expression.type)
            return expression.onStack
        }

        return generateCall(expression, expression.superQualifierSymbol, data)
    }

    private fun generateCall(expression: IrFunctionAccessExpression, superQualifierSymbol: IrClassSymbol?, data: BlockInfo): StackValue {
        val isSuperCall = superQualifierSymbol != null
        val callable = resolveToCallable(expression, isSuperCall)
        return if (callable is IrIntrinsicFunction) {
            callable.invoke(mv, this, data)
        } else {
            generateCall(expression, callable, data, isSuperCall)
        }
    }

    fun generateCall(expression: IrFunctionAccessExpression, callable: Callable, data: BlockInfo, isSuperCall: Boolean = false): StackValue {
        val callee = expression.symbol.owner
        val callGenerator = getOrCreateCallGenerator(expression)

        val receiver = expression.dispatchReceiver
        receiver?.apply {
            callGenerator.genValueAndPut(
                null, this,
                if (isSuperCall) receiver.asmType else callable.dispatchReceiverType!!,
                -1, this@ExpressionCodegen, data
            )
        }

        expression.extensionReceiver?.apply {
            callGenerator.genValueAndPut(null, this, callable.extensionReceiverType!!, -1, this@ExpressionCodegen, data)
        }

        callGenerator.beforeValueParametersStart()
        val defaultMask = DefaultCallArgs(callable.valueParameterTypes.size)
        val extraArgsShift =
            when {
                callee is IrConstructor && callee.parentAsClass.isEnumClass -> 2
                callee is IrConstructor && callee.parentAsClass.isInner -> 1 // skip the `$outer` parameter
                else -> 0
            }
        val typeParameters = if (callee is IrConstructor)
            callee.parentAsClass.typeParameters + callee.typeParameters
        else
            callee.typeParameters
        val typeArguments = (0 until typeParameters.size).map { expression.getTypeArgument(it)!! }
        val typeSubstitutionMap = typeParameters.map { it.symbol }.zip(typeArguments).toMap()
        expression.symbol.owner.valueParameters.forEachIndexed { i, irParameter ->
            val arg = expression.getValueArgument(i)
            val parameterType = callable.valueParameterTypes[i]
            when {
                arg != null -> {
                    callGenerator.genValueAndPut(irParameter, arg, parameterType, i, this@ExpressionCodegen, data)
                }
                irParameter.hasDefaultValue() -> {
                    callGenerator.putValueIfNeeded(
                        parameterType,
                        StackValue.createDefaultValue(parameterType),
                        ValueKind.DEFAULT_PARAMETER,
                        i,
                        this@ExpressionCodegen
                    )
                    defaultMask.mark(i - extraArgsShift/*TODO switch to separate lower*/)
                }
                else -> {
                    assert(irParameter.varargElementType != null)
                    val type = typeMapper.mapType(
                        irParameter.type.substitute(typeSubstitutionMap)
                    )
                    callGenerator.putValueIfNeeded(
                        parameterType,
                        StackValue.operation(type) {
                            it.aconst(0)
                            it.newarray(correctElementType(type))
                        },
                        ValueKind.GENERAL_VARARG, i, this@ExpressionCodegen
                    )
                }
            }
        }

        callGenerator.genCall(
            callable,
            defaultMask.generateOnStackIfNeeded(callGenerator, callee is IrConstructor, this),
            this,
            expression
        )

        val returnType = callee.returnType.substitute(typeSubstitutionMap)
        if (returnType.isNothing()) {
            mv.aconst(null)
            mv.athrow()
            return expression.onStack
        } else if (callee is IrConstructor) {
            return none()
        }

        return coerceNotToUnit(callable.returnType, returnType, expression.type)
    }

    override fun visitInstanceInitializerCall(expression: IrInstanceInitializerCall, data: BlockInfo): StackValue {
        throw AssertionError("Instruction should've been lowered before code generation: ${expression.render()}")
    }

    override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: BlockInfo): StackValue {
        //HACK
        StackValue.local(0, OBJECT_TYPE).put(OBJECT_TYPE, mv)
        return super.visitDelegatingConstructorCall(expression, data)
    }

    override fun visitVariable(declaration: IrVariable, data: BlockInfo): StackValue {
        val varType = typeMapper.mapType(declaration)
        val index = frame.enter(declaration.symbol, varType)

        declaration.markLineNumber(startOffset = true)

        declaration.initializer?.apply {
            StackValue.local(index, varType).store(gen(this, varType, data), mv)
            this.markLineNumber(startOffset = true)
        }

        val info = VariableInfo(
            declaration,
            index,
            varType,
            markNewLabel()
        )
        data.variables.add(info)

        return none()
    }

    fun gen(expression: IrElement, type: Type, data: BlockInfo): StackValue {
        gen(expression, data).put(type, mv)
        return onStack(type)
    }

    fun gen(expression: IrElement, data: BlockInfo): StackValue {
        return expression.accept(this, data)
    }

    override fun visitGetValue(expression: IrGetValue, data: BlockInfo): StackValue {
        // Do not generate line number information for loads from compiler-generated
        // temporary variables. They do not correspond to variable loads in user code.
        if (expression.symbol.owner.origin != IrDeclarationOrigin.IR_TEMPORARY_VARIABLE)
            expression.markLineNumber(startOffset = true)
        return generateLocal(expression.symbol, expression.asmType)
    }

    private fun generateFieldValue(expression: IrFieldAccessExpression, data: BlockInfo): StackValue {
        val receiverValue = expression.receiver?.accept(this, data) ?: StackValue.none()

        val realField = expression.symbol.owner.resolveFakeOverride()!!
        val fieldType = typeMapper.mapType(realField)
        val ownerType = typeMapper.mapImplementationOwner(realField)
        val fieldName = realField.name.asString()
        val isStatic = realField.isStatic

        return StackValue.field(fieldType, realField.type.toKotlinType(), ownerType, fieldName, isStatic, receiverValue, realField.descriptor)
    }

    override fun visitGetField(expression: IrGetField, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        val value = generateFieldValue(expression, data)
        value.put(mv)
        return onStack(value.type)
    }

    override fun visitSetField(expression: IrSetField, data: BlockInfo): StackValue {
        val expressionValue = expression.value
        // Do not add redundant field initializers that initialize to default values.
        // "expression.origin == null" means that the field is initialized when it is declared,
        // i.e., not in an initializer block or constructor body.
        val skip = irFunction is IrConstructor && irFunction.isPrimary &&
                expression.origin == null && expressionValue is IrConst<*> &&
                isDefaultValueForType(expression.symbol.owner.type, expressionValue)
        if (!skip) {
            expression.markLineNumber(startOffset = true)
            val fieldValue = generateFieldValue(expression, data)
            fieldValue.store(expressionValue.accept(this, data), mv)
        }
        // Assignments can be used as expressions, so return a value. Redundant pushes
        // will be eliminated by the peephole optimizer.
        putUnitInstance(mv)
        return onStack(AsmTypes.UNIT_TYPE)
    }


    /**
     * Returns true if the given constant value is the JVM's default value for the given type.
     * See: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-2.html#jvms-2.3
     */
    private fun isDefaultValueForType(fieldType: IrType, constExpression: IrConst<*>): Boolean {
        val value = constExpression.value
        val type = constExpression.asmType
        if (isPrimitive(type)) {
            if (!fieldType.isMarkedNullable() && value is Number) {
                if (type in setOf(Type.INT_TYPE, Type.BYTE_TYPE, Type.LONG_TYPE, Type.SHORT_TYPE) && value.toLong() == 0L) {
                    return true
                }
                if (type === Type.DOUBLE_TYPE && value.toDouble().equals(0.0)) {
                    return true
                }
                if (type === Type.FLOAT_TYPE && value.toFloat().equals(0.0f)) {
                    return true
                }
            }
            if (type === Type.BOOLEAN_TYPE && value is Boolean && !value) {
                return true
            }
            if (type === Type.CHAR_TYPE && value is Char && value.toInt() == 0) {
                return true
            }
        } else if (value == null) {
            return true
        }
        return false
    }

    private fun generateLocal(symbol: IrSymbol, type: Type): StackValue {
        val index = findLocalIndex(symbol)
        StackValue.local(index, type).put(mv)
        return onStack(type)
    }

    private fun findLocalIndex(irSymbol: IrSymbol): Int {
        return frame.getIndex(irSymbol).apply {
            if (this < 0) {
                if (irFunction.dispatchReceiverParameter != null) {
                    (irFunction.parent as? IrClass)?.takeIf { it.thisReceiver?.symbol == irSymbol }?.let { return 0 }
                }
                throw AssertionError(
                    "Non-mapped local declaration: " +
                            "${if (irSymbol.isBound) irSymbol.owner.dump() else irSymbol.descriptor} \n in ${irFunction.dump()}"
                )
            }
        }
    }

    override fun visitGetObjectValue(expression: IrGetObjectValue, data: BlockInfo): StackValue {
        throw AssertionError("Instruction should've been lowered before code generation: ${expression.render()}")
    }

    override fun visitGetEnumValue(expression: IrGetEnumValue, data: BlockInfo): StackValue {
        throw AssertionError("Instruction should've been lowered before code generation: ${expression.render()}")
    }

    override fun visitSetVariable(expression: IrSetVariable, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        expression.value.markLineNumber(startOffset = true)
        val value = expression.value.accept(this, data)
        StackValue.local(findLocalIndex(expression.symbol), expression.symbol.owner.asmType).store(value, mv)
        // Assignments can be used as expressions, so return a value. Redundant pushes
        // will be eliminated by the peephole optimizer.
        putUnitInstance(mv)
        return onStack(AsmTypes.UNIT_TYPE)
    }

    override fun <T> visitConst(expression: IrConst<T>, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        val value = expression.value
        val type = expression.asmType
        StackValue.constant(value, type).put(mv)
        return expression.onStack
    }

    override fun visitExpressionBody(body: IrExpressionBody, data: BlockInfo): StackValue {
        return body.expression.accept(this, data)
    }

    override fun visitElement(element: IrElement, data: BlockInfo): StackValue {
        TODO("not implemented for $element") //To change body of created functions use File | Settings | File Templates.
    }

    override fun visitClass(declaration: IrClass, data: BlockInfo): StackValue {
        classCodegen.generateLocalClass(declaration)
        return none()
    }

    override fun visitVararg(expression: IrVararg, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        val outType = expression.type
        val type = expression.asmType
        assert(type.sort == Type.ARRAY)
        val elementType = correctElementType(type)
        val arguments = expression.elements
        val size = arguments.size

        val hasSpread = arguments.firstIsInstanceOrNull<IrSpreadElement>() != null

        if (hasSpread) {
            val arrayOfReferences = outType.makeNotNull().isArray()
            if (size == 1) {
                // Arrays.copyOf(receiverValue, newLength)
                val argument = (arguments[0] as IrSpreadElement).expression
                val arrayType = if (arrayOfReferences)
                    Type.getType("[Ljava/lang/Object;")
                else
                    Type.getType("[" + elementType.descriptor)
                gen(argument, type, data)
                mv.dup()
                mv.arraylength()
                mv.invokestatic("java/util/Arrays", "copyOf", Type.getMethodDescriptor(arrayType, arrayType, Type.INT_TYPE), false)
                if (arrayOfReferences) {
                    mv.checkcast(type)
                }
            } else {
                val owner: String
                val addDescriptor: String
                val toArrayDescriptor: String
                if (arrayOfReferences) {
                    owner = "kotlin/jvm/internal/SpreadBuilder"
                    addDescriptor = "(Ljava/lang/Object;)V"
                    toArrayDescriptor = "([Ljava/lang/Object;)[Ljava/lang/Object;"
                } else {
                    val spreadBuilderClassName =
                        AsmUtil.asmPrimitiveTypeToLangPrimitiveType(elementType)!!.typeName.identifier + "SpreadBuilder"
                    owner = "kotlin/jvm/internal/" + spreadBuilderClassName
                    addDescriptor = "(" + elementType.descriptor + ")V"
                    toArrayDescriptor = "()" + type.descriptor
                }
                mv.anew(Type.getObjectType(owner))
                mv.dup()
                mv.iconst(size)
                mv.invokespecial(owner, "<init>", "(I)V", false)
                for (i in 0..size - 1) {
                    mv.dup()
                    val argument = arguments[i]
                    if (argument is IrSpreadElement) {
                        gen(argument.expression, OBJECT_TYPE, data)
                        mv.invokevirtual(owner, "addSpread", "(Ljava/lang/Object;)V", false)
                    } else {
                        gen(argument, elementType, data)
                        mv.invokevirtual(owner, "add", addDescriptor, false)
                    }
                }
                if (arrayOfReferences) {
                    mv.dup()
                    mv.invokevirtual(owner, "size", "()I", false)
                    newArrayInstruction(outType)
                    mv.invokevirtual(owner, "toArray", toArrayDescriptor, false)
                    mv.checkcast(type)
                } else {
                    mv.invokevirtual(owner, "toArray", toArrayDescriptor, false)
                }
            }
        } else {
            mv.iconst(size)
            newArrayInstruction(expression.type)
            val elementIrType = outType.getArrayOrPrimitiveArrayElementType() ?: context.irBuiltIns.anyNType
            for ((i, element) in expression.elements.withIndex()) {
                mv.dup()
                StackValue.constant(i).put(mv)
                val rightSide = gen(element, elementType, data)
                StackValue
                    .arrayElement(
                        elementType,
                        elementIrType.toKotlinType(),
                        StackValue.onStack(elementType, outType.toKotlinType()),
                        StackValue.onStack(Type.INT_TYPE)
                    )
                    .store(rightSide, mv)
            }
        }
        return expression.onStack
    }

    fun newArrayInstruction(arrayType: IrType) {
        if (arrayType.isArray()) {
            val elementIrType = arrayType.safeAs<IrSimpleType>()!!.arguments[0].safeAs<IrTypeProjection>()!!.type
//            putReifiedOperationMarkerIfTypeIsReifiedParameter(
//                    elementJetType,
//                    ReifiedTypeInliner.OperationKind.NEW_ARRAY
//            )
            mv.newarray(boxType(elementIrType.asmType))
        } else {
            val type = typeMapper.mapType(arrayType)
            mv.newarray(correctElementType(type))
        }
    }

    override fun visitReturn(expression: IrReturn, data: BlockInfo): StackValue {
        val value = expression.value.apply {
            gen(this, returnType, data)
        }

        val afterReturnLabel = Label()
        generateFinallyBlocksIfNeeded(returnType, afterReturnLabel, data)

        expression.markLineNumber(startOffset = true)
        mv.areturn(returnType)
        mv.mark(afterReturnLabel)
        mv.nop()/*TODO check RESTORE_STACK_IN_TRY_CATCH processor*/
        return expression.onStack
    }

    override fun visitWhen(expression: IrWhen, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        SwitchGenerator(expression, data, this).generate()?.let { return it }

        val irType = expression.type
        val endLabel = Label()
        var exhaustive = false
        for (branch in expression.branches) {
            val elseLabel = Label()
            if (branch.condition.isFalseConst() || branch.condition.isTrueConst()) {
                // True or false conditions known at compile time need not be generated. A linenumber and nop are still required
                // for a debugger to break on the line of the condition.
                if (branch !is IrElseBranch) {
                    branch.condition.markLineNumber(startOffset = true)
                    mv.nop()
                }
                if (branch.condition.isFalseConst())
                    continue // The branch body is dead code.
            } else {
                genConditionalJumpWithOptimizationsIfPossible(branch.condition, data, elseLabel)
            }
            gen(branch.result, data).let {
                coerceNotToUnit(it.type, it.kotlinType?.toIrType(), irType)
            }
            if (branch.condition.isTrueConst()) {
                exhaustive = true
                break // The rest of the expression is dead code.
            }
            mv.goTo(endLabel)
            mv.mark(elseLabel)
        }

        if (!exhaustive) {
            // TODO: make all non-exhaustive `if`/`when` return Nothing.
            if (irType.isUnit())
                putUnitInstance(mv)
            else if (!irType.isNothing())
                throw AssertionError("non-exhaustive `if`/`when` wants to return $irType")
        }

        mv.mark(endLabel)
        return if (irType.isNothing()) none() else expression.onStack
    }

    private fun genConditionalJumpWithOptimizationsIfPossible(
        originalCondition: IrExpression,
        data: BlockInfo,
        jumpToLabel: Label,
        originalJumpIfFalse: Boolean = true
    ) {
        var condition = originalCondition
        var jumpIfFalse = originalJumpIfFalse

        // Instead of materializing a negated value when used for control flow, flip the branch
        // targets instead. This significantly cuts down the amount of branches and loads of
        // const_0 and const_1 in the generated java bytecode.
        if (isNegation(condition, classCodegen.context)) {
            condition = (condition as IrCall).dispatchReceiver!!
            jumpIfFalse = !jumpIfFalse
        }

        // Do not materialize null constants to check for null. Instead use the JVM bytecode
        // ifnull and ifnonnull instructions.
        if (isNullCheck(condition)) {
            val call = condition as IrCall
            val left = call.getValueArgument(0)!!
            val right = call.getValueArgument(1)!!
            val other = if (left.isNullConst()) right else left
            gen(other, data).put(other.asmType, mv)
            if (jumpIfFalse) {
                mv.ifnonnull(jumpToLabel)
            } else {
                mv.ifnull(jumpToLabel)
            }
            return
        }

        // For comparison intrinsics, branch directly based on the comparison instead of
        // materializing a boolean and performing and extra jump.
        if (condition is IrCall) {
            val intrinsic = intrinsics.getIntrinsic(condition.symbol)
            if (intrinsic is ComparisonIntrinsic) {
                val callable = resolveToCallable(condition, false)
                (callable as IrIntrinsicFunction).loadArguments(this, data)
                val stackValue = intrinsic.genStackValue(condition, classCodegen.context)
                BranchedValue.condJump(stackValue, jumpToLabel, jumpIfFalse, mv)
                return
            }
        }

        // For instance of type operators, branch directly on the instanceof result instead
        // of materializing a boolean and performing an extra jump.
        if (condition is IrTypeOperatorCall &&
            (condition.operator == IrTypeOperator.NOT_INSTANCEOF || condition.operator == IrTypeOperator.INSTANCEOF)
        ) {
            val asmType = condition.typeOperand.asmType
            gen(condition.argument, OBJECT_TYPE, data)
            val type = boxType(asmType)
            generateIsCheck(mv, condition.typeOperand.toKotlinType(), type, state.languageVersionSettings.isReleaseCoroutines())
            val stackValue =
                if (condition.operator == IrTypeOperator.INSTANCEOF)
                    onStack(Type.BOOLEAN_TYPE)
                else
                    StackValue.not(onStack(Type.BOOLEAN_TYPE))
            BranchedValue.condJump(stackValue, jumpToLabel, jumpIfFalse, mv)
            return
        }

        gen(condition, data).put(condition.asmType, mv)
        BranchedValue.condJump(onStack(condition.asmType), jumpToLabel, jumpIfFalse, mv)
    }

    private fun isNullCheck(expression: IrExpression): Boolean {
        return expression is IrCall
                && expression.symbol == classCodegen.context.irBuiltIns.eqeqSymbol
                && (expression.getValueArgument(0)!!.isNullConst() || expression.getValueArgument(1)!!.isNullConst())
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall, data: BlockInfo): StackValue {
        val asmType = expression.typeOperand.asmType
        when (expression.operator) {
            IrTypeOperator.IMPLICIT_COERCION_TO_UNIT -> {
                val result = expression.argument.accept(this, data)
                expression.argument.markEndOfStatementIfNeeded()
                return result.discard()
            }

            IrTypeOperator.IMPLICIT_CAST -> {
                gen(expression.argument, asmType, data)
            }

            IrTypeOperator.CAST, IrTypeOperator.SAFE_CAST -> {
                val value = expression.argument.accept(this, data)
                value.put(boxType(value.type), mv)
                if (value.type === Type.VOID_TYPE) {
                    StackValue.putUnitInstance(mv)
                }
                val boxedType = boxType(asmType)
                generateAsCast(
                    mv, expression.typeOperand.toKotlinType(), boxedType, expression.operator == IrTypeOperator.SAFE_CAST,
                    state.languageVersionSettings.isReleaseCoroutines()
                )
                return onStack(boxedType)
            }

            IrTypeOperator.INSTANCEOF, IrTypeOperator.NOT_INSTANCEOF -> {
                gen(expression.argument, OBJECT_TYPE, data)
                val type = boxType(asmType)
                generateIsCheck(mv, expression.typeOperand.toKotlinType(), type, state.languageVersionSettings.isReleaseCoroutines())
                if (IrTypeOperator.NOT_INSTANCEOF == expression.operator) {
                    StackValue.not(StackValue.onStack(Type.BOOLEAN_TYPE)).put(mv)
                }
            }

            IrTypeOperator.IMPLICIT_NOTNULL -> {
                val value = gen(expression.argument, data)
                mv.dup()
                mv.visitLdcInsn("TODO provide message for IMPLICIT_NOTNULL") /*TODO*/
                mv.invokestatic(
                    "kotlin/jvm/internal/Intrinsics", "checkExpressionValueIsNotNull",
                    "(Ljava/lang/Object;Ljava/lang/String;)V", false
                )
                StackValue.onStack(value.type).put(asmType, mv)
            }

            IrTypeOperator.IMPLICIT_INTEGER_COERCION -> {
                gen(expression.argument, Type.INT_TYPE, data)
                StackValue.coerce(Type.INT_TYPE, typeMapper.mapType(expression.type), mv)
            }
        }
        return expression.onStack
    }

    private fun IrExpression.markEndOfStatementIfNeeded() {
        when (this) {
            is IrWhen -> if (this.branches.size > 1) {
                this.markLineNumber(false)
            }
            is IrTry -> this.markLineNumber(false)
            is IrContainerExpression -> when (this.origin) {
                IrStatementOrigin.WHEN, IrStatementOrigin.IF ->
                    this.markLineNumber(false)
            }
        }
    }

    override fun visitStringConcatenation(expression: IrStringConcatenation, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        return when (expression.arguments.size) {
            0 -> StackValue.constant("", expression.asmType)
            1 -> {
                // Convert single arg to string.
                val arg = expression.arguments[0]
                val argStackValue = gen(arg, arg.asmType, data)
                AsmUtil.genToString(argStackValue, argStackValue.type, argStackValue.kotlinType, typeMapper.kotlinTypeMapper).put(expression.asmType, mv)
                expression.onStack
            }
            else -> {
                // Use StringBuilder to concatenate.
                AsmUtil.genStringBuilderConstructor(mv)
                expression.arguments.forEach {
                    val stackValue = gen(it, it.asmType, data)
                    AsmUtil.genInvokeAppendMethod(mv, stackValue.type, stackValue.kotlinType)
                }
                mv.invokevirtual("java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false)
                expression.onStack
            }
        }
    }

    // Avoid true condition generation for tailrec
    // ASM and the Java verifier assume that L1 is reachable which causes several verifications to fail.
    // To avoid them, trivial jump elimination is required.
    // L0
    // ICONST_1 //could be eliminated
    // IFEQ L1 //could be eliminated
    // .... // no jumps
    // GOTO L0
    // L1
    //TODO: write elimination lower
    private fun generateLoopJump(condition: IrExpression, data: BlockInfo, label: Label, jumpIfFalse: Boolean) {
        condition.markLineNumber(true)
        if (condition is IrConst<*> && condition.value == true) {
            if (jumpIfFalse) {
                mv.fakeAlwaysFalseIfeq(label)
            } else {
                mv.fakeAlwaysTrueIfeq(label)
            }
        } else {
            genConditionalJumpWithOptimizationsIfPossible(condition, data, label, jumpIfFalse)
        }
    }

    override fun visitWhileLoop(loop: IrWhileLoop, data: BlockInfo): StackValue {
        val continueLabel = markNewLabel()
        val endLabel = Label()
        generateLoopJump(loop.condition, data, endLabel, true)

        with(LoopInfo(loop, continueLabel, endLabel)) {
            data.addInfo(this)
            loop.body?.let {
                gen(it, data).discard()
            }
            data.removeInfo(this)
        }
        mv.goTo(continueLabel)
        mv.mark(endLabel)

        return StackValue.none()
    }

    override fun visitBreakContinue(jump: IrBreakContinue, data: BlockInfo): StackValue {
        jump.markLineNumber(startOffset = true)
        generateBreakOrContinueExpression(jump, Label(), data)
        return none()
    }

    private fun generateBreakOrContinueExpression(
        expression: IrBreakContinue,
        afterBreakContinueLabel: Label,
        data: BlockInfo
    ) {
        if (data.isEmpty()) {
            throw UnsupportedOperationException("Target label for break/continue not found")
        }

        val stackElement = data.peek()

        when (stackElement) {
            is TryInfo -> //noinspection ConstantConditions
                genFinallyBlockOrGoto(stackElement, null, afterBreakContinueLabel, data)
            is LoopInfo -> {
                val loop = expression.loop
                //noinspection ConstantConditions
                if (loop == stackElement.loop) {
                    val label = if (expression is IrBreak) stackElement.breakLabel else stackElement.continueLabel
                    mv.fixStackAndJump(label)
                    mv.mark(afterBreakContinueLabel)
                    return
                }
            }
            else -> throw UnsupportedOperationException("Wrong BlockStackElement in processing stack")
        }

        data.pop()
        val result = generateBreakOrContinueExpression(expression, afterBreakContinueLabel, data)
        data.addInfo(stackElement)
        return result
    }

    override fun visitDoWhileLoop(loop: IrDoWhileLoop, data: BlockInfo): StackValue {
        val entry = markNewLabel()
        val endLabel = Label()
        val continueLabel = Label()

        mv.fakeAlwaysFalseIfeq(continueLabel)
        mv.fakeAlwaysFalseIfeq(endLabel)

        with(LoopInfo(loop, continueLabel, endLabel)) {
            data.addInfo(this)
            loop.body?.let {
                gen(it, data).discard()
            }
            data.removeInfo(this)
        }

        mv.visitLabel(continueLabel)
        generateLoopJump(loop.condition, data, entry, false)
        mv.mark(endLabel)

        return StackValue.none()
    }

    override fun visitTry(aTry: IrTry, data: BlockInfo): StackValue {
        aTry.markLineNumber(startOffset = true)
        val finallyExpression = aTry.finallyExpression
        val tryInfo = if (finallyExpression != null) TryInfo(aTry) else null
        if (tryInfo != null) {
            data.addInfo(tryInfo)
        }

        val tryBlockStart = markNewLabel()
        mv.nop()
        gen(aTry.tryResult, aTry.asmType, data)
        val tryBlockEnd = markNewLabel()

        val tryRegions = getCurrentTryIntervals(tryInfo, tryBlockStart, tryBlockEnd)

        val tryCatchBlockEnd = Label()
        genFinallyBlockOrGoto(tryInfo, tryCatchBlockEnd, null, data)

        val catches = aTry.catches
        for (clause in catches) {
            val clauseStart = markNewLabel()
            val parameter = clause.catchParameter
            val descriptorType = parameter.asmType
            val index = frame.enter(clause.catchParameter, descriptorType)
            mv.store(index, descriptorType)

            val catchBody = clause.result
            catchBody.markLineNumber(true)
            gen(catchBody, catchBody.asmType, data)

            frame.leave(clause.catchParameter)

            val clauseEnd = markNewLabel()

            mv.visitLocalVariable(
                parameter.name.asString(), descriptorType.descriptor, null, clauseStart, clauseEnd,
                index
            )

            genFinallyBlockOrGoto(
                tryInfo,
                if (clause != catches.last() || finallyExpression != null) tryCatchBlockEnd else null,
                null,
                data
            )

            generateExceptionTable(clauseStart, tryRegions, descriptorType.internalName)
        }

        //for default catch clause
        if (finallyExpression != null) {
            val defaultCatchStart = Label()
            mv.mark(defaultCatchStart)
            val savedException = frame.enterTemp(JAVA_THROWABLE_TYPE)
            mv.store(savedException, JAVA_THROWABLE_TYPE)

            val defaultCatchEnd = Label()
            mv.mark(defaultCatchEnd)

            //do it before finally block generation
            //javac also generates entry in exception table for default catch clause too!!!! so defaultCatchEnd as end parameter
            val defaultCatchRegions = getCurrentTryIntervals(tryInfo, tryBlockStart, defaultCatchEnd)

            genFinallyBlockOrGoto(tryInfo, null, null, data)

            mv.load(savedException, JAVA_THROWABLE_TYPE)
            frame.leaveTemp(JAVA_THROWABLE_TYPE)

            mv.athrow()

            generateExceptionTable(defaultCatchStart, defaultCatchRegions, null)
        }

        mv.mark(tryCatchBlockEnd)
        if (tryInfo != null) {
            data.removeInfo(tryInfo)
        }

        return aTry.onStack
    }

    private fun getCurrentTryIntervals(
        finallyBlockStackElement: TryInfo?,
        blockStart: Label,
        blockEnd: Label
    ): List<Label> {
        val gapsInBlock = if (finallyBlockStackElement != null) ArrayList<Label>(finallyBlockStackElement.gaps) else emptyList<Label>()
        assert(gapsInBlock.size % 2 == 0)
        val blockRegions = ArrayList<Label>(gapsInBlock.size + 2)
        blockRegions.add(blockStart)
        blockRegions.addAll(gapsInBlock)
        blockRegions.add(blockEnd)
        return blockRegions
    }

    private fun generateExceptionTable(catchStart: Label, catchedRegions: List<Label>, exception: String?) {
        var i = 0
        while (i < catchedRegions.size) {
            val startRegion = catchedRegions[i]
            val endRegion = catchedRegions[i + 1]
            mv.visitTryCatchBlock(startRegion, endRegion, catchStart, exception)
            i += 2
        }
    }

    private fun genFinallyBlockOrGoto(
        tryInfo: TryInfo?,
        tryCatchBlockEnd: Label?,
        afterJumpLabel: Label?,
        data: BlockInfo
    ) {
        if (tryInfo != null) {
            assert(tryInfo.gaps.size % 2 == 0) { "Finally block gaps are inconsistent" }

            val topOfStack = data.pop()
            assert(topOfStack === tryInfo) { "Top element of stack doesn't equals processing finally block" }

            val tryBlock = tryInfo.tryBlock
            val finallyStart = markNewLabel()
            tryInfo.gaps.add(finallyStart)

            //noinspection ConstantConditions
            gen(tryBlock.finallyExpression!!, Type.VOID_TYPE, data)
        }

        if (tryCatchBlockEnd != null) {
            if (tryInfo != null) {
                tryInfo.tryBlock.finallyExpression!!.markLineNumber(startOffset = false)
            }
            mv.goTo(tryCatchBlockEnd)
        }

        if (tryInfo != null) {
            val finallyEnd = afterJumpLabel ?: Label()
            if (afterJumpLabel == null) {
                mv.mark(finallyEnd)
            }
            tryInfo.gaps.add(finallyEnd)

            data.addInfo(tryInfo)
        }
    }

    fun generateFinallyBlocksIfNeeded(returnType: Type, afterReturnLabel: Label, data: BlockInfo) {
        if (data.hasFinallyBlocks()) {
            if (Type.VOID_TYPE != returnType) {
                val returnValIndex = frame.enterTemp(returnType)
                val localForReturnValue = StackValue.local(returnValIndex, returnType)
                localForReturnValue.store(StackValue.onStack(returnType), mv)
                doFinallyOnReturn(afterReturnLabel, data)
                localForReturnValue.put(returnType, mv)
                frame.leaveTemp(returnType)
            } else {
                doFinallyOnReturn(afterReturnLabel, data)
            }
        }
    }


    private fun doFinallyOnReturn(afterReturnLabel: Label, data: BlockInfo) {
        if (!data.isEmpty()) {
            val stackElement = data.peek()
            when (stackElement) {
                is TryInfo -> genFinallyBlockOrGoto(stackElement, null, afterReturnLabel, data)
                is LoopInfo -> {

                }
                else -> throw UnsupportedOperationException("Wrong BlockStackElement in processing stack")
            }

            data.pop()
            doFinallyOnReturn(afterReturnLabel, data)
            data.addInfo(stackElement)
        }
    }

    override fun visitThrow(expression: IrThrow, data: BlockInfo): StackValue {
        expression.markLineNumber(startOffset = true)
        gen(expression.value, JAVA_THROWABLE_TYPE, data)
        mv.athrow()
        return none()
    }

    override fun visitGetClass(expression: IrGetClass, data: BlockInfo): StackValue {
        generateClassLiteralReference(expression, true, data)
        return expression.onStack
    }

    override fun visitClassReference(expression: IrClassReference, data: BlockInfo): StackValue {
        generateClassLiteralReference(expression, true, data)
        return expression.onStack
    }

    fun generateClassLiteralReference(
        classReference: IrExpression,
        wrapIntoKClass: Boolean,
        data: BlockInfo
    ) {
        if (classReference !is IrClassReference /* && DescriptorUtils.isObjectQualifier(classReference.descriptor)*/) {
            assert(classReference is IrGetClass)
            JavaClassProperty.generateImpl(mv, gen((classReference as IrGetClass).argument, data))
        } else {
            val classType = classReference.classType
            if (classType is CrIrType) {
                putJavaLangClassInstance(mv, classType.type, null, typeMapper.kotlinTypeMapper)
                return
            } else {
                val kotlinType = classType.toKotlinType()
                val classifier = classType.classifierOrNull
                if (classifier is IrTypeParameterSymbol) {
                    assert(classifier.owner.isReified) { "Non-reified type parameter under ::class should be rejected by type checker: ${classifier.owner.dump()}" }
                    putReifiedOperationMarkerIfTypeIsReifiedParameter(kotlinType, ReifiedTypeInliner.OperationKind.JAVA_CLASS, mv, this)
                }

                putJavaLangClassInstance(mv, typeMapper.mapType(classType), kotlinType, typeMapper.kotlinTypeMapper)
            }
        }

        if (wrapIntoKClass) {
            wrapJavaClassIntoKClass(mv)
        }

    }

    internal fun coerceNotToUnit(fromType: Type, fromIrType: IrType?, toIrType: IrType): StackValue {
        val asmToType = toIrType.asmType
        // A void should still be materialized as a Unit to avoid stack depth mismatches.
        if (asmToType != AsmTypes.UNIT_TYPE || fromType == Type.VOID_TYPE || toIrType.isNullable()) {
            coerce(fromType, fromIrType?.toKotlinType(), asmToType, toIrType.toKotlinType(), mv)
            return onStack(asmToType, toIrType.toKotlinType())
        }
        return onStack(fromType, fromIrType?.toKotlinType())
    }

    val IrType.asmType: Type
        get() = typeMapper.mapType(this)

    val IrExpression.asmType: Type
        get() = type.asmType

    val IrVariable.asmType: Type
        get() = type.asmType

    val IrExpression.onStack: StackValue
        get() = StackValue.onStack(this.asmType)

    private fun resolveToCallable(irCall: IrFunctionAccessExpression, isSuper: Boolean): Callable {
        val intrinsic = intrinsics.getIntrinsic(irCall.symbol)
        if (intrinsic != null) {
            return intrinsic.toCallable(
                irCall,
                typeMapper.mapSignatureSkipGeneric(irCall.symbol.owner),
                classCodegen.context
            )
        }
        return typeMapper.mapToCallableMethod(irCall.symbol.owner, isSuper)
    }

    private fun getOrCreateCallGenerator(
        irFunction: IrFunction,
        element: IrMemberAccessExpression?,
        typeParameterMappings: IrTypeParameterMappings?,
        isDefaultCompilation: Boolean
    ): IrCallGenerator {
        if (element == null) return IrCallGenerator.DefaultCallGenerator

        // We should inline callable containing reified type parameters even if inline is disabled
        // because they may contain something to reify and straight call will probably fail at runtime
        val isInline = irFunction.isInlineCall(state)

        if (!isInline) return IrCallGenerator.DefaultCallGenerator

        val original = (irFunction as? IrSimpleFunction)?.resolveFakeOverride() ?: irFunction
        return if (isDefaultCompilation) {
            TODO()
        } else {
            IrInlineCodegen(this, state, original.descriptor, typeParameterMappings!!, IrSourceCompilerForInline(state, element, this))
        }
    }

    internal fun getOrCreateCallGenerator(
        functionAccessExpression: IrFunctionAccessExpression
    ): IrCallGenerator {
        val callee = functionAccessExpression.symbol.owner
        // TODO: do we need to collect _all_ type arguments?
        val typeArgumentContainer = if (callee is IrConstructor) callee.parentAsClass else callee
        val typeArguments =
            if (functionAccessExpression.typeArgumentsCount == 0) {
                //avoid ambiguity with type constructor type parameters
                emptyMap()
            } else typeArgumentContainer.typeParameters.keysToMap {
                functionAccessExpression.getTypeArgumentOrDefault(it)
            }

        val mappings = IrTypeParameterMappings()
        for (entry in typeArguments.entries) {
            val key = entry.key
            val type = entry.value

            val isReified = key.isReified || callee.isArrayConstructorWithLambda()

            val reificationArgument = extractReificationArgument(type)
            if (reificationArgument == null) {
                // type is not generic
                val signatureWriter = BothSignatureWriter(BothSignatureWriter.Mode.TYPE)
                val asmType = typeMapper.mapTypeParameter(type, signatureWriter)

                mappings.addParameterMappingToType(
                    key.name.identifier, type, asmType, signatureWriter.toString(), isReified
                )
            } else {
                mappings.addParameterMappingForFurtherReification(
                    key.name.identifier, type, reificationArgument, isReified
                )
            }
        }

        return getOrCreateCallGenerator(callee, functionAccessExpression, mappings, false)
    }

    override val frameMap: IrFrameMap
        get() = frame
    override val visitor: InstructionAdapter
        get() = mv
    override val inlineNameGenerator: NameGenerator = NameGenerator("${classCodegen.type.internalName}\$todo")

    override var lastLineNumber: Int = -1

    override fun consumeReifiedOperationMarker(typeParameterDescriptor: TypeParameterDescriptor) {
        //TODO
    }

    override fun propagateChildReifiedTypeParametersUsages(reifiedTypeParametersUsages: ReifiedTypeParametersUsages) {
        //TODO
    }

    override fun pushClosureOnStack(
        classDescriptor: ClassDescriptor,
        putThis: Boolean,
        callGenerator: CallGenerator,
        functionReferenceReceiver: StackValue?
    ) {
        //TODO
    }

    override fun markLineNumberAfterInlineIfNeeded() {
        //TODO
    }

    private fun markNewLabel() = Label().apply { mv.visitLabel(this) }

    private fun IrElement.markLineNumber(startOffset: Boolean) {
        val offset = if (startOffset) this.startOffset else endOffset
        if (offset < 0) {
            return
        }
        val lineNumber = fileEntry.getLineNumber(offset) + 1
        assert(lineNumber > 0)
        if (lastLineNumber == lineNumber) {
            return
        }
        lastLineNumber = lineNumber
        mv.visitLineNumber(lineNumber, markNewLabel())
    }

    /* Borrowed and modified from compiler/backend/src/org/jetbrains/kotlin/codegen/codegenUtil.kt */

    fun extractReificationArgument(initialType: IrType): IrReificationArgument? {
        var type = initialType
        var arrayDepth = 0
        val isNullable = type.isMarkedNullable()
        while (type.isArray()) {
            arrayDepth++
            type = (type as IrSimpleType).arguments[0].safeAs<IrTypeProjection>()?.type ?: classCodegen.context.irBuiltIns.anyNType
        }

        val parameter = type.getTypeParameterOrNull() ?: return null

        return IrReificationArgument(parameter.name.asString(), isNullable, arrayDepth)
    }

    /* From ReifiedTypeInliner.kt */
    inner class IrReificationArgument(
        val parameterName: String, val nullable: Boolean, private val arrayDepth: Int
    ) {
        fun asString() = "[".repeat(arrayDepth) + parameterName + (if (nullable) "?" else "")
        fun combine(replacement: IrReificationArgument) =
            IrReificationArgument(
                replacement.parameterName,
                this.nullable || (replacement.nullable && this.arrayDepth == 0),
                this.arrayDepth + replacement.arrayDepth
            )

        fun reify(replacementAsmType: Type, irType: IrType) =
            Pair(
                Type.getType("[".repeat(arrayDepth) + replacementAsmType),
                irType.arrayOf(arrayDepth).withHasQuestionMark(nullable)
            )

        private fun IrType.arrayOf(arrayDepth: Int): IrType {
            val builtins = classCodegen.context.irBuiltIns
            var currentType = this

            repeat(arrayDepth) {
                currentType = builtins.arrayClass.typeWith(currentType)
            }

            return currentType
        }

        fun toReificationArgument() = ReificationArgument(parameterName, nullable, arrayDepth)
    }

    /*TODO: Temporary copy from BridgeLowering */
    private fun IrType.eraseTypeParameters() = when (this) {
        is IrErrorType -> this
        is IrSimpleType -> {
            val owner = classifier.owner
            when (owner) {
                is IrClass -> IrSimpleTypeImpl(
                    classifier,
                    hasQuestionMark,
                    arguments.zip(owner.typeParameters).map { (arg, param) -> arg.eraseTypeParameters(param) },
                    annotations
                )
                is IrTypeParameter -> {
                    owner.upperBound() // !!!!!! nullability
                }
                else -> error("Unknown IrSimpleType classifier kind: $owner")
            }
        }
        else -> error("Unknown IrType kind: $this")
    }

    private fun IrTypeParameter.upperBound(): IrType {
        val superSimpleTypes = superTypes.asSequence().filterIsInstance<IrSimpleType>()
        val res = superSimpleTypes.firstOrNull { it.classifier is IrClassSymbol && !(it.classifier.owner as IrClass).isInterface }
            ?: superSimpleTypes.firstOrNull { it.classifier is IrClassSymbol }
            ?: classCodegen.context.irBuiltIns.anyNType
        return res.eraseTypeParameters()
    }

    private fun IrTypeArgument.eraseTypeParameters(param: IrTypeParameter): IrTypeArgument = when (this) {
        is IrTypeProjection -> makeTypeProjection(type.eraseTypeParameters(), variance) // !!!!!! TODO: must be wrong !!!!!!
        is IrStarProjection -> this
        else -> error("unknown type argyument kind: $this")
    }

    fun IrType.getArrayOrPrimitiveArrayElementType() = (this as? IrSimpleType)?.let {
        if (this.makeNotNull().isArray()) {
            assert(arguments.size == 1) { "Array should have one type argument" }
            arguments[0].safeAs<IrTypeProjection>()?.type
        } else {
            val primitiveType = getPrimitiveArrayElementType() ?: error("Not an array type $this")
            return context.irBuiltIns.primitiveTypeToIrType[primitiveType]!!
        }
    }
}

private class DefaultArg(val index: Int)

private class Vararg(val index: Int)


fun DefaultCallArgs.generateOnStackIfNeeded(callGenerator: IrCallGenerator, isConstructor: Boolean, codegen: ExpressionCodegen): Boolean {
    val toInts = toInts()
    if (!toInts.isEmpty()) {
        for (mask in toInts) {
            callGenerator.putValueIfNeeded(Type.INT_TYPE, StackValue.constant(mask, Type.INT_TYPE), ValueKind.DEFAULT_MASK, -1, codegen)
        }

        val parameterType = if (isConstructor) AsmTypes.DEFAULT_CONSTRUCTOR_MARKER else AsmTypes.OBJECT_TYPE
        callGenerator.putValueIfNeeded(
            parameterType,
            StackValue.constant(null, parameterType),
            ValueKind.METHOD_HANDLE_IN_DEFAULT,
            -1,
            codegen
        )
    }
    return toInts.isNotEmpty()
}

internal fun IrFunction.isInlineCall(state: GenerationState) =
    (!state.isInlineDisabled || containsReifiedTypeParameters()) &&
            (isInline || isArrayConstructorWithLambda())

/* Copied and modified from InlineUtil.java */
fun isInline(declaration: IrDeclaration?): Boolean = declaration is IrSimpleFunction && declaration.isInline

/**
 * @return true if the function is a constructor of one of 9 array classes (Array&lt;T&gt;, IntArray, FloatArray, ...)
 * which takes the size and an initializer lambda as parameters. Such constructors are marked as 'inline' but they are not loaded
 * as such because the 'inline' flag is not stored for constructors in the binary metadata. Therefore we pretend that they are inline
 */
fun IrFunction.isArrayConstructorWithLambda(): Boolean = this is IrConstructor &&
        valueParameters.size == 2 &&
        parentAsClass.isArrayOrPrimitiveArray()


fun IrFunction.containsReifiedTypeParameters(): Boolean =
    typeParameters.any { it.isReified }

fun IrClass.isArrayOrPrimitiveArray() = this.defaultType.let { it.isArray() || it.isPrimitiveArray() }

/* From typeUtil.java */
fun IrType.getTypeParameterOrNull() = classifierOrNull?.owner?.safeAs<IrTypeParameter>()

/* From ReifiedTypeInliner.kt */
class IrTypeParameterMappings {
    private val mappingsByName = hashMapOf<String, IrTypeParameterMapping>()

    fun addParameterMappingToType(name: String, type: IrType, asmType: Type, signature: String, isReified: Boolean) {
        mappingsByName[name] = IrTypeParameterMapping(
            name, type, asmType, reificationArgument = null, signature = signature, isReified = isReified
        )
    }

    fun addParameterMappingForFurtherReification(
        name: String,
        type: IrType,
        reificationArgument: ExpressionCodegen.IrReificationArgument,
        isReified: Boolean
    ) {
        mappingsByName[name] = IrTypeParameterMapping(
            name, type, asmType = null, reificationArgument = reificationArgument, signature = null, isReified = isReified
        )
    }

    operator fun get(name: String): IrTypeParameterMapping? = mappingsByName[name]

    fun hasReifiedParameters() = mappingsByName.values.any { it.isReified }

    internal inline fun forEach(l: (IrTypeParameterMapping) -> Unit) {
        mappingsByName.values.forEach(l)
    }

    fun toTypeParameterMappings() = TypeParameterMappings().also { result ->
        mappingsByName.forEach { (_, value) ->
            if (value.asmType == null) {
                result.addParameterMappingForFurtherReification(
                    value.name,
                    value.type.toKotlinType(),
                    value.reificationArgument!!.toReificationArgument(),
                    value.isReified
                )
            } else {
                result.addParameterMappingToType(
                    value.name,
                    value.type.toKotlinType(),
                    value.asmType,
                    value.signature!!,
                    value.isReified
                )
            }
        }
    }
}

class IrTypeParameterMapping(
    val name: String,
    val type: IrType,
    val asmType: Type?,
    val reificationArgument: ExpressionCodegen.IrReificationArgument?,
    val signature: String?,
    val isReified: Boolean
)
