/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.extensions

import org.jetbrains.kotlin.cfg.pseudocode.Pseudocode
import org.jetbrains.kotlin.contracts.extensions.AbstractContractsExtension
import org.jetbrains.kotlin.contracts.extensions.ExtensionBindingContextData
import org.jetbrains.kotlin.contracts.extensions.ExtensionContractComponents
import org.jetbrains.kotlin.contracts.model.ExtensionEffect
import org.jetbrains.kotlin.contracts.parsing.ContractCallContext
import org.jetbrains.kotlin.contracts.parsing.ContractParsingDiagnosticsCollector
import org.jetbrains.kotlin.contracts.parsing.ExtensionParserDispatcher
import org.jetbrains.kotlin.contracts.parsing.PsiContractParserDispatcher
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall

interface ContractsExtension : AbstractContractsExtension {
    companion object : ProjectExtensionDescriptor<ContractsExtension>(
        "org.jetbrains.kotlin.contractsExtension",
        ContractsExtension::class.java
    )

    fun getPsiParserDispatcher(
        collector: ContractParsingDiagnosticsCollector,
        callContext: ContractCallContext,
        dispatcher: PsiContractParserDispatcher
    ): ExtensionParserDispatcher

    fun collectDefiniteInvocations(
        effect: ExtensionEffect,
        resolvedCall: ResolvedCall<*>,
        bindingContext: BindingContext
    ): ContractsInfoForInvocation?

    fun analyzeFunction(
        function: KtFunction,
        pseudocode: Pseudocode,
        bindingContext: BindingContext,
        diagnosticSink: DiagnosticSink
    )
}

data class ContractsInfoForInvocation(val expression: KtExpression, val data: ExtensionBindingContextData)

@Suppress("UNCHECKED_CAST")
val ExtensionContractComponents.contractExtensions: List<ContractsExtension>
    get() = abstractContractsExtensions as List<ContractsExtension>