/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.model

import org.jetbrains.kotlin.contracts.description.expressions.ContractDescriptionValue
import org.jetbrains.kotlin.contracts.model.ESValue
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext

sealed class ContextEntityDeclaration<T : ContextEntity> {
    abstract val family: ContextFamily
    abstract val references: List<ContractDescriptionValue>
    abstract fun bind(sourceElement: KtElement, references: List<ESValue?>, bindingContext: BindingContext): T?
}

abstract class ProviderDeclaration : ContextEntityDeclaration<ContextProvider>()
abstract class VerifierDeclaration : ContextEntityDeclaration<ContextVerifier>()
abstract class CleanerDeclaration : ContextEntityDeclaration<ContextCleaner>()