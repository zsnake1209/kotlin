/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.parsing

import org.jetbrains.kotlin.contracts.description.EffectDeclaration
import org.jetbrains.kotlin.contracts.parsing.*

import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.utils.keysToMap

class PsiContextualContractsParserDispatcher(
    private val collector: ContractParsingDiagnosticsCollector,
    private val callContext: ContractCallContext,
    dispatcher: PsiContractParserDispatcher
) : ExtensionParserDispatcher {
    companion object {
        private val FACT_NAMES = listOf(
            ContractsDslNames.PROVIDES_CONTEXT,
            ContractsDslNames.STARTS_CONTEXT,
            ContractsDslNames.CLOSES_CONTEXT,
            ContractsDslNames.REQUIRES_CONTEXT,
            ContractsDslNames.BLOCK_NOT_EXPECTS_TO_CONTEXT
        )

        private val LAMBDA_FACT_NAMES = listOf(
            ContractsDslNames.CALLS_BLOCK_IN_CONTEXT,
            ContractsDslNames.BLOCK_EXPECTS_TO_CONTEXT,
            ContractsDslNames.BLOCK_REQUIRES_NOT_CONTEXT
        )
    }

    private val effectsParsers: Map<Name, PsiEffectParser>

    init {
        val psiFactParser = PsiFactParser(collector, callContext, dispatcher)
        val psiLambdaFactParser = PsiLambdaFactParser(collector, callContext, dispatcher)
        val parsers = mutableMapOf<Name, PsiEffectParser>()
        parsers += FACT_NAMES.keysToMap { psiFactParser }
        parsers += LAMBDA_FACT_NAMES.keysToMap { psiLambdaFactParser }
        effectsParsers = parsers
    }

    override fun parseEffects(expression: KtExpression): Collection<EffectDeclaration> {
        val returnType = expression.getType(callContext.bindingContext) ?: return emptyList()
        val parser = effectsParsers[returnType.constructor.declarationDescriptor?.name]
        if (parser == null) {
            collector.badDescription("unrecognized effect", expression)
            return emptyList()
        }

        return parser.tryParseEffect(expression)
    }
}