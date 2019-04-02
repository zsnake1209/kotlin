/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.intrinsics

import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor

interface GeneralIntrinsicMethod {
    // only method needed by IntrinsicMap
    fun isApplicableToOverload(descriptor: CallableMemberDescriptor): Boolean = true
}

interface IntrinsicsList<IntrinsicMethod : GeneralIntrinsicMethod> {
    val arrayConstructor: IntrinsicMethod
    val arrayGet: IntrinsicMethod
    val arrayIterator: IntrinsicMethod
    val arrayOf: IntrinsicMethod
    val arrayOfNulls: IntrinsicMethod
    val arraySet: IntrinsicMethod
    val arraySize: IntrinsicMethod
    val clone: IntrinsicMethod
    val compareTo: IntrinsicMethod
    val hashCode: IntrinsicMethod
    val inv: IntrinsicMethod
    val checkIsArrayOf: IntrinsicMethod
    val iteratorNext: IntrinsicMethod
    val javaClassProperty: IntrinsicMethod
    val java8UlongDivide: IntrinsicMethod?
    val java8UlongRemainder: IntrinsicMethod?
    val kCallableNameProperty: IntrinsicMethod?
    val kClassJavaObjectTypeProperty: IntrinsicMethod?
    val kCLassJavaPrimitiveTypeProperty: IntrinsicMethod?
    val kClassJavaProperty: IntrinsicMethod?
    val lateinitIsInitialized: IntrinsicMethod?
    val monitorEnter: IntrinsicMethod
    val monitorExit: IntrinsicMethod
    val mutableMapSet: IntrinsicMethod?
    val not: IntrinsicMethod
    val numberCast: IntrinsicMethod
    val rangeTo: IntrinsicMethod
    val stringConcat: IntrinsicMethod
    val stringGetChar: IntrinsicMethod
    val stringPlus: IntrinsicMethod
    val stringTrimIndent: IntrinsicMethod?
    val stringTrimMargin: IntrinsicMethod?
    val toString: IntrinsicMethod
    val unaryMinus: IntrinsicMethod
    val unaryPlus: IntrinsicMethod

    fun binaryOpIntrinsic(opcode: Int): IntrinsicMethod
    fun equalsIntrinsic(type: PrimitiveType): IntrinsicMethod
    fun incrementIntrinsic(myDelta: Int): IntrinsicMethod
}