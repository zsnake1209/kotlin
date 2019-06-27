/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.extensions

import org.jetbrains.kotlin.contracts.interpretation.ExtensionEffectDeclarationInterpreterConstructor
import org.jetbrains.kotlin.contracts.model.functors.ExtensionSubstitutor
import org.jetbrains.kotlin.contracts.model.visitors.ExtensionReducerConstructor

interface AbstractContractsExtension {
    val id: String

    fun getEffectDeclarationInterpreterConstructor(): ExtensionEffectDeclarationInterpreterConstructor

    fun getExtensionReducerConstructor(): ExtensionReducerConstructor

    fun getExtensionSubstitutor(): ExtensionSubstitutor

    fun emptyBindingContextData(): ExtensionBindingContextData
}

class ExtensionContractComponents(
    val abstractContractsExtensions: List<AbstractContractsExtension>
) {
    companion object {
        val DEFAULT = ExtensionContractComponents(emptyList())
    }

    val extensionInterpretersConstructors by lazy { abstractContractsExtensions.map { it.getEffectDeclarationInterpreterConstructor() }}
    val extensionReducerConstructors by lazy { abstractContractsExtensions.map { it.getExtensionReducerConstructor() }}
    val extensionSubstitutors by lazy { abstractContractsExtensions.map { it.getExtensionSubstitutor() } }
}