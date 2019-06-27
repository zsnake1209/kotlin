/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.resolution

import org.jetbrains.kotlin.contracts.contextual.model.*
import org.jetbrains.kotlin.contracts.description.EffectDeclaration
import org.jetbrains.kotlin.contracts.description.ExtensionEffectDeclaration
import org.jetbrains.kotlin.contracts.description.expressions.ContractDescriptionValue
import org.jetbrains.kotlin.contracts.description.expressions.FunctionReference
import org.jetbrains.kotlin.contracts.description.expressions.VariableReference

interface ContextualEffectDeclaration<T : ContextEntity, R : ContextEntityDeclaration<T>, O : ContractDescriptionValue>
    : ExtensionEffectDeclaration {
    val factory: R
    val references: List<ContractDescriptionValue>
    val owner: O
}

interface SimpleContextualEffectDeclaration<T : ContextEntity, R : ContextEntityDeclaration<T>>
    : ContextualEffectDeclaration<T, R, FunctionReference> {
    companion object {
        operator fun invoke(
            factory: ContextEntityDeclaration<*>,
            owner: FunctionReference
        ): EffectDeclaration = when (factory) {
            is ProviderDeclaration -> ContextProviderEffectDeclaration(factory, factory.references, owner)
            is VerifierDeclaration -> ContextVerifierEffectDeclaration(factory, factory.references, owner)
            is CleanerDeclaration -> ContextCleanerEffectDeclaration(factory, factory.references, owner)
        }
    }
}

interface LambdaContextualEffectDeclaration<T : ContextEntity, R : ContextEntityDeclaration<T>> :
    ContextualEffectDeclaration<T, R, VariableReference> {
    companion object {
        operator fun invoke(
            factory: ContextEntityDeclaration<*>,
            owner: VariableReference
        ): EffectDeclaration = when (factory) {
            is ProviderDeclaration -> LambdaContextProviderEffectDeclaration(factory, factory.references, owner)
            is VerifierDeclaration -> LambdaContextVerifierEffectDeclaration(factory, factory.references, owner)
            is CleanerDeclaration -> LambdaContextCleanerEffectDeclaration(factory, factory.references, owner)
        }
    }
}

data class ContextProviderEffectDeclaration(
    override val factory: ProviderDeclaration,
    override val references: List<ContractDescriptionValue>,
    override val owner: FunctionReference
) : SimpleContextualEffectDeclaration<ContextProvider, ProviderDeclaration>

data class LambdaContextProviderEffectDeclaration(
    override val factory: ProviderDeclaration,
    override val references: List<ContractDescriptionValue>,
    override val owner: VariableReference
) : LambdaContextualEffectDeclaration<ContextProvider, ProviderDeclaration>

data class ContextVerifierEffectDeclaration(
    override val factory: VerifierDeclaration,
    override val references: List<ContractDescriptionValue>,
    override val owner: FunctionReference
) : SimpleContextualEffectDeclaration<ContextVerifier, VerifierDeclaration>

data class LambdaContextVerifierEffectDeclaration(
    override val factory: VerifierDeclaration,
    override val references: List<ContractDescriptionValue>,
    override val owner: VariableReference
) : LambdaContextualEffectDeclaration<ContextVerifier, VerifierDeclaration>

data class ContextCleanerEffectDeclaration(
    override val factory: CleanerDeclaration,
    override val references: List<ContractDescriptionValue>,
    override val owner: FunctionReference
) : SimpleContextualEffectDeclaration<ContextCleaner, CleanerDeclaration>

data class LambdaContextCleanerEffectDeclaration(
    override val factory: CleanerDeclaration,
    override val references: List<ContractDescriptionValue>,
    override val owner: VariableReference
) : LambdaContextualEffectDeclaration<ContextCleaner, CleanerDeclaration>