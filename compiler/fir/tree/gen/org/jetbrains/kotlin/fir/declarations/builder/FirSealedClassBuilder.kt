/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.builder

import kotlin.contracts.*
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.builder.FirAnnotationContainerBuilder
import org.jetbrains.kotlin.fir.builder.FirBuilderDsl
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSealedClass
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.builder.AbstractFirRegularClassBuilder
import org.jetbrains.kotlin.fir.declarations.impl.FirSealedClassImpl
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.references.FirControlFlowGraphReference
import org.jetbrains.kotlin.fir.references.impl.FirEmptyControlFlowGraphReference
import org.jetbrains.kotlin.fir.scopes.FirScopeProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.visitors.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/*
 * This file was generated automatically
 * DO NOT MODIFY IT MANUALLY
 */

@FirBuilderDsl
class FirSealedClassBuilder : AbstractFirRegularClassBuilder, FirAnnotationContainerBuilder {
    override var source: FirSourceElement? = null
    override lateinit var session: FirSession
    override var resolvePhase: FirResolvePhase = FirResolvePhase.RAW_FIR
    override lateinit var origin: FirDeclarationOrigin
    override val annotations: MutableList<FirAnnotationCall> = mutableListOf()
    override val typeParameters: MutableList<FirTypeParameterRef> = mutableListOf()
    override lateinit var status: FirDeclarationStatus
    override lateinit var classKind: ClassKind
    override val declarations: MutableList<FirDeclaration> = mutableListOf()
    override lateinit var scopeProvider: FirScopeProvider
    override lateinit var name: Name
    override lateinit var symbol: FirRegularClassSymbol
    override var companionObject: FirRegularClass? = null
    override val superTypeRefs: MutableList<FirTypeRef> = mutableListOf()
    val inheritors: MutableList<ClassId> = mutableListOf()

    override fun build(): FirSealedClass {
        return FirSealedClassImpl(
            source,
            session,
            resolvePhase,
            origin,
            annotations,
            typeParameters,
            status,
            classKind,
            declarations,
            scopeProvider,
            name,
            symbol,
            companionObject,
            superTypeRefs,
            inheritors,
        )
    }


    @Deprecated("Modification of 'hasLazyNestedClassifiers' has no impact for FirSealedClassBuilder", level = DeprecationLevel.HIDDEN)
    override var hasLazyNestedClassifiers: Boolean
        get() = throw IllegalStateException()
        set(value) {
            throw IllegalStateException()
        }

    @Deprecated("Modification of 'controlFlowGraphReference' has no impact for FirSealedClassBuilder", level = DeprecationLevel.HIDDEN)
    override var controlFlowGraphReference: FirControlFlowGraphReference
        get() = throw IllegalStateException()
        set(value) {
            throw IllegalStateException()
        }
}

@OptIn(ExperimentalContracts::class)
inline fun buildSealedClass(init: FirSealedClassBuilder.() -> Unit): FirSealedClass {
    contract {
        callsInPlace(init, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
    }
    return FirSealedClassBuilder().apply(init).build()
}
