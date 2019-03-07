/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.types

import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierEqualityChecker
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.utils.DFS


fun IrClassifierSymbol.superTypes() = when (this) {
    is IrClassSymbol -> owner.superTypes
    is IrTypeParameterSymbol -> owner.superTypes
    else -> emptyList<IrType>()
}

fun IrClassifierSymbol.isSubtypeOfClass(superClass: IrClassSymbol, checker: IrClassifierEqualityChecker): Boolean {
    if (checker.check(this, superClass)) return true
    return superTypes().any { it.isSubtypeOfClass(superClass, checker) }
}

fun IrType.isSubtypeOfClass(superClass: IrClassSymbol, typeCheckerContext: IrClassifierEqualityChecker): Boolean {
    if (this !is IrSimpleType) return false
    return classifier.isSubtypeOfClass(superClass, typeCheckerContext)
}

fun IrType.isEqualsTo(that: IrType, checker: IrClassifierEqualityChecker): Boolean {
    if (this is IrDynamicType && that is IrDynamicType) return true
    if (this is IrErrorType || that is IrErrorType) return false
    if (this === that) return true
    if (this is IrSimpleType && that is IrSimpleType) return checker.check(this.classifier, that.classifier) &&
            this.arguments.zip(that.arguments).all { (ths, tht) ->
                when (ths) {
                    is IrStarProjection -> tht is IrStarProjection
                    is IrTypeProjection -> tht is IrTypeProjection
                            && ths.variance == tht.variance
                            && ths.type.isEqualsTo(tht.type, checker)
                    else -> error("Unsupported Type Argument")
                }
            }
    return false
}

// TODO: extract LCA algorithm and use wherever it's needed (etc resolveFakeOverride)
fun Collection<IrClassifierSymbol>.commonSuperclass(checker: IrClassifierEqualityChecker): IrClassifierSymbol {
    var superClassifiers: MutableSet<IrClassifierSymbol>? = null

    require(isNotEmpty())

    val order = fold(emptyList<IrClassifierSymbol>()) { _, classifierSymbol ->
        val visited = mutableSetOf<IrClassifierSymbol>()
        DFS.topologicalOrder(listOf(classifierSymbol), { classifier ->
            val superTypes = when (classifier) {
                is IrClassSymbol -> classifier.owner.superTypes
                is IrTypeParameterSymbol -> classifier.owner.superTypes
                else -> error("Unsupported classifier")
            }

            superTypes.map { (it as IrSimpleType).classifier }
        }, DFS.VisitedWithSet(visited)).also {
            val tmp = superClassifiers
            if (tmp == null) {
                superClassifiers = visited
            } else {
                tmp.apply {
                    retainAll { c -> visited.any { v -> checker.check(c, v) } }
                }
            }
        }
    }

    requireNotNull(superClassifiers)

    return order.first { o -> superClassifiers!!.any { s -> checker.check(o, s) } }
}