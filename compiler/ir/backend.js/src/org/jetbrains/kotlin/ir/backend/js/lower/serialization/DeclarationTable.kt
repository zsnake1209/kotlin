package org.jetbrains.kotlin.ir.backend.js.lower.serialization

import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.IrAnonymousInitializerImpl
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns

fun <K, V> MutableMap<K, V>.putOnce(k:K, v: V): Unit {
    assert(!this.containsKey(k) || this[k] == v) {
        println("adding $v for $k, but it is already ${this[k]} for $k")
    }
    this.put(k, v)
}

class DescriptorTable {
    private val descriptors = mutableMapOf<DeclarationDescriptor, Long>()
    fun put(descriptor: DeclarationDescriptor, uniqId: UniqId) {
        descriptors.putOnce(descriptor, uniqId.index)
    }
    fun get(descriptor: DeclarationDescriptor) = descriptors[descriptor]
}

// TODO: We don't manage id clashes anyhow now.
class DeclarationTable(val builtIns: IrBuiltIns, val descriptorTable: DescriptorTable) {

    val table = mutableMapOf<IrDeclaration, UniqId>()
    val debugIndex = mutableMapOf<UniqId, String>()
    val descriptors = descriptorTable
    var currentIndex = 0x100000000L

    init {
        builtIns.knownBuiltins.forEach {
            table.put(it, UniqId(currentIndex ++, false))
        }
        builtIns.basicSymbols.forEach {
            table[it.owner] = UniqId(currentIndex++, false)
        }
    }

    fun uniqIdByDeclaration(value: IrDeclaration): UniqId {
        val index = table.getOrPut(value) {

            if (value.origin == IrDeclarationOrigin.FAKE_OVERRIDE ||
                !value.isExported()
                    || value is IrVariable
                    || value is IrTypeParameter
                    || value is IrValueParameter
                    || value is IrAnonymousInitializerImpl
            ) {

                UniqId(currentIndex++, true)
            } else {
                UniqId(value.uniqIdIndex, false)
            }
        }

        debugIndex.put(index, "${if (index.isLocal) "" else value.uniqSymbolName()} descriptor = ${value.descriptor}")

        return index
    }
}

// This is what we pre-populate the declaration table with.
val IrBuiltIns.knownBuiltins: List<IrSimpleFunction> // TODO: why do we have this list??? We need the complete list!
    get() = (lessFunByOperandType.values +
            lessOrEqualFunByOperandType.values +
            greaterOrEqualFunByOperandType.values +
            greaterFunByOperandType.values +
            ieee754equalsFunByOperandType.values +
            eqeqeqFun + eqeqFun +
            throwNpeFun + booleanNotFun + noWhenBranchMatchedExceptionFun + enumValueOfFun +
            dataClassArrayMemberToStringFun + dataClassArrayMemberHashCodeFun)
