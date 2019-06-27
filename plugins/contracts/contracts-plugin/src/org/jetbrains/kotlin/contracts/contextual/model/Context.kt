/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.model

import org.jetbrains.kotlin.contracts.contextual.cfg.ContextContracts
import org.jetbrains.kotlin.diagnostics.DiagnosticSink

interface ContextFamily {
    val id: String
    val combiner: ContextCombiner
    val emptyContext: Context
}

interface Context {
    val family: ContextFamily
    fun reportRemaining(sink: DiagnosticSink, declaredContracts: ContextContracts)
}

interface ContextEntity {
    val family: ContextFamily
}

interface ContextProvider : ContextEntity

interface ContextVerifier : ContextEntity {
    fun verify(contexts: List<Context>, diagnosticSink: DiagnosticSink, declaredContracts: ContextContracts)
}

interface ContextCleaner : ContextEntity {
    fun cleanupProcessed(context: Context): Context
}

interface ContextCombiner {
    fun or(a: Context, b: Context): Context
    fun combine(context: Context, provider: ContextProvider): Context
}