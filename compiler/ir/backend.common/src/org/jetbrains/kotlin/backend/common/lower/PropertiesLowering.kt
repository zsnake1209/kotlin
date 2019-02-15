/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.lower

import org.jetbrains.kotlin.backend.common.BackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.makeIrFilePhase
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockImpl
import org.jetbrains.kotlin.ir.util.transformDeclarationsFlat
import org.jetbrains.kotlin.ir.visitors.*
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

val propertiesPhase = makeIrFilePhase(
    ::PropertiesLowering,
    name = "Properties",
    description = "Move fields and accessors for properties to their classes",
    stickyPostconditions = setOf(::checkNoProperties)
)

fun checkNoProperties(irFile: IrFile) {
    irFile.acceptVoid(object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
            element.acceptChildrenVoid(this)
        }

        override fun visitProperty(declaration: IrProperty) {
            error("No properties should remain at this stage")
        }
    })
}

class PropertiesLowering() : IrElementTransformerVoid(), FileLoweringPass {
    constructor(@Suppress("UNUSED_PARAMETER") context: BackendContext) : this()

    override fun lower(irFile: IrFile) {
        irFile.accept(this, null)
    }

    override fun visitFile(declaration: IrFile): IrFile {
        declaration.transformChildrenVoid(this)
        declaration.transformDeclarationsFlat { lowerProperty(it, ClassKind.CLASS) }
        return declaration
    }

    override fun visitClass(declaration: IrClass): IrStatement {
        declaration.transformChildrenVoid(this)
        declaration.transformDeclarationsFlat { lowerProperty(it, declaration.kind) }
        declaration.declarations.removeAll { it is IrProperty }
        return declaration
    }

    private fun lowerProperty(declaration: IrDeclaration, kind: ClassKind): List<IrDeclaration>? =
        if (declaration is IrProperty)
            ArrayList<IrDeclaration>(3).apply {
                // JvmFields in a companion object refer to companion's owners and should not be generated within companion.
                if (kind != ClassKind.ANNOTATION_CLASS && declaration.backingField?.parent == declaration.parent) {
                    addIfNotNull(declaration.backingField)
                }
                addIfNotNull(declaration.getter)
                addIfNotNull(declaration.setter)
            }
        else
            null
}

class LocalDelegatedPropertiesLowering : IrElementTransformerVoid(), FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.accept(this, null)
    }

    override fun visitLocalDelegatedProperty(declaration: IrLocalDelegatedProperty): IrStatement {
        declaration.transformChildrenVoid(this)

        val initializer = declaration.delegate.initializer!!
        declaration.delegate.initializer = IrBlockImpl(
            initializer.startOffset, initializer.endOffset, initializer.type, null,
            listOfNotNull(
                declaration.getter,
                declaration.setter,
                initializer
            )
        )

        return declaration.delegate
    }
}
