/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.resolution

import org.jetbrains.kotlin.contracts.model.ESValue
import org.jetbrains.kotlin.contracts.model.ExtensionEffect
import org.jetbrains.kotlin.contracts.model.visitors.ExtensionReducer
import org.jetbrains.kotlin.contracts.model.visitors.Reducer

class ContextualEffectReducer(private val reducer: Reducer) : ExtensionReducer {
    override fun reduce(effect: ExtensionEffect): ExtensionEffect = when(effect) {
        is ContextProviderEffect -> ContextProviderEffect(effect.providerDeclaration, effect.reducedReferences(), effect.owner)
        is ContextVerifierEffect -> ContextVerifierEffect(effect.verifierDeclaration, effect.reducedReferences(), effect.owner)
        is ContextCleanerEffect -> ContextCleanerEffect(effect.cleanerDeclaration, effect.reducedReferences(), effect.owner)
        else -> effect
    }

    private fun ContextualEffect.reducedReferences(): List<ESValue?> = references.map { it?.accept(reducer) as? ESValue }
}