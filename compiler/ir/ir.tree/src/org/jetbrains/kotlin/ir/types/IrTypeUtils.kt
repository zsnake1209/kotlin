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
    if (checker.areEqual(this, superClass)) return true
    return superTypes().any { it.isSubtypeOfClass(superClass, checker) }
}

fun IrType.isSubtypeOfClass(superClass: IrClassSymbol, typeCheckerContext: IrClassifierEqualityChecker): Boolean {
    if (this !is IrSimpleType) return false
    return classifier.isSubtypeOfClass(superClass, typeCheckerContext)
}

fun IrType.isEqualTo(that: IrType, checker: IrClassifierEqualityChecker): Boolean {
    if (this is IrDynamicType && that is IrDynamicType) return true
    if (this is IrErrorType || that is IrErrorType) return false
    if (this === that) return true
    if (this is IrSimpleType && that is IrSimpleType) return checker.areEqual(this.classifier, that.classifier) &&
            this.arguments.zip(that.arguments).all { (ths, tht) ->
                when (ths) {
                    is IrStarProjection -> tht is IrStarProjection
                    is IrTypeProjection -> tht is IrTypeProjection
                            && ths.variance == tht.variance
                            && ths.type.isEqualTo(tht.type, checker)
                    else -> error("Unsupported Type Argument")
                }
            }
    return false
}

fun Collection<IrClassifierSymbol>.commonSuperclass(checker: IrClassifierEqualityChecker): IrClassifierSymbol {
    var superClassifiers: MutableSet<IrClassifierSymbol>? = null

    require(isNotEmpty())

    val order = fold(emptyList<IrClassifierSymbol>()) { _, classifierSymbol ->
        val visited = mutableSetOf<IrClassifierSymbol>()
        DFS.topologicalOrder(
            listOf(classifierSymbol), { it.superTypes().map { s -> (s as IrSimpleType).classifier } },
            DFS.VisitedWithSet(visited)
        ).also {
            if (superClassifiers == null) {
                superClassifiers = visited
            } else {
                superClassifiers?.apply {
                    retainAll { c -> visited.any { v -> checker.areEqual(c, v) } }
                }
            }
        }
    }

    requireNotNull(superClassifiers)

    return order.firstOrNull { o -> superClassifiers!!.any { s -> checker.areEqual(o, s) } }
        ?: error("No common superType found for non-empty set of classifiers")
}