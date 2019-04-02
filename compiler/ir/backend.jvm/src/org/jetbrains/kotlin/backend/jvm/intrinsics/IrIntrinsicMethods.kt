/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.jvm.intrinsics

import org.jetbrains.coverage.org.objectweb.asm.Opcodes.IADD
import org.jetbrains.kotlin.builtins.KotlinBuiltIns.*
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.expressions.OperatorConventions
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.org.objectweb.asm.Opcodes.*
import org.jetbrains.org.objectweb.asm.Type

class IrIntrinsicMethods(irBuiltIns: IrBuiltIns) {
    private val KOTLIN_JVM = FqName("kotlin.jvm")
    internal val RECEIVER_PARAMETER_FQ_NAME = FqName("T")

    private val intrinsicsMap = (
            listOf(
                Key(KOTLIN_JVM, RECEIVER_PARAMETER_FQ_NAME, "<get-javaClass>", 0) to JavaClassProperty,
                Key(KOTLIN_JVM, FQ_NAMES.kClass.toSafe(), "<get-java>", 0) to KClassJavaProperty,
                Key(FqName("kotlin.jvm.internal.unsafe"), null, "monitorEnter", 1) to MonitorInstruction.MONITOR_ENTER,
                Key(FqName("kotlin.jvm.internal.unsafe"), null, "monitorExit", 1) to MonitorInstruction.MONITOR_EXIT,
                Key(KOTLIN_JVM, FQ_NAMES.array.toSafe(), "isArrayOf", 0) to IsArrayOf,
                Key(BUILT_INS_PACKAGE_FQ_NAME, null, "arrayOf", 1) to ArrayOf
            ) + OperatorConventions.NUMBER_CONVERSIONS.flatMap { method ->
                listOf(Key(FQ_NAMES.number.toSafe(), null, method.asString(), 0) to NumberCast) +
                PrimitiveType.NUMBER_TYPES.map { type ->
                     Key(type.typeFqName, null, method.asString(), 0) to NumberCast
                }
            } + PrimitiveType.NUMBER_TYPES.flatMap { type ->
                val typeFqName = type.typeFqName
                listOf(
                    Key(typeFqName, null, "plus", 0) to UnaryPlus,
                    Key(typeFqName, null, "unaryPlus", 0) to UnaryPlus,
                    Key(typeFqName, null, "minus", 0) to UnaryMinus,
                    Key(typeFqName, null, "unaryMinus", 0) to UnaryMinus,
                    Key(typeFqName, null, "inv", 0) to Inv,
                    Key(typeFqName, null, "rangeTo", 1) to RangeTo,
                    Key(typeFqName, null, "inc", 0) to INC,
                    Key(typeFqName, null, "dec", 0) to DEC
                )
            } + PrimitiveType.values().flatMap { type ->
                val typeFqName = type.typeFqName
                val asmPrimitiveType = AsmTypes.valueTypeForPrimitive(type)
                listOf(
                    Key(typeFqName, null, "equals", 1) to
                            if (asmPrimitiveType == Type.FLOAT_TYPE || asmPrimitiveType == Type.DOUBLE_TYPE)
                                TotalOrderEquals(asmPrimitiveType)
                            else
                                EQUALS,
                    Key(typeFqName, null, "hashCode", 0) to HashCode,
                    Key(typeFqName, null, "toString", 0) to ToString,
                    Key(BUILT_INS_PACKAGE_FQ_NAME, null, type.arrayTypeName.asString().decapitalize() + "Of", 1) to ArrayOf
                )
            } +
                    binaryOp("plus", IADD) +
                    binaryOp("minus", ISUB) +
                    binaryOp("times", IMUL) +
                    binaryOp("div", IDIV) +
                    binaryOp("mod", IREM) +
                    binaryOp("rem", IREM) +
                    binaryOp("shl", ISHL) +
                    binaryOp("shr", ISHR) +
                    binaryOp("ushr", IUSHR) +
                    binaryOp("and", IAND) +
                    binaryOp("or", IOR) +
                    binaryOp("xor", IXOR) + listOf(
                Key(FQ_NAMES._boolean.toSafe(), null, "not", 0) to Not,
                Key(FQ_NAMES.string.toSafe(), null, "plus", 1) to Concat,
                Key(FQ_NAMES.string.toSafe(), null, "get", 1) to StringGetChar,
                Key(FQ_NAMES.cloneable.toSafe(), null, "clone", 0) to Clone,
                Key(BUILT_INS_PACKAGE_FQ_NAME, FQ_NAMES.any.toSafe(), "toString", 0) to ToString,
                Key(BUILT_INS_PACKAGE_FQ_NAME, FQ_NAMES.string.toSafe(), "plus", 1) to StringPlus,
                Key(BUILT_INS_PACKAGE_FQ_NAME, null, "arrayOfNulls", 1) to NewArray
            ) + PrimitiveType.values().flatMap { type ->
                listOf(
                    Key(type.typeFqName, null, "compareTo", 1) to CompareTo,
                    Key(
                        COLLECTIONS_PACKAGE_FQ_NAME.child(Name.identifier(type.typeName.asString() + "Iterator")),
                        null,
                        "next",
                        0
                    ) to IteratorNext
                )
            } + arrayMethods()
    ).toMap()

    private val irMapping = hashMapOf<IrFunctionSymbol, IntrinsicMethod>()

    private fun createPrimitiveComparisonIntrinsics(typeToIrFun: Map<SimpleType, IrSimpleFunctionSymbol>, operator: KtSingleValueToken) {
        for ((type, irFunSymbol) in typeToIrFun) {
            irMapping[irFunSymbol] = PrimitiveComparison(type, operator)
        }
    }

    init {
        irMapping[irBuiltIns.eqeqSymbol] = Equals(KtTokens.EQEQ)
        irMapping[irBuiltIns.eqeqeqSymbol] = Equals(KtTokens.EQEQEQ)
        irMapping[irBuiltIns.ieee754equalsFunByOperandType[irBuiltIns.float]!!] = Ieee754Equals(Type.FLOAT_TYPE)
        irMapping[irBuiltIns.ieee754equalsFunByOperandType[irBuiltIns.double]!!] = Ieee754Equals(Type.DOUBLE_TYPE)
        irMapping[irBuiltIns.booleanNotSymbol] = Not

        createPrimitiveComparisonIntrinsics(irBuiltIns.lessFunByOperandType, KtTokens.LT)
        createPrimitiveComparisonIntrinsics(irBuiltIns.lessOrEqualFunByOperandType, KtTokens.LTEQ)
        createPrimitiveComparisonIntrinsics(irBuiltIns.greaterFunByOperandType, KtTokens.GT)
        createPrimitiveComparisonIntrinsics(irBuiltIns.greaterOrEqualFunByOperandType, KtTokens.GTEQ)

        irMapping[irBuiltIns.enumValueOfSymbol] = IrEnumValueOf()
        irMapping[irBuiltIns.noWhenBranchMatchedExceptionSymbol] = IrNoWhenBranchMatchedException()
        irMapping[irBuiltIns.illegalArgumentExceptionSymbol] = IrIllegalArgumentException()
        irMapping[irBuiltIns.throwNpeSymbol] = ThrowNPE()
    }

    fun getIntrinsic(symbol: IrFunctionSymbol): IntrinsicMethod? {
        intrinsicsMap[symbol.toKey()]?.let { return it }
        return irMapping[symbol]
    }

    data class Key(val owner: FqName, val receiverParameter: FqName?, val name: String, val valueParameterCount: Int)

    companion object {
        internal val INTRINSICS_CLASS_NAME = "kotlin/jvm/internal/Intrinsics"

        private val INC = Increment(1)
        private val DEC = Increment(-1)
        private val EQUALS = Equals(KtTokens.EQEQ)

        fun getReceiverParameterFqName(symbol: IrFunctionSymbol): FqName? =
            symbol.owner.extensionReceiverParameter?.type?.safeAs<IrSimpleType>()?.classifier?.owner?.let {
                when (it) {
                    is IrClass -> it.fqNameWhenAvailable
                    is IrTypeParameter -> FqName(it.name.asString())
                    else -> null
                }
            }

        fun IrFunctionSymbol.toKey(): Key? {
            return Key(
                owner.parent.safeAs<IrClass>()?.fqNameWhenAvailable ?: owner.parent.safeAs<IrPackageFragment>()?.fqName ?: return null,
                getReceiverParameterFqName(this),
                owner.name.asString(),
                owner.valueParameters.size
            )
        }

        fun binaryOp(methodName: String, opcode: Int): List<Pair<Key, IntrinsicMethod>> {
            val op = BinaryOp(opcode)
            return PrimitiveType.values().map { type ->
                Key(type.typeFqName, null, methodName, 1) to op
            }
        }

        fun arrayMethods(): List<Pair<Key, IntrinsicMethod>> =
            JvmPrimitiveType.values().flatMap {
                arrayMethods(it.primitiveType.arrayTypeFqName)
            } + arrayMethods(FQ_NAMES.array.toSafe())

        fun arrayMethods(arrayTypeFqName: FqName) = listOf(
            Key(arrayTypeFqName, null, "<get-size>", 0) to ArraySize,
            Key(arrayTypeFqName, null, "set", 2) to ArraySet,
            Key(arrayTypeFqName, null, "get", 1) to ArrayGet,
            Key(arrayTypeFqName, null, "clone", 0) to Clone,
            Key(arrayTypeFqName, null, "iterator", 0) to ArrayIterator,
            Key(arrayTypeFqName, null, "<init>", 2) to ArrayConstructor
        )
    }
}
