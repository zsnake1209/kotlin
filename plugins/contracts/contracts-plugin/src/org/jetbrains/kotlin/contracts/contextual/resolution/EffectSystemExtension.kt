/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.resolution

import org.jetbrains.kotlin.contracts.ESLambda
import org.jetbrains.kotlin.contracts.contextual.FactsBindingInfo
import org.jetbrains.kotlin.contracts.model.ExtensionEffect
import org.jetbrains.kotlin.contracts.model.structure.ESFunction
import org.jetbrains.kotlin.extensions.ContractsInfoForInvocation
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

internal fun collectDefiniteInvocationsOfContextualContracts(
    effect: ExtensionEffect,
    resolvedCall: ResolvedCall<*>,
    bindingContext: BindingContext
): ContractsInfoForInvocation? = when (effect) {
    is ContextProviderEffect -> extractProvider(effect, resolvedCall, bindingContext)
    is ContextVerifierEffect -> extractVerifier(effect, resolvedCall, bindingContext)
    is ContextCleanerEffect -> extractCleaner(effect, resolvedCall, bindingContext)
    else -> null
}

private fun extractProvider(
    effect: ContextProviderEffect,
    resolvedCall: ResolvedCall<*>,
    bindingContext: BindingContext
): ContractsInfoForInvocation? {
    val providerDeclaration = effect.providerDeclaration
    fun bindProvider(sourceElement: KtElement) = providerDeclaration.bind(sourceElement, effect.references, bindingContext)

    return when (effect.owner) {
        is ESFunction -> {
            val callExpression = resolvedCall.callExpression ?: return null
            val provider = bindProvider(callExpression) ?: return null
            ContractsInfoForInvocation(callExpression, FactsBindingInfo(provider))
        }
        is ESLambda -> {
            val lambda = effect.owner.lambda.functionLiteral
            val provider = bindProvider(lambda) ?: return null
            ContractsInfoForInvocation(lambda, FactsBindingInfo(provider))
        }
        else -> null
    }
}

private fun extractVerifier(
    effect: ContextVerifierEffect,
    resolvedCall: ResolvedCall<*>,
    bindingContext: BindingContext
): ContractsInfoForInvocation? {
    val verifierDeclaration = effect.verifierDeclaration
    fun bindVerifier(sourceElement: KtElement) = verifierDeclaration.bind(sourceElement, effect.references, bindingContext)

    return when (effect.owner) {
        is ESFunction -> {
            val callExpression = resolvedCall.callExpression ?: return null
            val verifier = bindVerifier(callExpression) ?: return null
            ContractsInfoForInvocation(callExpression, FactsBindingInfo(verifier))
        }
        is ESLambda -> {
            val lambda = effect.owner.functionLiteral
            val verifier = bindVerifier(lambda) ?: return null
            ContractsInfoForInvocation(lambda, FactsBindingInfo(verifier))
        }
        else -> null
    }
}

private fun extractCleaner(
    effect: ContextCleanerEffect,
    resolvedCall: ResolvedCall<*>,
    bindingContext: BindingContext
): ContractsInfoForInvocation? {
    val cleanerDeclaration = effect.cleanerDeclaration
    fun bindCleaner(sourceElement: KtElement) = cleanerDeclaration.bind(sourceElement, effect.references, bindingContext)

    return when (effect.owner) {
        is ESFunction -> {
            val callExpression = resolvedCall.callExpression ?: return null
            val cleaner = bindCleaner(callExpression) ?: return null
            ContractsInfoForInvocation(callExpression, FactsBindingInfo(cleaner))
        }
        is ESLambda -> {
            val lambda = effect.owner.functionLiteral
            val cleaner = bindCleaner(lambda) ?: return null
            ContractsInfoForInvocation(lambda, FactsBindingInfo(cleaner))
        }
        else -> null
    }
}

private val ResolvedCall<*>.callExpression: KtCallExpression?
    get() = call.callElement as? KtCallExpression

private val ESLambda.functionLiteral: KtFunctionLiteral
    get() = lambda.functionLiteral