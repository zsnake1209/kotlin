/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.checkers

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.contracts.parsing.isContractCallDescriptor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.isFirstStatement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.LexicalScopeKind

object ContractNotAllowedCallChecker : CallChecker {
    override fun check(resolvedCall: ResolvedCall<*>, reportOn: PsiElement, context: CallCheckerContext) {
        if (reportOn !is KtElement) return
        val descriptor = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return
        if (!descriptor.isContractCallDescriptor()) return

        val callElement = resolvedCall.call.callElement
        var hasErrors = false

        fun contractNotAllowed(message: String) {
            hasErrors = true
            context.trace.report(Errors.CONTRACT_NOT_ALLOWED.on(reportOn, message))
        }

        val scope = context.scope
        val functionDescriptor = scope.ownerDescriptor as? FunctionDescriptor

        if (functionDescriptor == null || functionDescriptor is PropertyAccessorDescriptor)
            contractNotAllowed("Contracts are allowed only for functions")

        var inFunctionBodyBlock = true

        val newContractsPermissionEnabled = context.languageVersionSettings.supportsFeature(LanguageFeature.NewPermissionsForContractsDeclaration)

        val containingDeclaration = scope.ownerDescriptor.containingDeclaration

        val containingDeclarationAcceptable = if (newContractsPermissionEnabled) {
            containingDeclaration is PackageFragmentDescriptor || containingDeclaration is ClassDescriptor
        } else {
            containingDeclaration is PackageFragmentDescriptor
        }

        if (!containingDeclarationAcceptable
            || scope.kind != LexicalScopeKind.CODE_BLOCK
            || (scope.parent as? LexicalScope)?.kind != LexicalScopeKind.FUNCTION_INNER_SCOPE
        ) {
            if (scope.kind == LexicalScopeKind.FUNCTION_INNER_SCOPE) {
                contractNotAllowed("Contracts are allowed only in function body block")
                inFunctionBodyBlock = false
            } else {
                if (newContractsPermissionEnabled) {
                    contractNotAllowed("Contracts are allowed only for top-level functions and final not override member functions")
                } else {
                    contractNotAllowed("Contracts are allowed only for top-level functions")
                }
            }
        }

        if (functionDescriptor?.isOperator == true) contractNotAllowed("Contracts are not allowed for operator functions")

        val modalityAcceptable = if (newContractsPermissionEnabled) {
            functionDescriptor?.isOverridableOrOverrides == false
        } else {
            functionDescriptor?.isOverridable == false
        }

        if (!modalityAcceptable) contractNotAllowed("Contracts are not allowed for open functions or override functions")

        if (!callElement.isFirstStatement() && inFunctionBodyBlock) {
            contractNotAllowed("Contract should be the first statement")
        }

        if (hasErrors) {
            context.trace.record(BindingContext.CONTRACT_NOT_ALLOWED, callElement, true)
        }
    }
}