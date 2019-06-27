/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.resolution

import org.jetbrains.kotlin.contracts.model.ESEffect
import org.jetbrains.kotlin.contracts.model.ESValue
import org.jetbrains.kotlin.contracts.model.ExtensionEffect
import org.jetbrains.kotlin.contracts.model.structure.ESFunction
import org.jetbrains.kotlin.contracts.model.visitors.Substitutor

fun substituteContextualEffect(effect: ExtensionEffect, substitutor: Substitutor): ESEffect? {
    if (effect !is ContextualEffect) return null
    val substitutedOwner = if (effect.owner is ESFunction)
        effect.owner
    else
        effect.owner.accept(substitutor) as? ESValue ?: return null

    val substitutedReferences = effect.references.map { it?.accept(substitutor) as? ESValue }

    return when (effect) {
        is ContextProviderEffect -> ContextProviderEffect(effect.providerDeclaration, substitutedReferences, substitutedOwner)
        is ContextVerifierEffect -> ContextVerifierEffect(effect.verifierDeclaration, substitutedReferences, substitutedOwner)
        is ContextCleanerEffect -> ContextCleanerEffect(effect.cleanerDeclaration, substitutedReferences, substitutedOwner)
    }
}
