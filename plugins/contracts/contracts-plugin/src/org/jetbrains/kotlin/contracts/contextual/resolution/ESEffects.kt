/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.resolution

import org.jetbrains.kotlin.contracts.contextual.model.CleanerDeclaration
import org.jetbrains.kotlin.contracts.contextual.model.ProviderDeclaration
import org.jetbrains.kotlin.contracts.contextual.model.VerifierDeclaration
import org.jetbrains.kotlin.contracts.model.ESEffect
import org.jetbrains.kotlin.contracts.model.ESValue
import org.jetbrains.kotlin.contracts.model.ExtensionEffect

sealed class ContextualEffect(val references: List<ESValue?>, val owner: ESValue) : ExtensionEffect() {
    final override fun isImplies(other: ESEffect): Boolean? = null
}

class ContextProviderEffect(
    val providerDeclaration: ProviderDeclaration,
    references: List<ESValue?>,
    owner: ESValue
) : ContextualEffect(references, owner)

class ContextVerifierEffect(
    val verifierDeclaration: VerifierDeclaration,
    references: List<ESValue?>,
    owner: ESValue
) : ContextualEffect(references, owner)

class ContextCleanerEffect(
    val cleanerDeclaration: CleanerDeclaration,
    references: List<ESValue?>,
    owner: ESValue
) : ContextualEffect(references, owner)