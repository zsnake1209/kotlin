/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor

abstract class FirResolvedTypeRef(
    session: FirSession,
    psi: PsiElement?,
    val type: ConeKotlinType
) : FirTypeRef, FirAbstractElement(session, psi) {

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R =
        visitor.visitResolvedTypeRef(this, data)
}

class FirResolvedFunctionTypeRef(
    psi: PsiElement?,
    session: FirSession,
    override val isMarkedNullable: Boolean,
    override val annotations: MutableList<FirAnnotationCall>,
    override var receiverTypeRef: FirTypeRef?,
    override val valueParameters: MutableList<FirValueParameter>,
    override var returnTypeRef: FirTypeRef,
    type: ConeKotlinType
) : @VisitedSupertype FirResolvedTypeRef(session, psi, type), FirFunctionTypeRef {
    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R =
        visitor.visitResolvedFunctionTypeRef(this, data)

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        receiverTypeRef = receiverTypeRef?.transformSingle(transformer, data)
        returnTypeRef = returnTypeRef.transformSingle(transformer, data)
        valueParameters.transformInplace(transformer, data)

        return this
    }
}

inline fun <reified T : ConeKotlinType> FirTypeRef.coneTypeUnsafe() = (this as FirResolvedTypeRef).type as T
inline fun <reified T : ConeKotlinType> FirTypeRef.coneTypeSafe() = (this as? FirResolvedTypeRef)?.type as? T
