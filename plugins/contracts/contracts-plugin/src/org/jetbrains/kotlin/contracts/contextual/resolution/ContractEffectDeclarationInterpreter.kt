/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.resolution

import org.jetbrains.kotlin.contracts.description.EffectDeclaration
import org.jetbrains.kotlin.contracts.description.expressions.FunctionReference
import org.jetbrains.kotlin.contracts.description.expressions.LambdaParameterReceiverReference
import org.jetbrains.kotlin.contracts.description.expressions.VariableReference
import org.jetbrains.kotlin.contracts.interpretation.ContractInterpretationDispatcher
import org.jetbrains.kotlin.contracts.interpretation.EffectDeclarationInterpreter
import org.jetbrains.kotlin.contracts.model.ESEffect
import org.jetbrains.kotlin.contracts.model.structure.AbstractESValue
import org.jetbrains.kotlin.contracts.model.structure.ESFunction
import org.jetbrains.kotlin.contracts.model.structure.ESVariable

class ContractEffectDeclarationInterpreter(private val dispatcher: ContractInterpretationDispatcher) : EffectDeclarationInterpreter {
    override fun tryInterpret(effectDeclaration: EffectDeclaration): ESEffect? = when (effectDeclaration) {
        is ContextProviderEffectDeclaration -> interpretContextProviderEffect(effectDeclaration)
        is LambdaContextProviderEffectDeclaration -> interpretLambdaContextProviderEffect(effectDeclaration)
        is ContextVerifierEffectDeclaration -> interpretContextVerifierEffect(effectDeclaration)
        is LambdaContextVerifierEffectDeclaration -> interpretLambdaContextVerifierEffect(effectDeclaration)
        is ContextCleanerEffectDeclaration -> interpretContextCleaner(effectDeclaration)
        is LambdaContextCleanerEffectDeclaration -> interpretLambdaContextCleaner(effectDeclaration)
        else -> null
    }

    private fun SimpleContextualEffectDeclaration<*, *>.interpretOwner(): ESFunction? = dispatcher.interpretFunction(owner)
    private fun LambdaContextualEffectDeclaration<*, *>.interpretOwner(): ESVariable? = dispatcher.interpretVariable(owner)
    private fun ContextualEffectDeclaration<*, *, *>.interpretReferences(): List<AbstractESValue?> = references.map {
        when (it) {
            is VariableReference -> dispatcher.interpretVariable(it)
            is FunctionReference -> dispatcher.interpretFunction(it)
            is LambdaParameterReceiverReference -> dispatcher.interpretLambdaParameterReceiverReference(it)
            else -> throw AssertionError("Illegal type of ContractDescriptionValue type: ${it::class}")
        }
    }

    private fun interpretContextProviderEffect(effectDeclaration: ContextProviderEffectDeclaration): ESEffect? {
        return ContextProviderEffect(
            effectDeclaration.factory,
            effectDeclaration.interpretReferences(),
            effectDeclaration.interpretOwner() ?: return null
        )
    }

    private fun interpretLambdaContextProviderEffect(effectDeclaration: LambdaContextProviderEffectDeclaration): ESEffect? {
        return ContextProviderEffect(
            effectDeclaration.factory,
            effectDeclaration.interpretReferences(),
            effectDeclaration.interpretOwner() ?: return null
        )
    }

    private fun interpretContextVerifierEffect(effectDeclaration: ContextVerifierEffectDeclaration): ESEffect? {
        return ContextVerifierEffect(
            effectDeclaration.factory,
            effectDeclaration.interpretReferences(),
            effectDeclaration.interpretOwner() ?: return null
        )
    }

    private fun interpretLambdaContextVerifierEffect(effectDeclaration: LambdaContextVerifierEffectDeclaration): ESEffect? {
        return ContextVerifierEffect(
            effectDeclaration.factory,
            effectDeclaration.interpretReferences(),
            effectDeclaration.interpretOwner() ?: return null
        )
    }

    private fun interpretContextCleaner(effectDeclaration: ContextCleanerEffectDeclaration): ESEffect? {
        return ContextCleanerEffect(
            effectDeclaration.factory,
            effectDeclaration.interpretReferences(),
            effectDeclaration.interpretOwner() ?: return null
        )
    }

    private fun interpretLambdaContextCleaner(effectDeclaration: LambdaContextCleanerEffectDeclaration): ESEffect? {
        return ContextCleanerEffect(
            effectDeclaration.factory,
            effectDeclaration.interpretReferences(),
            effectDeclaration.interpretOwner() ?: return null
        )
    }
}