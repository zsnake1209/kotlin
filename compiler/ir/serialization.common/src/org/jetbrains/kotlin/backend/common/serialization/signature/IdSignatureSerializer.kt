/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.backend.common.serialization.DeclarationTable
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.KotlinMangler
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName

open class IdSignatureSerializer(val mangler: KotlinMangler, startIndex: Long) {
    fun composeSignatureForDeclaration(declaration: IrDeclaration): IdSignature {
        return if (mangler.run { declaration.isExported() }) {
            composePublicIdSignature(declaration)
        } else composeFileLocalIdSignature(declaration)
    }

    private var localIndex: Long = startIndex

    private inner class PublicIdSigBuilder : IrElementVisitorVoid {
        private var packageFqn = FqName.ROOT
        private val classFanSegments = mutableListOf<String>()
        private var hash_id: Long? = null
        private var mask = 0L
        private var tail = false

        private fun reset() {
            this.packageFqn = FqName.ROOT
            this.classFanSegments.clear()
            this.hash_id = null
            this.mask = 0L
            this.tail = false
        }

        private fun build() = IdSignature.PublicSignature(packageFqn, FqName.fromSegments(classFanSegments), hash_id, mask)

        override fun visitElement(element: IrElement) = error("Unexpected element ${element.render()}")

        override fun visitPackageFragment(declaration: IrPackageFragment) {
            packageFqn = declaration.fqName
        }

        override fun visitClass(declaration: IrClass) {
            declaration.parent.acceptVoid(this)
            classFanSegments.add(declaration.name.asString())
        }

        override fun visitFunction(declaration: IrFunction) {
            assert(!tail)
            tail = true
            hash_id = mangler.run { declaration.hashedMangle }
            declaration.parent.acceptVoid(this)
            classFanSegments.add(declaration.name.asString())
        }

        override fun visitProperty(declaration: IrProperty) {
            assert(!tail)
            tail = true
//            hash_id = mangler.run { declaration.name.asString().hashMangle }
            hash_id = mangler.run { declaration.hashedMangle }
            declaration.parent.acceptVoid(this)
            classFanSegments.add(declaration.name.asString())
        }

        override fun visitTypeAlias(declaration: IrTypeAlias) {
            assert(!tail)
            tail = true
//            hash_id = mangler.run { declaration.name.asString().hashMangle }
//            hash_id = mangler.run { declaration.hashedMangle }
            declaration.parent.acceptVoid(this)
            classFanSegments.add(declaration.name.asString())
        }

        override fun visitEnumEntry(declaration: IrEnumEntry) {
            assert(!tail)
            tail = true
//            hash_id = mangler.run { declaration.name.asString().hashMangle }
//            hash_id = mangler.run { declaration.hashedMangle }
            declaration.parent.acceptVoid(this)
            classFanSegments.add(declaration.name.asString())
        }

        fun buildSignature(declaration: IrDeclaration): IdSignature.PublicSignature {
            reset()

            declaration.acceptVoid(this)

            return build()
        }
    }

    private val publicSignatureBuilder = PublicIdSigBuilder()

    private fun composeContainerIdSignature(container: IrDeclarationParent): IdSignature {
        if (container is IrPackageFragment) return IdSignature.PublicSignature(container.fqName, FqName.ROOT, null, 0)
        if (container is IrDeclaration) return composeSignatureForDeclaration(container)
        error("Unexpected container ${container.render()}")
    }

    fun composePublicIdSignature(declaration: IrDeclaration): IdSignature.PublicSignature {
        assert(mangler.run { declaration.isExported() })
        return publicSignatureBuilder.buildSignature(declaration)
    }

    fun composeFileLocalIdSignature(declaration: IrDeclaration): IdSignature.FileLocalSignature {
        assert(!mangler.run { declaration.isExported() })
        return IdSignature.FileLocalSignature(composeContainerIdSignature(declaration.parent), ++localIndex)
    }
}