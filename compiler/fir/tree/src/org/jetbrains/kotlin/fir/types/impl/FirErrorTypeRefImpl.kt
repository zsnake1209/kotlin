/*
 * Copyright 2000-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.types.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.transformInplace
import org.jetbrains.kotlin.fir.types.ConeKotlinErrorType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRefWithNullability
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor

class FirErrorTypeRefImpl(
    override val session: FirSession,
    override val psi: PsiElement?,
    override val reason: String
) : FirTypeRefWithNullability, FirErrorTypeRef() {
    override val type: ConeKotlinType = ConeKotlinErrorType(reason)

    override val isMarkedNullable: Boolean
        get() = false

    override val annotations = mutableListOf<FirAnnotationCall>()

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R {
        return super<FirErrorTypeRef>.accept(visitor, data)
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        annotations.transformInplace(transformer, data)

        return this
    }
}
