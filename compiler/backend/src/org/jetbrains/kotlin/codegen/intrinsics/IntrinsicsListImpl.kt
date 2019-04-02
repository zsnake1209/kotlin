/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.intrinsics

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.resolve.jvm.JvmPrimitiveType

class IntrinsicsListImpl(
    jvmTarget: JvmTarget,
    canReplaceStdlibRuntimeApiBehavior: Boolean = false,
    private val shouldThrowNpeOnExplicitEqualsForBoxedNull: Boolean = true
) : IntrinsicsList<IntrinsicMethod> {
    override val arrayConstructor = ArrayConstructor
    override val arrayGet = ArrayGet()
    override val arrayIterator = ArrayIterator()
    override val arrayOf = ArrayOf()
    override val arrayOfNulls = NewArray()
    override val arraySet = ArraySet()
    override val arraySize = ArraySize()
    override val clone = Clone()
    override val compareTo = CompareTo()
    override val hashCode = HashCode(jvmTarget)
    override val inv = Inv()
    override val checkIsArrayOf = IsArrayOf()
    override val iteratorNext = IteratorNext()
    override val javaClassProperty = JavaClassProperty
    override val java8UlongDivide = if (jvmTarget.compareTo(JvmTarget.JVM_1_8) >= 0) Java8ULongDivide() else null
    override val java8UlongRemainder = if (jvmTarget.compareTo(JvmTarget.JVM_1_8) >= 0) Java8ULongRemainder() else null
    override val kCallableNameProperty = KCallableNameProperty()
    override val kClassJavaObjectTypeProperty = KClassJavaObjectTypeProperty()
    override val kCLassJavaPrimitiveTypeProperty = KClassJavaPrimitiveTypeProperty()
    override val kClassJavaProperty = KClassJavaProperty()
    override val lateinitIsInitialized = LateinitIsInitialized
    override val monitorEnter = MonitorInstruction.MONITOR_ENTER
    override val monitorExit = MonitorInstruction.MONITOR_EXIT
    override val mutableMapSet = MutableMapSet()
    override val not = Not()
    override val numberCast = NumberCast()
    override val rangeTo = RangeTo()
    override val stringConcat = Concat()
    override val stringGetChar = StringGetChar()
    override val stringPlus = StringPlus()
    override val stringTrimIndent = if (canReplaceStdlibRuntimeApiBehavior) TrimIndent() else null
    override val stringTrimMargin = if (canReplaceStdlibRuntimeApiBehavior) TrimMargin() else null
    override val toString = ToString()
    override val unaryMinus = UnaryMinus()
    override val unaryPlus = UnaryPlus()

    override fun binaryOpIntrinsic(opcode: Int) = BinaryOp(opcode)

    override fun equalsIntrinsic(type: PrimitiveType) = if (shouldThrowNpeOnExplicitEqualsForBoxedNull) {
        val wrapperType = AsmUtil.asmTypeByFqNameWithoutInnerClasses(JvmPrimitiveType.get(type).getWrapperFqName())
        EqualsThrowingNpeForNullReceiver(wrapperType)
    } else {
        Equals()
    }

    override fun incrementIntrinsic(myDelta: Int) = Increment(myDelta)
}