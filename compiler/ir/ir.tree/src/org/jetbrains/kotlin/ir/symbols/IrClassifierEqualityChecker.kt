/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.symbols

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.types.isEqualsTo
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

interface IrClassifierEqualityChecker {
    fun check(left: IrClassifierSymbol, right: IrClassifierSymbol): Boolean

    companion object {
        object SimpleByReferenceIdentity : IrClassifierEqualityChecker {
            override fun check(left: IrClassifierSymbol, right: IrClassifierSymbol) = left === right
        }

        private val IrDeclarationWithName.fqName
            get(): FqName? {
                val parentFqName = when (val parent = parent) {
                    is IrPackageFragment -> parent.fqName
                    is IrDeclarationWithName -> parent.fqName
                    else -> return null
                }
                return parentFqName?.child(name)
            }

        object FqNameEqualityChecker : IrClassifierEqualityChecker {
            override fun check(left: IrClassifierSymbol, right: IrClassifierSymbol): Boolean {
                if (left === right) return true
                if (!left.isBound || !right.isBound) checkViaDescriptors(left.descriptor, right.descriptor)
                return checkViaDeclarations(left.owner, right.owner)
            }

            private fun isFqnEquality(c1: IrDeclarationWithName, c2: IrDeclarationWithName) = c1.fqName == c2.fqName

            private fun checkViaDeclarations(c1: IrSymbolOwner, c2: IrSymbolOwner): Boolean {
                if (c1 is IrClass && c2 is IrClass) {
                    return isFqnEquality(c1, c2)
                }

                if (c1 is IrTypeParameter && c2 is IrTypeParameter) {
                    if (c1.index != c2.index || c1.name != c2.name) return false
                    val p1 = c1.parent
                    val p2 = c2.parent
                    if (p1 === p2) return true
                    if (p1 is IrClass && p2 is IrClass) return isFqnEquality(p1, p2)
                    if (p1 is IrSimpleFunction && p2 is IrSimpleFunction) {
                        if (!isFqnEquality(p1, p2)) return false
                        if (p1.valueParameters.size != p2.valueParameters.size) return false
                        return p1.valueParameters.zip(p2.valueParameters).all { (l, r) -> l.type.isEqualsTo(r.type, this) }
                    }
                    return false
                }

                return false
            }

            private fun checkViaDescriptors(c1: ClassifierDescriptor, c2: ClassifierDescriptor): Boolean {
                if (c1 is ClassDescriptor && c2 is ClassDescriptor) {
                    return c1.fqNameSafe == c2.fqNameSafe
                }
                if (c1 is TypeParameterDescriptor && c2 is TypeParameterDescriptor) {
                    if (c1.index != c2.index || c1.name != c2.name) return false

                    return c1.typeConstructor == c2.typeConstructor
                }

                return false
            }

        }
    }
}