/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

class DebuggerFieldExpressionCodegenExtension : ExpressionCodegenExtension {
    override fun applyProperty(receiver: StackValue, resolvedCall: ResolvedCall<*>, c: ExpressionCodegenExtension.Context): StackValue? {
        val propertyDescriptor = resolvedCall.resultingDescriptor as? DebuggerFieldPropertyDescriptor ?: return null

        return StackValue.StackValueWithSimpleReceiver.field(
            c.typeMapper.mapType(propertyDescriptor.type),
            propertyDescriptor.ownerType,
            propertyDescriptor.field.name.asString(),
            false,
            receiver
        )
    }
}