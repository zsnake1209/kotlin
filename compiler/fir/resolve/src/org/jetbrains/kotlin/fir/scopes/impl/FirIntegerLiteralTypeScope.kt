/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirSimpleFunctionImpl
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.resolve.scopeSessionKey
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.symbols.AbstractFirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.CallableId
import org.jetbrains.kotlin.fir.symbols.ConeClassifierLookupTag
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.ConeIntegerLiteralType
import org.jetbrains.kotlin.fir.types.ConeIntegerLiteralTypeImpl
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

private object FirIntegerLiteralTypeClassifierSymbol : FirClassifierSymbol<FirIntegerLiteralTypeClassifier>() {
    override fun toLookupTag(): ConeClassifierLookupTag {
        throw IllegalStateException("Should not be called")
    }
}

private object FirIntegerLiteralTypeClassifier : FirDeclaration, FirSymbolOwner<FirIntegerLiteralTypeClassifier> {
    override val symbol: AbstractFirBasedSymbol<FirIntegerLiteralTypeClassifier>
        get() = FirIntegerLiteralTypeClassifierSymbol

    override val source: FirSourceElement? get() = throw IllegalStateException("Should not be called")
    override val session: FirSession get() = throw IllegalStateException("Should not be called")
    override val resolvePhase: FirResolvePhase get() = throw IllegalStateException("Should not be called")

    override fun <R, D> accept(visitor: FirVisitor<R, D>, data: D): R {
        throw IllegalStateException("Should not be called")
    }

    override fun replaceResolvePhase(newResolvePhase: FirResolvePhase) {
        throw IllegalStateException("Should not be called")
    }

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {
        throw IllegalStateException("Should not be called")
    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        throw IllegalStateException("Should not be called")
    }
}

class FirIntegerLiteralTypeScope(private val session: FirSession) : FirScope() {
    companion object {
        val BINARY_OPERATOR_NAMES = FirIntegerOperator.Kind.values().filterNot { it.unary }.map { it.operatorName }
        val UNARY_OPERATOR_NAMES = FirIntegerOperator.Kind.values().filter { it.unary }.map { it.operatorName }
        private val ALL_OPERATORS = FirIntegerOperator.Kind.values().map { it.operatorName to it }.toMap()

        val ILT_SYMBOL: FirClassifierSymbol<*> = FirIntegerLiteralTypeClassifierSymbol
        val SCOPE_SESSION_KEY = scopeSessionKey<FirClassifierSymbol<*>, FirIntegerLiteralTypeScope>()
    }

    private val BINARY_OPERATOR_SYMBOLS = BINARY_OPERATOR_NAMES.map { name ->
        name to FirNamedFunctionSymbol(CallableId(name)).apply {
            createFirFunction(name, this).apply {
                val valueParameterName = Name.identifier("arg")
                valueParameters += buildValueParameter {
                    source = null
                    session = this@FirIntegerLiteralTypeScope.session
                    returnTypeRef = FirILTTypeRefPlaceHolder()
                    this.name = valueParameterName
                    symbol = FirVariableSymbol(valueParameterName)
                    defaultValue = null
                    isCrossinline = false
                    isNoinline = false
                    isVararg = false
                }
            }
        }
    }.toMap()

    private val UNARY_OPERATOR_SYMBOLS = UNARY_OPERATOR_NAMES.map { name ->
        name to FirNamedFunctionSymbol(CallableId(name)).apply { createFirFunction(name, this) }
    }.toMap()

    @UseExperimental(FirImplementationDetail::class)
    private fun createFirFunction(name: Name, symbol: FirNamedFunctionSymbol): FirSimpleFunctionImpl {
        val kind = ALL_OPERATORS.getValue(name)
        return FirIntegerOperator(
            source = null,
            session,
            FirILTTypeRefPlaceHolder(),
            receiverTypeRef = null,
            kind,
            FirResolvedDeclarationStatusImpl(Visibilities.PUBLIC, Modality.FINAL).apply {
                isOperator = kind.isOperator
                isInfix = kind.isInfix
            },
            symbol
        ).apply {
            resolvePhase = FirResolvePhase.BODY_RESOLVE
        }
    }

    override fun processClassifiersByName(name: Name, processor: (FirClassifierSymbol<*>) -> Unit) {

    }

    override fun processFunctionsByName(name: Name, processor: (FirFunctionSymbol<*>) -> Unit) {
        val symbol = BINARY_OPERATOR_SYMBOLS[name]
            ?: UNARY_OPERATOR_SYMBOLS[name]
            ?: return
        processor(symbol)
    }

    override fun processPropertiesByName(name: Name, processor: (FirVariableSymbol<*>) -> Unit) {
    }
}

@UseExperimental(FirImplementationDetail::class)
class FirIntegerOperator @FirImplementationDetail constructor(
    source: FirSourceElement?,
    session: FirSession,
    returnTypeRef: FirTypeRef,
    receiverTypeRef: FirTypeRef?,
    val kind: Kind,
    status: FirDeclarationStatus,
    symbol: FirFunctionSymbol<FirSimpleFunction>
) : FirSimpleFunctionImpl(
    source,
    session,
    resolvePhase = FirResolvePhase.BODY_RESOLVE,
    returnTypeRef,
    receiverTypeRef,
    typeParameters = mutableListOf(),
    valueParameters = mutableListOf(),
    body = null,
    status,
    containerSource = null,
    kind.operatorName,
    symbol,
    annotations = mutableListOf(),
) {
    enum class Kind(val unary: Boolean, val operatorName: Name, val isOperator: Boolean, val isInfix: Boolean) {
        PLUS(unary = false, OperatorNameConventions.PLUS, isOperator = true, isInfix = false),
        MINUS(unary = false, OperatorNameConventions.MINUS, isOperator = true, isInfix = false),
        TIMES(unary = false, OperatorNameConventions.TIMES, isOperator = true, isInfix = false),
        DIV(unary = false, OperatorNameConventions.DIV, isOperator = true, isInfix = false),
        REM(unary = false, OperatorNameConventions.REM, isOperator = true, isInfix = false),
        SHL(unary = false, Name.identifier("shl"), isOperator = false, isInfix = true),
        SHR(unary = false, Name.identifier("shr"), isOperator = false, isInfix = true),
        USHR(unary = false, Name.identifier("ushr"), isOperator = false, isInfix = true),
        XOR(unary = false, Name.identifier("xor"), isOperator = false, isInfix = true),
        AND(unary = false, Name.identifier("and"), isOperator = false, isInfix = true),
        OR(unary = false, Name.identifier("or"), isOperator = false, isInfix = true),
        UNARY_PLUS(unary = true, OperatorNameConventions.UNARY_PLUS, isOperator = true, isInfix = false),
        UNARY_MINUS(unary = true, OperatorNameConventions.UNARY_MINUS, isOperator = true, isInfix = false),
        INV(unary = true, Name.identifier("inv"), isOperator = false, isInfix = false)
    }
}

class FirILTTypeRefPlaceHolder : FirResolvedTypeRef() {
    override val source: FirSourceElement? get() = null
    override val annotations: List<FirAnnotationCall> get() = emptyList()
    override var type: ConeIntegerLiteralType = ConeIntegerLiteralTypeImpl(0)
    override val delegatedTypeRef: FirTypeRef? get() = null

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {}

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        return this
    }
}

