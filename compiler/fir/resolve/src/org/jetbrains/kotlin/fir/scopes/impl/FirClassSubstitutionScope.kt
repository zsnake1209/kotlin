/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.scopes.impl

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.impl.FirDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirMemberFunctionImpl
import org.jetbrains.kotlin.fir.declarations.impl.FirValueParameterImpl
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutorByMap
import org.jetbrains.kotlin.fir.resolve.transformers.ReturnTypeCalculatorWithJump
import org.jetbrains.kotlin.fir.resolve.transformers.firUnsafe
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.symbols.*
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.FirResolvedTypeRefImpl
import org.jetbrains.kotlin.name.Name

class FirClassSubstitutionScope(
    private val session: FirSession,
    private val useSiteScope: FirScope,
    substitution: Map<ConeTypeParameterSymbol, ConeKotlinType>
) : FirScope {

    private val fakeOverrides = mutableMapOf<ConeCallableSymbol, ConeCallableSymbol>()

    private val substitutor = ConeSubstitutorByMap(substitution)

    override fun processFunctionsByName(name: Name, processor: (ConeFunctionSymbol) -> ProcessorAction): ProcessorAction {
        useSiteScope.processFunctionsByName(name) process@{ original ->

            val function = fakeOverrides.getOrPut(original) { createFakeOverride(original, name) }
            processor(function as ConeFunctionSymbol)
        }


        return super.processFunctionsByName(name, processor)
    }

    override fun processPropertiesByName(name: Name, processor: (ConeVariableSymbol) -> ProcessorAction): ProcessorAction {
        return useSiteScope.processPropertiesByName(name, processor)
    }

    private val typeCalculator by lazy { ReturnTypeCalculatorWithJump(session) }

    private fun ConeKotlinType.substitute(): ConeKotlinType? {
        return substitutor.substituteOrNull(this)
    }

    private fun createFakeOverride(
        original: ConeFunctionSymbol,
        name: Name
    ): FirFunctionSymbol {

        val member = original.firUnsafe<FirFunction>()
        if (member is FirConstructor) return original as FirFunctionSymbol // TODO: substitution for constructors
        member as FirNamedFunction

        val receiverType = member.receiverTypeRef?.coneTypeUnsafe()
        val newReceiverType = receiverType?.substitute()

        val returnType = typeCalculator.tryCalculateReturnType(member).type
        val newReturnType = returnType.substitute()

        val newParameterTypes = member.valueParameters.map {
            it.returnTypeRef.coneTypeUnsafe().substitute()
        }

        val symbol = FirFunctionSymbol(original.callableId, true)
        with(member) {
            // TODO: consider using here some light-weight functions instead of pseudo-real FirMemberFunctionImpl
            // As second alternative, we can invent some light-weight kind of FirRegularClass
            FirMemberFunctionImpl(
                this@FirClassSubstitutionScope.session,
                psi,
                symbol,
                name,
                member.receiverTypeRef?.withReplacedConeType(this@FirClassSubstitutionScope.session, newReceiverType),
                member.returnTypeRef.withReplacedConeType(this@FirClassSubstitutionScope.session, newReturnType)
            ).apply {
                status = member.status as FirDeclarationStatusImpl
                valueParameters += member.valueParameters.zip(newParameterTypes) { valueParameter, newType ->
                    with(valueParameter) {
                        FirValueParameterImpl(
                            this@FirClassSubstitutionScope.session, psi,
                            name, this.returnTypeRef.withReplacedConeType(this@FirClassSubstitutionScope.session, newType),
                            defaultValue, isCrossinline, isNoinline, isVararg,
                            FirVariableSymbol(valueParameter.symbol.callableId)
                        )
                    }
                }
            }
        }
        return symbol
    }
}


fun FirTypeRef.withReplacedConeType(session: FirSession, newType: ConeKotlinType?): FirResolvedTypeRef {
    require(this is FirResolvedTypeRef)
    if (newType == null) return this

    return FirResolvedTypeRefImpl(
        session, psi, newType,
        isMarkedNullable,
        annotations
    )

}
