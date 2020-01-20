/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.backend.common.serialization.DeclarationTableX
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
    lateinit var table: DeclarationTableX

    private inner class PublicIdSigBuilder : IdSignatureBuilder<IrDeclaration>(), IrElementVisitorVoid {

        override fun accept(d: IrDeclaration) {
            d.acceptVoid(this)
        }

        private fun collectFqNames(declaration: IrDeclarationWithName) {
            declaration.parent.acceptVoid(this)
            classFanSegments.add(declaration.name.asString())
        }

        override fun visitElement(element: IrElement) = error("Unexpected element ${element.render()}")

        override fun visitPackageFragment(declaration: IrPackageFragment) {
            packageFqn = declaration.fqName
        }

        override fun visitClass(declaration: IrClass) {
            collectFqNames(declaration)
            setExpected(declaration.isExpect)
        }

        override fun visitSimpleFunction(declaration: IrSimpleFunction) {
            val property = declaration.correspondingPropertySymbol
            if (property != null) {
                hash_id_acc = mangler.run { declaration.hashedMangle }
                property.owner.acceptVoid(this)
                classFanSegments.add(declaration.name.asString())
            } else {
                hash_id = mangler.run { declaration.hashedMangle }
                collectFqNames(declaration)
            }
            setExpected(declaration.isExpect)
        }

        override fun visitConstructor(declaration: IrConstructor) {
            hash_id = mangler.run { declaration.hashedMangle }
            collectFqNames(declaration)
            setExpected(declaration.isExpect)
        }

        override fun visitProperty(declaration: IrProperty) {
            hash_id = mangler.run { declaration.hashedMangle }
            collectFqNames(declaration)
            setExpected(declaration.isExpect)
        }

        override fun visitTypeAlias(declaration: IrTypeAlias) {
            collectFqNames(declaration)
        }

        override fun visitEnumEntry(declaration: IrEnumEntry) {
            collectFqNames(declaration)
        }
    }

    private val publicSignatureBuilder = PublicIdSigBuilder()

    private fun composeContainerIdSignature(container: IrDeclarationParent): IdSignature {
        if (container is IrPackageFragment) return IdSignature.PublicSignature(container.fqName, FqName.ROOT, null, 0)
        if (container is IrDeclaration) return table.uniqIdByDeclaration(container)
        error("Unexpected container ${container.render()}")
    }

    fun composePublicIdSignature(declaration: IrDeclaration): IdSignature {
        assert(mangler.run { declaration.isExported() })
        return publicSignatureBuilder.buildSignature(declaration)
    }

    fun composeFileLocalIdSignature(declaration: IrDeclaration): IdSignature {
        assert(!mangler.run { declaration.isExported() })

        return table.privateDeclarationSignature(declaration) {
            val container = when (declaration) {
                is IrField -> composeSignatureForDeclaration(declaration.correspondingPropertySymbol!!.owner)
                is IrSimpleFunction -> declaration.correspondingPropertySymbol?.let {
                    composeSignatureForDeclaration(it.owner)
                } ?: composeContainerIdSignature(declaration.parent)
                else -> composeContainerIdSignature(declaration.parent)
            }
            IdSignature.FileLocalSignature(container, ++localIndex)
        }
    }
}