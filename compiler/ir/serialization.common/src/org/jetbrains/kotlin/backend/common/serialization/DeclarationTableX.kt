/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization

import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignature
import org.jetbrains.kotlin.backend.common.serialization.signature.IdSignatureSerializer
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.util.KotlinMangler


interface IdSignatureClashTracker {
    fun commit(declaration: IrDeclaration, signature: IdSignature)

    companion object {
        val DEFAULT_TRACKER = object : IdSignatureClashTracker {
            override fun commit(declaration: IrDeclaration, signature: IdSignature) {}
        }
    }
}

abstract class GlobalDeclarationTableX(
    val signaturer: IdSignatureSerializer,
    private val mangler: KotlinMangler,
    private val clashTracker: IdSignatureClashTracker
) {
    private val table = mutableMapOf<IrDeclaration, IdSignature>()

    constructor(signaturer: IdSignatureSerializer, mangler: KotlinMangler) : this(
        signaturer,
        mangler,
        IdSignatureClashTracker.DEFAULT_TRACKER
    )

    protected fun loadKnownBuiltins(builtIns: IrBuiltIns) {
        val mask = 1L shl 63
        builtIns.knownBuiltinsX.forEach {
            val index = mangler.run { it.mangle.hashMangle }
            table[it] = IdSignature.BuiltInSignature(it.mangle, index or mask).also { id -> clashTracker.commit(it, id) }
        }
    }

    open fun computeUniqIdByDeclaration(declaration: IrDeclaration): IdSignature {
        return table.getOrPut(declaration) {
            signaturer.composePublicIdSignature(declaration).also { clashTracker.commit(declaration, it) }
        }
    }

    fun isExportedDeclaration(declaration: IrDeclaration): Boolean = with(mangler) { declaration.isExported() }
}

open class DeclarationTableX(private val globalDeclarationTable: GlobalDeclarationTableX) {
    private val table = mutableMapOf<IrDeclaration, IdSignature>()
    private val signaturer = globalDeclarationTable.signaturer

    private fun IrDeclaration.isLocalDeclaration(): Boolean {
        return !isExportedDeclaration(this)
    }

    fun isExportedDeclaration(declaration: IrDeclaration) = globalDeclarationTable.isExportedDeclaration(declaration)

    protected open fun tryComputeBackendSpecificUniqId(declaration: IrDeclaration): IdSignature? = null

    private fun computeUniqIdByDeclaration(declaration: IrDeclaration): IdSignature {
        tryComputeBackendSpecificUniqId(declaration)?.let { return it }
        return if (declaration.isLocalDeclaration()) {
            table.getOrPut(declaration) { signaturer.composeFileLocalIdSignature(declaration) }
        } else globalDeclarationTable.computeUniqIdByDeclaration(declaration)
    }

    fun uniqIdByDeclaration(declaration: IrDeclaration): IdSignature {
        return computeUniqIdByDeclaration(declaration)
    }
}

// This is what we pre-populate tables with
val IrBuiltIns.knownBuiltinsX
    get() = irBuiltInsSymbols
