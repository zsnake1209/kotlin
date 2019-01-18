/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.serialization

import org.jetbrains.kotlin.backend.common.descriptors.propertyIfAccessor
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.OverridingUtil
import org.jetbrains.kotlin.resolve.checkers.ExpectedActualDeclarationChecker
//import org.jetbrains.kotlin.konan.library.*

class SerializedIr (
    val module: ByteArray,
    val declarations: Map<UniqId, ByteArray>,
    val debugIndex: Map<UniqId, String>
)

internal val DeclarationDescriptor.isExpectMember: Boolean
    get() = this is MemberDescriptor && this.isExpect

internal val DeclarationDescriptor.isSerializableExpectClass: Boolean
    get() = this is ClassDescriptor && ExpectedActualDeclarationChecker.shouldGenerateExpectClass(this)

val IrDeclaration.isPropertyAccessor get() =
    this is IrSimpleFunction && this.correspondingProperty != null

val IrDeclaration.isPropertyField get() =
    this is IrField && this.correspondingProperty != null

val IrDeclaration.isTopLevelDeclaration get() =
    parent !is IrDeclaration && !this.isPropertyAccessor && !this.isPropertyField

fun IrDeclaration.findTopLevelDeclaration(): IrDeclaration =
    if (this.isTopLevelDeclaration) this
    else if (this.isPropertyAccessor) (this as IrSimpleFunction).correspondingProperty!!.findTopLevelDeclaration()
    else if (this.isPropertyField) (this as IrField).correspondingProperty!!.findTopLevelDeclaration()
    else (this.parent as IrDeclaration).findTopLevelDeclaration()

fun DeclarationDescriptor.findTopLevelDescriptor(): DeclarationDescriptor {
    return if (this.containingDeclaration is org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor) this.propertyIfAccessor
    else this.containingDeclaration!!.findTopLevelDescriptor()
}

tailrec internal fun DeclarationDescriptor.findPackage(): PackageFragmentDescriptor {
    return if (this is PackageFragmentDescriptor) this
    else this.containingDeclaration!!.findPackage()
}

val ModuleDescriptor.isForwardDeclarationModule: Boolean get() =
    name == Name.special("<forward declarations>")

internal fun <T : CallableMemberDescriptor> T.resolveFakeOverrideMaybeAbstract(): Set<T> {
    if (this.kind.isReal) {
        return setOf(this)
    } else {
        val overridden = OverridingUtil.getOverriddenDeclarations(this)
        val filtered = OverridingUtil.filterOutOverridden(overridden)
        // TODO: is it correct to take first?
        @Suppress("UNCHECKED_CAST")
        return filtered as Set<T>
    }
}

//val moduleToLibrary = mutableMapOf<ModuleDescriptor, KonanLibrary>()