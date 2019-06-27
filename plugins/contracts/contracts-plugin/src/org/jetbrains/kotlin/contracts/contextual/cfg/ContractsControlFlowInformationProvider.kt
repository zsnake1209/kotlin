/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.cfg

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cfg.pseudocode.Pseudocode
import org.jetbrains.kotlin.contracts.contextual.ContextualEffectSystem
import org.jetbrains.kotlin.contracts.contextual.model.ContextFamily
import org.jetbrains.kotlin.contracts.contextual.resolution.ContextCleanerEffectDeclaration
import org.jetbrains.kotlin.contracts.contextual.resolution.ContextProviderEffectDeclaration
import org.jetbrains.kotlin.contracts.contextual.resolution.ContextVerifierEffectDeclaration
import org.jetbrains.kotlin.contracts.description.ContractProviderKey
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.resolve.BindingContext

class ContractsControlFlowInformationProvider(
    private val function: KtFunction,
    private val pseudocode: Pseudocode,
    private val bindingContext: BindingContext,
    private val diagnosticSink: DiagnosticSink
) {
    private val project: Project = function.project

    fun analyze() {
        val declaredContracts = collectDeclaredContextualContracts()
        val pseudocodeEffectsData = getPseudocodeEffectsData(declaredContracts)

        val contextsInfo = pseudocodeEffectsData.computeResultingContexts() ?: return

        for (family in ContextualEffectSystem.getFamilies(project)) {
            val contexts = contextsInfo[family] ?: continue
            for (context in contexts) {
                context.reportRemaining(diagnosticSink, declaredContracts[family] ?: ContextContracts())
            }
        }
    }

    private fun getPseudocodeEffectsData(declaredContracts: Map<ContextFamily, ContextContracts>) =
        PseudocodeEffectsData(pseudocode, diagnosticSink, bindingContext, declaredContracts)

    private fun collectDeclaredContextualContracts(): Map<ContextFamily, ContextContracts> {
        val functionDescriptor = bindingContext[BindingContext.FUNCTION, function] ?: return emptyMap()
        val contractProvider = functionDescriptor.getUserData(ContractProviderKey) ?: return emptyMap()

        val contractDescription = contractProvider.getContractDescription()
        if (contractDescription == null || contractDescription.effects.isEmpty()) return emptyMap()

        val providers = contractDescription.effects
            .mapNotNull { it as? ContextProviderEffectDeclaration }
            .map { it.factory }
            .groupBy { it.family }

        val verifiers = contractDescription.effects
            .mapNotNull { it as? ContextVerifierEffectDeclaration }
            .map { it.factory }
            .groupBy { it.family }

        val cleaners = contractDescription.effects
            .mapNotNull { it as? ContextCleanerEffectDeclaration }
            .map { it.factory }
            .groupBy { it.family }

        val res = mutableMapOf<ContextFamily, ContextContracts>()

        for (family in ContextualEffectSystem.getFamilies(project)) {
            res[family] = ContextContracts(
                providers = providers[family] ?: emptyList(),
                verifiers = verifiers[family] ?: emptyList(),
                cleaners = cleaners[family] ?: emptyList()
            )
        }

        return res.withDefault { ContextContracts() }
    }
}