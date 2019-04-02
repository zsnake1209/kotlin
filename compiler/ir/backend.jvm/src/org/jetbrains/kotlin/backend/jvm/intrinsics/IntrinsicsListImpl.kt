/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.intrinsics

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.codegen.intrinsics.IntrinsicsList
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.org.objectweb.asm.Type

object IntrinsicsListImpl : IntrinsicsList<IntrinsicMethod> {
    override val arrayConstructor = ArrayConstructor
    override val arrayGet = ArrayGet()
    override val arrayIterator = ArrayIterator()
    override val arrayOf = ArrayOf()
    override val arrayOfNulls = NewArray()
    override val arraySet = ArraySet()
    override val arraySize = ArraySize()
    override val clone = Clone()
    override val compareTo = CompareTo()
    override val hashCode = HashCode()
    override val inv = Inv()
    override val checkIsArrayOf = IsArrayOf()
    override val iteratorNext = IteratorNext()
    override val javaClassProperty = JavaClassProperty
    override val java8UlongDivide = null
    override val java8UlongRemainder = null
    override val kCallableNameProperty = null
    override val kClassJavaObjectTypeProperty = null
    override val kCLassJavaPrimitiveTypeProperty = null
    override val kClassJavaProperty = KClassJavaProperty()
    override val lateinitIsInitialized = null
    override val monitorEnter = MonitorInstruction.MONITOR_ENTER
    override val monitorExit = MonitorInstruction.MONITOR_EXIT
    override val mutableMapSet = null
    override val not = Not()
    override val numberCast = NumberCast()
    override val rangeTo = RangeTo()
    override val stringConcat = Concat()
    override val stringGetChar = StringGetChar()
    override val stringPlus = StringPlus()
    override val stringTrimIndent = null
    override val stringTrimMargin = null
    override val toString = ToString()
    override val unaryMinus = UnaryMinus()
    override val unaryPlus = UnaryPlus()

    override fun binaryOpIntrinsic(opcode: Int) = BinaryOp(opcode)

    override fun equalsIntrinsic(type: PrimitiveType): IntrinsicMethod {
        val asmPrimitiveType = AsmTypes.valueTypeForPrimitive(type)
        return if (asmPrimitiveType === Type.FLOAT_TYPE || asmPrimitiveType === Type.DOUBLE_TYPE) {
            TotalOrderEquals(asmPrimitiveType)
        } else {
            Equals(KtTokens.EQEQ)
        }
    }

    override fun incrementIntrinsic(myDelta: Int) = Increment(myDelta)
}