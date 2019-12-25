/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.mangle.classic

import org.jetbrains.kotlin.backend.common.serialization.mangle.KotlinExportChecker
import org.jetbrains.kotlin.backend.common.serialization.mangle.SpecialDeclarationType
import org.jetbrains.kotlin.backend.common.serialization.mangle.publishedApiAnnotation
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.util.constructedClass
import org.jetbrains.kotlin.ir.util.isAnonymousObject

abstract class ClassicExportChecker :
    KotlinExportChecker<IrDeclaration> {
    override fun check(declaration: IrDeclaration, type: SpecialDeclarationType): Boolean {
        return isExportedImplClasssic(declaration)
    }

    override fun IrDeclaration.isPlatformSpecificExported(): Boolean = false

    private fun isExportedImplClasssic(declaration: IrDeclaration): Boolean {
        // TODO: revise
        if (declaration is IrValueDeclaration) return false
        if (declaration is IrAnonymousInitializer) return false
        if (declaration is IrLocalDelegatedProperty) return false

        val descriptorAnnotations = declaration.descriptor.annotations

        if (declaration.isPlatformSpecificExported()) return true

        if (declaration is IrTypeAlias && declaration.parent is IrPackageFragment) {
            return true
        }

        if (descriptorAnnotations.hasAnnotation(publishedApiAnnotation)) {
            return true
        }

        if (declaration.isAnonymousObject)
            return false

        if (declaration is IrConstructor && declaration.constructedClass.kind.isSingleton) {
            // Currently code generator can access the constructor of the singleton,
            // so ignore visibility of the constructor itself.
            return isExportedImplClasssic(declaration.constructedClass)
        }

        if (declaration is IrFunction) {
            val descriptor = declaration.descriptor
            // TODO: this code is required because accessor doesn't have a reference to property.
            if (descriptor is PropertyAccessorDescriptor) {
                val property = descriptor.correspondingProperty
                if (property.annotations.hasAnnotation(publishedApiAnnotation)) return true
            }
        }

        val visibility = when (declaration) {
            is IrClass -> declaration.visibility
            is IrFunction -> declaration.visibility
            is IrProperty -> declaration.visibility
            is IrField -> declaration.visibility
            is IrTypeAlias -> declaration.visibility
            else -> null
        }

        /**
         * note: about INTERNAL - with support of friend modules we let frontend to deal with internal declarations.
         */
        if (visibility != null && !visibility.isPublicAPI && visibility != Visibilities.INTERNAL) {
            // If the declaration is explicitly marked as non-public,
            // then it must not be accessible from other modules.
            return false
        }

        val parent = declaration.parent
        if (parent !is org.jetbrains.kotlin.ir.declarations.IrDeclaration) {
            return true
        }

        return isExportedImplClasssic(parent)
    }
}