/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.resolve.*
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.fir.scopes.createImportingScopes
import org.jetbrains.kotlin.fir.scopes.impl.FirMemberTypeParameterScope
import org.jetbrains.kotlin.fir.scopes.impl.nestedClassifierScope
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassifierSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.FirErrorTypeRefImpl
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.fir.visitors.compose
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addIfNotNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class FirNewSupertypeResolverTransformerAdapter : FirTransformer<Nothing?>() {
    private val supertypeComputationSession = SupertypeComputationSession()
    private val scopeSession = ScopeSession()
    private val applySupertypesTransformer = FirApplySupertypesTransformer(supertypeComputationSession)


    override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
        return element.compose()
    }

    override fun transformFile(file: FirFile, data: Nothing?): CompositeTransformResult<FirFile> {
        val supertypeResolverVisitor = FirNewSupertypeResolverVisitor(file.session, supertypeComputationSession, scopeSession)
        file.accept(supertypeResolverVisitor)
        return file.transform(applySupertypesTransformer, null)
    }

}

class FirApplySupertypesTransformer(
    private val supertypeComputationSession: SupertypeComputationSession
) : FirTransformer<Nothing?>() {
    override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
        return element.compose()
    }

    override fun transformFile(file: FirFile, data: Nothing?): CompositeTransformResult<FirDeclaration> {
        return (file.transformChildren(this, null) as FirFile).compose()
    }

    override fun transformRegularClass(regularClass: FirRegularClass, data: Nothing?): CompositeTransformResult<FirStatement> {
        val supertypeRefs = getResolvedSupertypeRefs(regularClass)

        // TODO: Replace with an immutable version or transformer
        regularClass.replaceSuperTypeRefs(supertypeRefs)
        regularClass.replaceResolvePhase(FirResolvePhase.SUPER_TYPES)

        return (regularClass.transformChildren(this, null) as FirRegularClass).compose()
    }

    override fun transformSealedClass(sealedClass: FirSealedClass, data: Nothing?): CompositeTransformResult<FirStatement> {
        return transformRegularClass(sealedClass, data)
    }

    override fun transformEnumEntry(enumEntry: FirEnumEntry, data: Nothing?): CompositeTransformResult<FirStatement> {
        return transformRegularClass(enumEntry, data)
    }

    private fun getResolvedSupertypeRefs(classLikeDeclaration: FirClassLikeDeclaration<*>): List<FirTypeRef> {
        val status = supertypeComputationSession.getSupertypesComputationStatus(classLikeDeclaration)
        require(status is SupertypeComputationStatus.Computed) {
            "Unexpected status at FirApplySupertypesTransformer: $status for ${classLikeDeclaration.symbol.classId}"
        }
        return status.supertypeRefs
    }

    override fun transformTypeAlias(typeAlias: FirTypeAlias, data: Nothing?): CompositeTransformResult<FirDeclaration> {
        val supertypeRefs = getResolvedSupertypeRefs(typeAlias)

        assert(supertypeRefs.size == 1) {
            "Expected single supertypeRefs, but found ${supertypeRefs.size} in ${typeAlias.symbol.classId}"
        }

        // TODO: Replace with an immutable version or transformer
        typeAlias.replaceExpandedTypeRef(supertypeRefs[0])
        typeAlias.replaceResolvePhase(FirResolvePhase.SUPER_TYPES)

        return typeAlias.compose()
    }
}

private fun FirMemberDeclaration.typeParametersScope(): FirScope? {
    if (typeParameters.isEmpty()) return null
    return FirMemberTypeParameterScope(this)
}

private fun createScopesForNestedClasses(
    regularClass: FirRegularClass,
    session: FirSession,
    supertypeComputationSession: SupertypeComputationSession
): Collection<FirScope> =
    mutableListOf<FirScope>().apply {
        lookupSuperTypes(
            regularClass,
            lookupInterfaces = false, deep = true, useSiteSession = session,
            supertypeSupplier = supertypeComputationSession.supertypesSupplier
        ).asReversed().mapTo(this) {
                nestedClassifierScope(it.lookupTag.classId, session)
        }
        addIfNotNull(regularClass.typeParametersScope())
        val companionObjects = regularClass.declarations.filterIsInstance<FirRegularClass>().filter { it.isCompanion }
        for (companionObject in companionObjects) {
            add(nestedClassifierScope(companionObject))
        }
        add(nestedClassifierScope(regularClass))
    }

class FirNewSupertypeResolverVisitor(
    private val session: FirSession,
    private val supertypeComputationSession: SupertypeComputationSession,
    private val scopeSession: ScopeSession
) : FirVisitorVoid() {
    override fun visitElement(element: FirElement) {}

    private fun prepareFileScope(file: FirFile): FirImmutableCompositeScope {
        return supertypeComputationSession.getOrPutFileScope(file) {
            FirImmutableCompositeScope(ImmutableList.ofAll(createImportingScopes(file, session, scopeSession).asReversed()))
        }
    }

    private fun prepareScopeForNestedClasses(regularClass: FirRegularClass): FirImmutableCompositeScope {
        return supertypeComputationSession.getOrPutScopeForNestedClasses(regularClass) {
            val scope = prepareScope(regularClass)

            resolveAllSupertypes(regularClass, regularClass.superTypeRefs)

            scope.childScope(createScopesForNestedClasses(regularClass, session, supertypeComputationSession))
        }
    }

    private fun resolveAllSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration<*>,
        supertypeRefs: List<FirTypeRef>
    ) {
        val supertypes =
            resolveSpecificClassLikeSupertypes(classLikeDeclaration, supertypeRefs)
                .mapNotNull { (it as? FirResolvedTypeRef)?.type }

        for (supertype in supertypes) {
            if (supertype !is ConeClassLikeType) continue
            val fir = session.firProvider.getFirClassifierByFqName(supertype.lookupTag.classId) ?: continue
            resolveAllSupertypes(fir, fir.supertypeRefs())
        }
    }

    private fun FirClassLikeDeclaration<*>.supertypeRefs() = when (this) {
        is FirRegularClass -> superTypeRefs
        is FirTypeAlias -> listOf(expandedTypeRef)
        else -> emptyList()
    }

    private fun prepareScope(classLikeDeclaration: FirClassLikeDeclaration<*>): FirImmutableCompositeScope {
        val classId = classLikeDeclaration.symbol.classId
        val outerClassFir = classId.outerClassId?.let(session.firProvider::getFirClassifierByFqName) as? FirRegularClass

        val result = if (outerClassFir == null) {
            prepareFileScope(session.firProvider.getFirClassifierContainerFile(classId))
        } else {
            prepareScopeForNestedClasses(outerClassFir)
        }

        return result.childScope(classLikeDeclaration.typeParametersScope())
    }

    private fun resolveSpecificClassLikeSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration<*>,
        resolveSuperTypeRefs: (FirTransformer<Nothing?>) -> List<FirTypeRef>
    ): List<FirTypeRef> {
        when (val status = supertypeComputationSession.getSupertypesComputationStatus(classLikeDeclaration)) {
            is SupertypeComputationStatus.Computed -> return status.supertypeRefs
            is SupertypeComputationStatus.Computing -> return listOf(
                FirErrorTypeRefImpl(classLikeDeclaration.psi, "Loop in supertype definition for ${classLikeDeclaration.symbol.classId}")
            )
        }

        supertypeComputationSession.startComputingSupertypes(classLikeDeclaration)
        val scope = prepareScope(classLikeDeclaration)

        val transformer = FirSpecificTypeResolverTransformer(scope, session)
        val resolvedTypesRefs = resolveSuperTypeRefs(transformer)

        supertypeComputationSession.storeSupertypes(classLikeDeclaration, resolvedTypesRefs)
        return resolvedTypesRefs
    }

    override fun visitRegularClass(regularClass: FirRegularClass) {
        resolveSpecificClassLikeSupertypes(regularClass, regularClass.superTypeRefs)
        regularClass.acceptChildren(this)
    }

    override fun visitSealedClass(sealedClass: FirSealedClass) {
        visitRegularClass(sealedClass)
    }

    override fun visitEnumEntry(enumEntry: FirEnumEntry) {
        visitRegularClass(enumEntry)
    }

    fun resolveSpecificClassLikeSupertypes(
        classLikeDeclaration: FirClassLikeDeclaration<*>,
        supertypeRefs: List<FirTypeRef>
    ): List<FirTypeRef> {
        return resolveSpecificClassLikeSupertypes(classLikeDeclaration) { transformer ->
            supertypeRefs.map {
                val superTypeRef = transformer.transformTypeRef(it, null).single

                if (superTypeRef.coneTypeSafe<ConeTypeParameterType>() != null)
                    FirErrorTypeRefImpl(
                        superTypeRef.psi,
                        "Type parameter cannot be a super-type: ${superTypeRef.coneTypeUnsafe<ConeTypeParameterType>().render()}"
                    )
                else
                    superTypeRef
            }
        }
    }

    override fun visitTypeAlias(typeAlias: FirTypeAlias) {
        resolveSpecificClassLikeSupertypes(typeAlias) { transformer ->
            val resolvedTypeRef =
                transformer.transformTypeRef(typeAlias.expandedTypeRef, null).single as? FirResolvedTypeRef
                    ?: return@resolveSpecificClassLikeSupertypes listOf(
                        FirErrorTypeRefImpl(
                            typeAlias.expandedTypeRef.psi,
                            "Unresolved expanded typeRef for ${typeAlias.symbol.classId}"
                        )
                    )

            val type = resolvedTypeRef.type
            if (type is ConeAbbreviatedType) {
                val expansionTypeAlias = type.abbreviationLookupTag.toSymbol(session)?.safeAs<FirTypeAliasSymbol>()?.fir
                if (expansionTypeAlias != null) {
                    visitTypeAlias(expansionTypeAlias)
                }
            }

            listOf(resolvedTypeRef)
        }
    }

    override fun visitFile(file: FirFile) {
        file.acceptChildren(this)
    }
}

class SupertypeComputationSession {
    private val fileScopesMap = hashMapOf<FirFile, FirImmutableCompositeScope>() 
    private val scopesForNestedClassesMap = hashMapOf<FirRegularClass, FirImmutableCompositeScope>() 
    private val supertypeStatusMap = hashMapOf<FirClassLikeDeclaration<*>, SupertypeComputationStatus>()

    val supertypesSupplier = object : SupertypeSupplier {
        override fun forClass(regularClass: FirRegularClass): List<ConeClassLikeType> {
            if (regularClass.resolvePhase > FirResolvePhase.SUPER_TYPES) return regularClass.superConeTypes
            return (getSupertypesComputationStatus(regularClass) as? SupertypeComputationStatus.Computed)?.supertypeRefs?.mapNotNull {
                it.coneTypeSafe<ConeClassLikeType>()
            }.orEmpty()
        }

        override fun expansionForTypeAlias(typeAlias: FirTypeAlias): ConeClassLikeType? {
            if (typeAlias.resolvePhase > FirResolvePhase.SUPER_TYPES) return typeAlias.expandedConeType
            return (getSupertypesComputationStatus(typeAlias) as? SupertypeComputationStatus.Computed)
                ?.supertypeRefs
                ?.getOrNull(0)?.coneTypeSafe()
        }
    }
    
    fun getSupertypesComputationStatus(classLikeDeclaration: FirClassLikeDeclaration<*>): SupertypeComputationStatus =
        supertypeStatusMap[classLikeDeclaration] ?: SupertypeComputationStatus.NotComputed

    fun getOrPutFileScope(file: FirFile, scope: () -> FirImmutableCompositeScope): FirImmutableCompositeScope =
        fileScopesMap.getOrPut(file) { scope() }

    fun getOrPutScopeForNestedClasses(regularClass: FirRegularClass, scope: () -> FirImmutableCompositeScope): FirImmutableCompositeScope =
        scopesForNestedClassesMap.getOrPut(regularClass) { scope() }

    fun startComputingSupertypes(classLikeDeclaration: FirClassLikeDeclaration<*>) {
        require(supertypeStatusMap[classLikeDeclaration] == null) {
            "Unexpected in startComputingSupertypes supertype status for $classLikeDeclaration: ${supertypeStatusMap[classLikeDeclaration]}"
        }

        supertypeStatusMap[classLikeDeclaration] = SupertypeComputationStatus.Computing
    }
    fun storeSupertypes(classLikeDeclaration: FirClassLikeDeclaration<*>, resolvedTypesRefs: List<FirTypeRef>) {
        require(supertypeStatusMap[classLikeDeclaration] is SupertypeComputationStatus.Computing) {
            "Unexpected in storeSupertypes supertype status for $classLikeDeclaration: ${supertypeStatusMap[classLikeDeclaration]}"
        }

        supertypeStatusMap[classLikeDeclaration] = SupertypeComputationStatus.Computed(resolvedTypesRefs)
    }


}

sealed class SupertypeComputationStatus {
    object NotComputed : SupertypeComputationStatus()
    object Computing : SupertypeComputationStatus()

    class Computed(val supertypeRefs: List<FirTypeRef>) : SupertypeComputationStatus()
}

typealias ImmutableList<E> = javaslang.collection.List<E>

class FirImmutableCompositeScope(
    private val scopes: ImmutableList<FirScope>
) : FirScope() {

    fun childScope(newScope: FirScope?) = newScope?.let { FirImmutableCompositeScope(scopes.push(newScope)) } ?: this
    fun childScope(newScopes: Collection<FirScope>) = FirImmutableCompositeScope(scopes.pushAll(newScopes))

    override fun processClassifiersByName(
        name: Name,
        processor: (FirClassifierSymbol<*>) -> ProcessorAction
    ): ProcessorAction {
        for (scope in scopes) {
            if (!scope.processClassifiersByName(name, processor)) {
                return ProcessorAction.STOP
            }
        }
        return ProcessorAction.NEXT
    }

    private inline fun <T> processComposite(
        process: FirScope.(Name, (T) -> ProcessorAction) -> ProcessorAction,
        name: Name,
        noinline processor: (T) -> ProcessorAction
    ): ProcessorAction {
        val unique = mutableSetOf<T>()
        for (scope in scopes) {
            if (!scope.process(name) {
                    if (unique.add(it)) {
                        processor(it)
                    } else {
                        ProcessorAction.NEXT
                    }
                }
            ) {
                return ProcessorAction.STOP
            }
        }
        return ProcessorAction.NEXT
    }

    override fun processFunctionsByName(name: Name, processor: (FirFunctionSymbol<*>) -> ProcessorAction): ProcessorAction {
        return processComposite(FirScope::processFunctionsByName, name, processor)
    }

    override fun processPropertiesByName(name: Name, processor: (FirCallableSymbol<*>) -> ProcessorAction): ProcessorAction {
        return processComposite(FirScope::processPropertiesByName, name, processor)
    }

}

