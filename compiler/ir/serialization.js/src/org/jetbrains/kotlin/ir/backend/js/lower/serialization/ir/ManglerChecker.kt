/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.serialization.ir

import org.jetbrains.kotlin.backend.common.serialization.mangle.*
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

class ManglerChecker : IrElementVisitorVoid {
    private val irExportChecker = IrExportCheckerVisitor()
    private val descirptorExportChecker = DescriptorExportCheckerVisitor()

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    private fun hashedMangleImplIr(declaration: IrDeclaration): String {
        val sb = StringBuilder(256) // this capacity in enough for JS stdlib which 99%% is 225 symbols
        declaration.accept(IrMangleVisitor(sb), true)

        mangleSizes.add(sb.length)

        return sb.toString()
    }

    private fun hashedMangleImplDesc(declaration: IrDeclaration): String {
        val sb = StringBuilder(256)
        declaration.descriptor.accept(DescriptorMangleVisitor(sb, descriptorPrefix(declaration)), true)

        return sb.toString()
    }

    private fun isExportedImplClassic(declaration: IrDeclaration): Boolean {
        if (declaration is IrValueDeclaration) return false
        if (declaration is IrAnonymousInitializer) return false
        if (declaration is IrLocalDelegatedProperty) return false
        return with(JsManglerForBE) { declaration.isExportedClassic() }
    }

    private fun hashedMangleImplClassic(declaration: IrDeclaration): String {
        return with(JsManglerForBE) { declaration.mangleString }
    }

    override fun visitDeclaration(declaration: IrDeclaration) {
        val e0 = isExportedImplClassic(declaration)
        val e1 = declaration.accept(irExportChecker, null)

        if (e0 != e1) {
            println("${declaration.render()}\n Classic: $e0\n Visitor: $e1\n")
            error("${declaration.render()}\n Classic: $e0\n Visitor: $e1\n")
        }

        val kind = SpecialDeclarationType.declarationToType(declaration)
        val e2 = declaration.descriptor.accept(descirptorExportChecker, kind)
        if (e1 != e2) {
            println("${declaration.render()}\n Visitor: $e1\n Descrip: $e2\n")
            error("${declaration.render()}\n Visitor: $e1\n Descrip: $e2\n")
        }
        if (!e1) return

        val m1 = hashedMangleImplClassic(declaration)
        val m2 = hashedMangleImplIr(declaration)
        val m3 = hashedMangleImplDesc(declaration)

        if (m1 != m2) {
            println("Classic: $m1\nVisitor: $m2\n")
            error("Classic: $m1\nVisitor: $m2\n")
        }

        if (m1 != m3) {
            println("Classic: $m1\nDescrip: $m3\n")
            error("Classic: $m1\nDescrip: $m3\n")
        }

        declaration.acceptChildrenVoid(this)
    }
}