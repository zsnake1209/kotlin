/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.fir.declarations.impl.FirEnumEntryImpl
import org.jetbrains.kotlin.fir.expressions.FirAnnotationContainer
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.fir.visitors.FirVisitor

interface FirClass : FirDeclarationContainer, FirStatement, FirAnnotationContainer {
    // including delegated types
    val superTypeRefs: List<FirTypeRef>

    val classKind: ClassKind

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R =
        visitor.visitClass(this, data)

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        for (superType in superTypeRefs) {
            superType.accept(visitor, data)
        }
        var constructorFound = false
        for (declaration in declarations) {
            declaration.accept(visitor, data)
            if (!constructorFound && declaration is FirConstructor) {
                for (typeParameter in declaration.typeParameters) {
                    typeParameter.accept(visitor, data)
                }
                constructorFound = true
            }
        }
    }
}

val FirClassSymbol.superConeTypes: List<ConeClassLikeType>
    get() {
        // Cast to AbstractFirBasedSymbol<*> is needed to avoid CHECKCAST to FirRegularClass (interface)
        // that would happen in if it was 'this.fir' while we're trying to avoid interface calls/checkcasts in hot-spots
        // NB: Looks like JVM doesn't perform devirtualization here because there are more then two implementations of FirRegularClass
        val fir = (this as AbstractFirBasedSymbol<*>).fir

        // TODO: Remove FirRegularClass as a supertype for enum entry
        return when (fir) {
            is FirAbstractRegularClass -> fir.superConeTypes
            is FirEnumEntryImpl -> fir.superConeTypes
            else -> error("Unexpected implementation of FirRegularClass: ${fir::class}")
        }
    }

private val FirAbstractRegularClass.superConeTypes get() = superTypeRefs.coneTypeSafeRefList()
private val FirEnumEntryImpl.superConeTypes get() = superTypeRefs.coneTypeSafeRefList()

private fun List<FirTypeRef>.coneTypeSafeRefList(): List<ConeClassLikeType> =
    mapNotNullTo(ArrayList(size)) { it.coneTypeSafe() }
