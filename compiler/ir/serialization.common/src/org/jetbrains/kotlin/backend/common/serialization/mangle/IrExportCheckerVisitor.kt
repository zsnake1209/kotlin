/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle

import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.SpecialNames

class IrExportCheckerVisitor : IrElementVisitor<Boolean, Nothing?> {

    private val publishedApiAnnotation = FqName("kotlin.PublishedApi")

    private fun IrDeclaration.isExported(annotations: List<IrConstructorCall>, visibility: Visibility?): Boolean {
        if (annotations.hasAnnotation(publishedApiAnnotation)) return true
        if (visibility != null && !visibility.isPubliclyVisible()) return false

        return parent.accept(this@IrExportCheckerVisitor, null)
    }

    private fun Visibility.isPubliclyVisible(): Boolean = isPublicAPI || this === Visibilities.INTERNAL

    override fun visitElement(element: IrElement, data: Nothing?): Boolean = error("Should bot reach here ${element.render()}")

    override fun visitDeclaration(declaration: IrDeclaration, data: Nothing?) = declaration.run { isExported(annotations, null) }

    override fun visitValueParameter(declaration: IrValueParameter, data: Nothing?) = false
    override fun visitVariable(declaration: IrVariable, data: Nothing?) = false

    override fun visitField(declaration: IrField, data: Nothing?): Boolean {
        val annotations = declaration.run { correspondingPropertySymbol?.owner?.annotations ?: annotations }
        val r = declaration.run { isExported(annotations, visibility) }
        if (r) {
            1
        }
        return r
    }

    override fun visitProperty(declaration: IrProperty, data: Nothing?): Boolean {
        return declaration.run { isExported(annotations, visibility) }
    }

    override fun visitPackageFragment(declaration: IrPackageFragment, data: Nothing?): Boolean = true

    override fun visitTypeAlias(declaration: IrTypeAlias, data: Nothing?): Boolean =
        if (declaration.parent is IrPackageFragment) true
        else declaration.run { isExported(annotations, visibility) }

    override fun visitClass(declaration: IrClass, data: Nothing?): Boolean {
        if (declaration.name == SpecialNames.NO_NAME_PROVIDED) return false
        return declaration.run { isExported(annotations, visibility) }
    }

    override fun visitConstructor(declaration: IrConstructor, data: Nothing?): Boolean {
        val klass = declaration.constructedClass
        return if (klass.kind.isSingleton) klass.accept(this, null) else declaration.run { isExported(annotations, visibility) }
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction, data: Nothing?): Boolean {
        val annotations = declaration.run { correspondingPropertySymbol?.owner?.annotations ?: annotations }
        return declaration.run { isExported(annotations, visibility) }
    }
}
