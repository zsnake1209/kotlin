/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.addChild
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.utils.getJsModule
import org.jetbrains.kotlin.ir.backend.js.utils.getJsQualifier
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrExternalPackageFragmentImpl
import org.jetbrains.kotlin.ir.symbols.IrExternalPackageFragmentSymbol
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isEffectivelyExternal
import org.jetbrains.kotlin.ir.util.transformFlat
import org.jetbrains.kotlin.name.FqName

private val BODILESS_BUILTIN_CLASSES = listOf(
    "kotlin.String",
    "kotlin.Nothing",
    "kotlin.Array",
    "kotlin.Any",
    "kotlin.ByteArray",
    "kotlin.CharArray",
    "kotlin.ShortArray",
    "kotlin.IntArray",
    "kotlin.LongArray",
    "kotlin.FloatArray",
    "kotlin.DoubleArray",
    "kotlin.BooleanArray",
    "kotlin.Boolean",
    "kotlin.Byte",
    "kotlin.Short",
    "kotlin.Int",
    "kotlin.Float",
    "kotlin.Double",
    "kotlin.Function"
).map { FqName(it) }.toSet()

private class DescriptorlessExternalPackageFragmentSymbol : IrExternalPackageFragmentSymbol {
    override val descriptor: PackageFragmentDescriptor
        get() = error("Operation is unsupported")

    private var _owner: IrExternalPackageFragment? = null
    override val owner get() = _owner!!

    override val isBound get() = _owner != null

    override fun bind(owner: IrExternalPackageFragment) {
        _owner = owner
    }
}

//class MoveBodilessDeclarationsToSeparatePlaceLowering(context: JsIrBackendContext): FileLoweringPass {
//    override fun lower(irFile: IrFile) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//
//}

fun moveBodilessDeclarationsToSeparatePlace(context: JsIrBackendContext, module: IrModuleFragment) {
    val externalPackageFragment = IrExternalPackageFragmentImpl(
        DescriptorlessExternalPackageFragmentSymbol(),
        FqName.ROOT
    )

    context.externalPackageFragment = externalPackageFragment

    val bodilessBuiltInsPackageFragment = IrExternalPackageFragmentImpl(
        DescriptorlessExternalPackageFragmentSymbol(),
        FqName("kotlin")
    )

    context.bodilessBuiltInsPackageFragment = bodilessBuiltInsPackageFragment

    fun isBuiltInClass(declaration: IrDeclaration): Boolean =
        declaration is IrClass && declaration.fqNameWhenAvailable in BODILESS_BUILTIN_CLASSES

    fun collectExternalClasses(container: IrDeclarationContainer, includeCurrentLevel: Boolean): List<IrClass> {
        val externalClasses =
            container.declarations.filterIsInstance<IrClass>().filter { it.isEffectivelyExternal() }

        val nestedExternalClasses =
            externalClasses.flatMap { collectExternalClasses(it, true) }

        return if (includeCurrentLevel)
            externalClasses + nestedExternalClasses
        else
            nestedExternalClasses
    }

    fun lowerFile(irFile: IrFile): IrFile? {
        context.externalNestedClasses += collectExternalClasses(irFile, includeCurrentLevel = false)

        if (irFile.getJsModule() != null || irFile.getJsQualifier() != null) {
            context.packageLevelJsModules.add(irFile)
            return null
        }

        irFile.declarations.transformFlat {
            val d = it as? IrDeclarationWithName ?: return@transformFlat null

            if (isBuiltInClass(d)) {
                bodilessBuiltInsPackageFragment.addChild(d)
                return@transformFlat emptyList()
            } else if (d.isEffectivelyExternal()) {
                if (d.getJsModule() != null)
                    context.declarationLevelJsModules.add(d)

                externalPackageFragment.addChild(d)
                return@transformFlat emptyList()
            }

            null
        }

        return irFile
    }

    module.files.transformFlat { irFile ->
        listOfNotNull(lowerFile(irFile))
    }
}
