/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.backend.jvm.intrinsics

import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.org.objectweb.asm.Type

class IrIntrinsicMethods(irBuiltIns: IrBuiltIns) {

    val intrinsics = IntrinsicMethods()

    private val irMapping = hashMapOf<IrFunctionSymbol, IntrinsicMethod>()

    private fun createPrimitiveComparisonIntrinsics(typeToIrFun: Map<SimpleType, IrSimpleFunctionSymbol>, operator: KtSingleValueToken) {
        for ((type, irFunSymbol) in typeToIrFun) {
            irMapping[irFunSymbol] = PrimitiveComparison(type, operator)
        }
    }

    init {
        irMapping[irBuiltIns.eqeqSymbol] = Equals(KtTokens.EQEQ)
        irMapping[irBuiltIns.eqeqeqSymbol] = Equals(KtTokens.EQEQEQ)
        irMapping[irBuiltIns.ieee754equalsFunByOperandType[irBuiltIns.float]!!] = Ieee754Equals(Type.FLOAT_TYPE)
        irMapping[irBuiltIns.ieee754equalsFunByOperandType[irBuiltIns.double]!!] = Ieee754Equals(Type.DOUBLE_TYPE)
        irMapping[irBuiltIns.booleanNotSymbol] = Not()

        createPrimitiveComparisonIntrinsics(irBuiltIns.lessFunByOperandType, KtTokens.LT)
        createPrimitiveComparisonIntrinsics(irBuiltIns.lessOrEqualFunByOperandType, KtTokens.LTEQ)
        createPrimitiveComparisonIntrinsics(irBuiltIns.greaterFunByOperandType, KtTokens.GT)
        createPrimitiveComparisonIntrinsics(irBuiltIns.greaterOrEqualFunByOperandType, KtTokens.GTEQ)

        irMapping[irBuiltIns.enumValueOfSymbol] = IrEnumValueOf()
        irMapping[irBuiltIns.noWhenBranchMatchedExceptionSymbol] = IrNoWhenBranchMatchedException()
        irMapping[irBuiltIns.illegalArgumentExceptionSymbol] = IrIllegalArgumentException()
        irMapping[irBuiltIns.throwNpeSymbol] = ThrowNPE()
    }

    fun getIntrinsic(symbol: IrFunctionSymbol): IntrinsicMethod? {
        intrinsics.getIntrinsic(symbol.descriptor)?.let { return it }
        symbol.owner.safeAs<IrSimpleFunction>()?.correspondingPropertySymbol?.let {
            return intrinsics.getIntrinsic(DescriptorUtils.unwrapFakeOverride(it.descriptor))
        }
        return irMapping[symbol]
    }
}
