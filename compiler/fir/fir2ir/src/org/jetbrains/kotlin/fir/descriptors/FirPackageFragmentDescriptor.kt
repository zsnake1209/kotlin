/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.descriptors

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationAttributes
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.scopes.MemberScope

open class FirPackageFragmentDescriptor(
    override val fqName: FqName,
    val moduleDescriptor: ModuleDescriptor,
    override val session: FirSession
) : FirDeclaration, PackageFragmentDescriptor {
    override val source: FirSourceElement?
        get() = null

    override val resolvePhase: FirResolvePhase
        get() = FirResolvePhase.BODY_RESOLVE

    override val origin: FirDeclarationOrigin
        get() = FirDeclarationOrigin.Source

    override val attributes: FirDeclarationAttributes = FirDeclarationAttributes()

    override fun replaceResolvePhase(newResolvePhase: FirResolvePhase) {

    }

    override fun <R, D> acceptChildren(visitor: FirVisitor<R, D>, data: D) {

    }

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        return this
    }

    override fun getContainingDeclaration(): ModuleDescriptor {
        return moduleDescriptor
    }


    override fun getMemberScope(): MemberScope {
        return MemberScope.Empty
    }

    override fun getOriginal(): DeclarationDescriptorWithSource {
        return this
    }

    override fun getName(): Name {
        return fqName.shortName()
    }

    override fun <R : Any?, D : Any?> accept(visitor: DeclarationDescriptorVisitor<R, D>?, data: D): R {
        return visitor?.visitPackageFragmentDescriptor(this, data) as R
    }

    override fun getSource(): SourceElement {
        TODO("not implemented")
    }

    override fun acceptVoid(visitor: DeclarationDescriptorVisitor<Void, Void>?) {
        visitor?.visitPackageFragmentDescriptor(this, null)
    }

    override val annotations: Annotations
        get() = Annotations.EMPTY

}