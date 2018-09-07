/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.js.resolve.diagnostics

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.checkers.DeclarationChecker
import org.jetbrains.kotlin.resolve.checkers.DeclarationCheckerContext
import org.jetbrains.kotlin.resolve.isInlineClass

class JsAdditionalReservedMembersAndConstructsForInlineClass : DeclarationChecker {

    companion object {
        private val reservedProperties = setOf("unbox")
    }

    override fun check(declaration: KtDeclaration, descriptor: DeclarationDescriptor, context: DeclarationCheckerContext) {
        val containingDeclaration = descriptor.containingDeclaration ?: return
        if (!containingDeclaration.isInlineClass()) return

        if (descriptor !is PropertyDescriptor) return

        val ktProperty = declaration as? KtNamedDeclaration ?: return
        val functionName = descriptor.name.asString()
        if (functionName in reservedProperties) {
            val nameIdentifier = ktProperty.nameIdentifier ?: return
            context.trace.report(Errors.RESERVED_MEMBER_INSIDE_INLINE_CLASS.on(nameIdentifier, functionName))
        }
    }
}