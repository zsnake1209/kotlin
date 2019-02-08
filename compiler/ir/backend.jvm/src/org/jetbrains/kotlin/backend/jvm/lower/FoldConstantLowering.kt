/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstKind
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.DescriptorUtils

internal val foldConstantLoweringPhase = makeIrFilePhase(
    ::FoldConstantLowering,
    name = "FoldConstantLowering",
    description = "Constant Folding"
)

/**
 * A pass to fold constant expressions of most common types.
 *
 * For example, the expression "O" + 'K' + (1.toLong() + 2.0) will be folded to "OK3.0" at compile time.
 */
class FoldConstantLowering(private val context: JvmBackendContext) : IrElementTransformerVoid(), FileLoweringPass {

    /**
     * ID of an unary operator / method.
     *
     * An unary operator / method can be identified by its operand type (in full qualified name) and its name.
     */
    private data class UnaryOp(
        val operandType: FqName,
        val operatorFN: String
    ) {
        companion object {
            fun get(descriptor: CallableMemberDescriptor) =
                UnaryOp(
                    DescriptorUtils.getFqNameSafe(descriptor.containingDeclaration),
                    descriptor.name.asString()
                )
        }
    }

    /**
     * ID of an binary operator / method.
     *
     * An binary operator / method can be identified by its operand types (in full qualified names) and its name.
     */
    private data class BinaryOp(
        val lhsType: FqName,
        val rhsType: FqName,
        val operatorFN: String
    ) {
        companion object {
            fun get(descriptor: CallableMemberDescriptor, rhsKind: IrConstKind<*>) =
                BinaryOp(
                    DescriptorUtils.getFqNameSafe(descriptor.containingDeclaration),
                    KIND_TO_FQNAME[rhsKind]!!,
                    descriptor.name.asString()
                )
        }
    }

    companion object {
        private val STRING = KotlinBuiltIns.FQ_NAMES.string.toSafe()
        private val CHAR = PrimitiveType.CHAR.typeFqName
        private val BOOLEAN = PrimitiveType.BOOLEAN.typeFqName
        private val BYTE = PrimitiveType.BYTE.typeFqName
        private val SHORT = PrimitiveType.SHORT.typeFqName
        private val INT = PrimitiveType.INT.typeFqName
        private val LONG = PrimitiveType.LONG.typeFqName
        private val FLOAT = PrimitiveType.FLOAT.typeFqName
        private val DOUBLE = PrimitiveType.DOUBLE.typeFqName

        private val ANY = listOf(STRING, CHAR, BOOLEAN, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE)

        // Map IrConstKind to FqName
        private val KIND_TO_FQNAME = hashMapOf<IrConstKind<*>, FqName>(
            IrConstKind.Boolean to BOOLEAN,
            IrConstKind.String to STRING,
            IrConstKind.Char to CHAR,
            IrConstKind.Byte to BYTE,
            IrConstKind.Short to SHORT,
            IrConstKind.Int to INT,
            IrConstKind.Long to LONG,
            IrConstKind.Float to FLOAT,
            IrConstKind.Double to DOUBLE
        )

        private val UNARY_OP_TO_EVALUATOR = HashMap<UnaryOp?, Function1<Any?, Any>>()
        private val BINARY_OP_TO_EVALUATOR = HashMap<BinaryOp?, Function2<Any?, Any?, Any>>()

        @Suppress("UNCHECKED_CAST")
        private fun <T> registerUnaryOp(operandType: FqName, operatorFN: String, f: (T) -> Any) {
            UNARY_OP_TO_EVALUATOR[UnaryOp(operandType, operatorFN)] = f as Function1<Any?, Any>
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T1, T2> registerBinaryOp(lhsType: FqName, rhsType: FqName, operatorFN: String, f: (T1, T2) -> Any) {
            BINARY_OP_TO_EVALUATOR[BinaryOp(lhsType, rhsType, operatorFN)] = f as Function2<Any?, Any?, Any>
        }

        private fun <T1, T2> registerSymmetricBinaryOp(lhsType: FqName, rhsType: FqName, operatorFN: String, f: (T1, T2) -> Any) {
            registerBinaryOp(lhsType, rhsType, operatorFN, f)
            registerBinaryOp(rhsType, lhsType, operatorFN, f)
        }

        // Class Number defines toChar, toByte, ..., toString.
        // This allows us to factor them out and save some typing.
        private fun <T : Number> registerUnaryNumberOps(operandType: FqName) {
            registerUnaryOp<T>(operandType, "toChar") { it.toChar() }
            registerUnaryOp<T>(operandType, "toByte") { it.toByte() }
            registerUnaryOp<T>(operandType, "toShort") { it.toShort() }
            registerUnaryOp<T>(operandType, "toInt") { it.toInt() }
            registerUnaryOp<T>(operandType, "toLong") { it.toLong() }
            registerUnaryOp<T>(operandType, "toFloat") { it.toFloat() }
            registerUnaryOp<T>(operandType, "toDouble") { it.toDouble() }
            registerUnaryOp<T>(operandType, "toString") { it.toString() }
        }

        init {
            // Unary ops of Number
            registerUnaryNumberOps<Byte>(BYTE)
            registerUnaryNumberOps<Short>(SHORT)
            registerUnaryNumberOps<Int>(INT)
            registerUnaryNumberOps<Float>(FLOAT)
            registerUnaryNumberOps<Long>(LONG)
            registerUnaryNumberOps<Double>(DOUBLE)

            // Unfortunately unaryPlus and unaryMinus are not part of Number and have to be enumerated.
            registerUnaryOp<Byte>(BYTE, "unaryPlus") { it.unaryPlus() }
            registerUnaryOp<Byte>(BYTE, "unaryMinus") { it.unaryMinus() }
            registerUnaryOp<Short>(SHORT, "unaryPlus") { it.unaryPlus() }
            registerUnaryOp<Short>(SHORT, "unaryMinus") { it.unaryMinus() }
            registerUnaryOp<Int>(INT, "unaryPlus") { it.unaryPlus() }
            registerUnaryOp<Int>(INT, "unaryMinus") { it.unaryMinus() }
            registerUnaryOp<Long>(LONG, "unaryPlus") { it.unaryPlus() }
            registerUnaryOp<Long>(LONG, "unaryMinus") { it.unaryMinus() }
            registerUnaryOp<Float>(FLOAT, "unaryPlus") { it.unaryPlus() }
            registerUnaryOp<Float>(FLOAT, "unaryMinus") { it.unaryMinus() }
            registerUnaryOp<Double>(DOUBLE, "unaryPlus") { it.unaryPlus() }
            registerUnaryOp<Double>(DOUBLE, "unaryMinus") { it.unaryMinus() }

            registerUnaryOp<Int>(INT, "inv") { it.inv() }
            registerUnaryOp<Long>(LONG, "inv") { it.inv() }

            // Binary ops of Number
            // Byte and Short are promoted to Int before operation.
            for (lhsType in listOf(BYTE, SHORT, INT)) {
                for (rhsType in listOf(BYTE, SHORT, INT)) {
                    registerBinaryOp<Int, Int>(lhsType, rhsType, "plus") { a, b -> a.plus(b) }
                    registerBinaryOp<Int, Int>(lhsType, rhsType, "minus") { a, b -> a.minus(b) }
                    registerBinaryOp<Int, Int>(lhsType, rhsType, "times") { a, b -> a.times(b) }
                    registerBinaryOp<Int, Int>(lhsType, rhsType, "div") { a, b -> a.div(b) }
                    registerBinaryOp<Int, Int>(lhsType, rhsType, "rem") { a, b -> a.rem(b) }
                    registerBinaryOp<Int, Int>(lhsType, rhsType, "mod") { a, b -> a.rem(b) }
                    registerBinaryOp<Int, Int>(lhsType, rhsType, "compareTo") { a, b -> a.compareTo(b) }
                }
            }
            // Integer types are promoted to Long if one of the operands is Long.
            for (opType in listOf(BYTE, SHORT, INT, LONG)) {
                registerSymmetricBinaryOp<Long, Long>(LONG, opType, "plus") { a, b -> a.plus(b) }
                registerSymmetricBinaryOp<Long, Long>(LONG, opType, "minus") { a, b -> a.minus(b) }
                registerSymmetricBinaryOp<Long, Long>(LONG, opType, "times") { a, b -> a.times(b) }
                registerSymmetricBinaryOp<Long, Long>(LONG, opType, "div") { a, b -> a.div(b) }
                registerSymmetricBinaryOp<Long, Long>(LONG, opType, "rem") { a, b -> a.rem(b) }
                registerSymmetricBinaryOp<Long, Long>(LONG, opType, "mod") { a, b -> a.rem(b) }
                registerSymmetricBinaryOp<Long, Long>(LONG, opType, "compareTo") { a, b -> a.compareTo(b) }
            }
            // Integer types are promoted to Float if one of the operands is Float.
            for (opType in listOf(BYTE, SHORT, INT, LONG, FLOAT)) {
                registerSymmetricBinaryOp<Float, Float>(FLOAT, opType, "plus") { a, b -> a.plus(b) }
                registerSymmetricBinaryOp<Float, Float>(FLOAT, opType, "minus") { a, b -> a.minus(b) }
                registerSymmetricBinaryOp<Float, Float>(FLOAT, opType, "times") { a, b -> a.times(b) }
                registerSymmetricBinaryOp<Float, Float>(FLOAT, opType, "div") { a, b -> a.div(b) }
                registerSymmetricBinaryOp<Float, Float>(FLOAT, opType, "rem") { a, b -> a.rem(b) }
                registerSymmetricBinaryOp<Float, Float>(FLOAT, opType, "mod") { a, b -> a.rem(b) }
                registerSymmetricBinaryOp<Float, Float>(FLOAT, opType, "compareTo") { a, b -> a.compareTo(b) }
            }
            // Numbers are promoted to Double if one of the operands is Double.
            for (opType in listOf(BYTE, SHORT, INT, LONG, FLOAT, DOUBLE)) {
                registerSymmetricBinaryOp<Double, Double>(DOUBLE, opType, "plus") { a, b -> a.plus(b) }
                registerSymmetricBinaryOp<Double, Double>(DOUBLE, opType, "minus") { a, b -> a.minus(b) }
                registerSymmetricBinaryOp<Double, Double>(DOUBLE, opType, "times") { a, b -> a.times(b) }
                registerSymmetricBinaryOp<Double, Double>(DOUBLE, opType, "div") { a, b -> a.div(b) }
                registerSymmetricBinaryOp<Double, Double>(DOUBLE, opType, "rem") { a, b -> a.rem(b) }
                registerSymmetricBinaryOp<Double, Double>(DOUBLE, opType, "mod") { a, b -> a.rem(b) }
                registerSymmetricBinaryOp<Double, Double>(DOUBLE, opType, "compareTo") { a, b -> a.compareTo(b) }
            }

            // Bitwise operations are only available on Int and Long in JVM.
            registerBinaryOp<Int, Int>(INT, INT, "shl") { a, b -> a.shl(b) }
            registerBinaryOp<Int, Int>(INT, INT, "shr") { a, b -> a.shr(b) }
            registerBinaryOp<Int, Int>(INT, INT, "ushr") { a, b -> a.ushr(b) }
            registerBinaryOp<Int, Int>(INT, INT, "and") { a, b -> a.and(b) }
            registerBinaryOp<Int, Int>(INT, INT, "or") { a, b -> a.or(b) }
            registerBinaryOp<Int, Int>(INT, INT, "xor") { a, b -> a.xor(b) }
            registerBinaryOp<Long, Int>(LONG, INT, "shl") { a, b -> a.shl(b) }
            registerBinaryOp<Long, Int>(LONG, INT, "shr") { a, b -> a.shr(b) }
            registerBinaryOp<Long, Int>(LONG, INT, "ushr") { a, b -> a.ushr(b) }
            registerBinaryOp<Long, Long>(LONG, LONG, "and") { a, b -> a.and(b) }
            registerBinaryOp<Long, Long>(LONG, LONG, "or") { a, b -> a.or(b) }
            registerBinaryOp<Long, Long>(LONG, LONG, "xor") { a, b -> a.xor(b) }

            // String
            registerUnaryOp<String>(STRING, "toString") { it }
            registerUnaryOp<String>(STRING, "length") { it.length }
            registerBinaryOp<String, String>(STRING, STRING, "plus") { a, b -> a.plus(b) }
            registerBinaryOp<String, String>(STRING, STRING, "compareTo") { a, b -> a.compareTo(b) }

            // Boolean
            registerUnaryOp<Boolean>(BOOLEAN, "toString") { it.toString() }
            registerUnaryOp<Boolean>(BOOLEAN, "not") { it.not() }
            registerBinaryOp<Boolean, Boolean>(BOOLEAN, BOOLEAN, "and") { a, b -> a.and(b) }
            registerBinaryOp<Boolean, Boolean>(BOOLEAN, BOOLEAN, "compareTo") { a, b -> a.compareTo(b) }
            registerBinaryOp<Boolean, Boolean>(BOOLEAN, BOOLEAN, "or") { a, b -> a.or(b) }
            registerBinaryOp<Boolean, Boolean>(BOOLEAN, BOOLEAN, "xor") { a, b -> a.xor(b) }

            // Char
            registerUnaryOp<Char>(CHAR, "toChar") { it }
            registerUnaryOp<Char>(CHAR, "toByte") { it.toByte() }
            registerUnaryOp<Char>(CHAR, "toShort") { it.toShort() }
            registerUnaryOp<Char>(CHAR, "toInt") { it.toInt() }
            registerUnaryOp<Char>(CHAR, "toLong") { it.toLong() }
            registerUnaryOp<Char>(CHAR, "toFloat") { it.toFloat() }
            registerUnaryOp<Char>(CHAR, "toDouble") { it.toDouble() }
            registerUnaryOp<Char>(CHAR, "toString") { it.toString() }
            registerBinaryOp<Char, Char>(CHAR, CHAR, "compareTo") { a, b -> a.compareTo(b) }
            registerBinaryOp<Char, Char>(CHAR, CHAR, "minus") { a, b -> a.minus(b) }
            registerBinaryOp<Char, Int>(CHAR, INT, "minus") { a, b -> a.minus(b) }
            registerBinaryOp<Char, Int>(CHAR, INT, "plus") { a, b -> a.plus(b) }

            // ANY
            for (type in ANY) {
                registerBinaryOp<String, Any>(STRING, type, "plus") { a, b -> a.plus(b) }
                registerBinaryOp<Boolean, Any>(BOOLEAN, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<Char, Any>(CHAR, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<Byte, Any>(BYTE, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<Short, Any>(SHORT, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<Int, Any>(INT, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<Long, Any>(LONG, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<Float, Any>(FLOAT, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<Double, Any>(DOUBLE, type, "equals") { a, b -> a.equals(b) }
                registerBinaryOp<String, Any>(STRING, type, "equals") { a, b -> a.equals(b) }
            }
        }
    }

    private fun buildIrConstant(call: IrCall, v: Any): IrExpression {
        return if (call.type.isInt()) {
            IrConstImpl.int(call.startOffset, call.endOffset, call.type, v as Int)
        } else if (call.type.isChar()) {
            IrConstImpl.char(call.startOffset, call.endOffset, call.type, v as Char)
        } else if (call.type.isBoolean()) {
            IrConstImpl.boolean(call.startOffset, call.endOffset, call.type, v as Boolean)
        } else if (call.type.isByte()) {
            IrConstImpl.byte(call.startOffset, call.endOffset, call.type, v as Byte)
        } else if (call.type.isShort()) {
            IrConstImpl.short(call.startOffset, call.endOffset, call.type, v as Short)
        } else if (call.type.isLong()) {
            IrConstImpl.long(call.startOffset, call.endOffset, call.type, v as Long)
        } else if (call.type.isDouble()) {
            IrConstImpl.double(call.startOffset, call.endOffset, call.type, v as Double)
        } else if (call.type.isFloat()) {
            IrConstImpl.float(call.startOffset, call.endOffset, call.type, v as Float)
        } else if (call.type.isString()) {
            IrConstImpl.string(call.startOffset, call.endOffset, call.type, v as String)
        } else {
            throw IllegalArgumentException("Unexpected IrCall return type")
        }
    }

    private fun tryFoldingUnaryOps(call: IrCall): IrExpression {
        val owner = call.dispatchReceiver
        if (owner !is IrConst<*>)
            return call

        val evaluator = UNARY_OP_TO_EVALUATOR[UnaryOp.get(call.descriptor)] ?: return call

        return buildIrConstant(call, evaluator(owner.value!!))
    }

    // Do type promotion so that we don't have to hard-code all the table.
    // T.{plus, minus, times, rem, mod, div, compareTo}(T) where T is {Int, Long, Float, Double} are defined.
    // A map without type promotion would be 7 (operators) * 36 (# number types ^ 2) = 252 lines of lambda.
    private fun promotePrimitive(a: Any, b: Any): Pair<Any, Any> {
        if (a is Number && b is Number) {
            if (a is Double || b is Double)
                return Pair(a.toDouble(), b.toDouble())

            if (a is Float || b is Float)
                return Pair(a.toFloat(), b.toFloat())

            if (a is Long || b is Long)
                return Pair(a.toLong(), b.toLong())

            return Pair(a.toInt(), b.toInt())
        }

        return Pair(a, b)
    }

    private fun tryFoldingBinaryOps(call: IrCall): IrExpression {
        val lhs = call.dispatchReceiver
        val rhs = call.getValueArgument(0)

        if (lhs !is IrConst<*> || rhs !is IrConst<*>)
            return call

        val evaluator = BINARY_OP_TO_EVALUATOR[BinaryOp.get(call.descriptor, rhs.kind)] ?: return call

        val (v1, v2) = promotePrimitive(lhs.value!!, rhs.value!!)
        val evaluated = try {
            evaluator(v1, v2)
        } catch (e: ArithmeticException) {
            // Don't cast a runtime exception into compile time. E.g., division by zero.
            return call
        }

        return buildIrConstant(call, evaluated)
    }

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitCall(expression: IrCall): IrExpression {
                expression.transformChildrenVoid(this)
                return when (expression.valueArgumentsCount) {
                    0 -> tryFoldingUnaryOps(expression)
                    1 -> tryFoldingBinaryOps(expression)
                    else -> expression
                }
            }
        })
    }
}