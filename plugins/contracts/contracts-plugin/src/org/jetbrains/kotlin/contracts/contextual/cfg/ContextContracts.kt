/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.cfg

import org.jetbrains.kotlin.contracts.contextual.model.CleanerDeclaration
import org.jetbrains.kotlin.contracts.contextual.model.ProviderDeclaration
import org.jetbrains.kotlin.contracts.contextual.model.VerifierDeclaration

class ContextContracts internal constructor(
    val providers: Collection<ProviderDeclaration> = emptyList(),
    val verifiers: Collection<VerifierDeclaration> = emptyList(),
    val cleaners: Collection<CleanerDeclaration> = emptyList()
)