/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.intrinsics

import org.jetbrains.kotlin.codegen.Callable
import org.jetbrains.kotlin.codegen.CallableMethod
import org.jetbrains.org.objectweb.asm.Type

class MutableMapSet : IntrinsicMethod() {
    override fun toCallable(method: CallableMethod): Callable =
        object : IntrinsicCallable(
            method,
            { v ->
                v.invokeinterface("java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
                v.pop()
            }
        ) {
            override val parameterTypes: Array<Type>
                get() = method.valueParameterTypes.toTypedArray()
        }
}