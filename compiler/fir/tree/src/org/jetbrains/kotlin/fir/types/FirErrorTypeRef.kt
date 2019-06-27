/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor

@VisitedClass
class FirErrorTypeRef(
    session: FirSession,
    psi: PsiElement?,
    val reason: String
) : FirTypeRefWithNullability, @VisitedSupertype FirResolvedTypeRef(session, psi, ConeKotlinErrorType(reason)) {
    override val isMarkedNullable: Boolean
        get() = false

    override val annotations = mutableListOf<FirAnnotationCall>()

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        annotations.transformInplace(transformer, data)

        return this
    }

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R =
        visitor.visitErrorTypeRef(this, data)
}
