/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.contracts.interpretation

import org.jetbrains.kotlin.contracts.description.CallsEffectDeclaration
import org.jetbrains.kotlin.contracts.description.ConditionalEffectDeclaration
import org.jetbrains.kotlin.contracts.description.EffectDeclaration
import org.jetbrains.kotlin.contracts.description.ReturnsEffectDeclaration
import org.jetbrains.kotlin.contracts.model.ConditionalEffect
import org.jetbrains.kotlin.contracts.model.Functor
import org.jetbrains.kotlin.contracts.model.SimpleEffect
import org.jetbrains.kotlin.contracts.model.functors.SubstitutingFunctor
import org.jetbrains.kotlin.contracts.model.structure.ESCalls
import org.jetbrains.kotlin.contracts.model.structure.ESReturns
import org.jetbrains.kotlin.descriptors.FunctionDescriptor

internal class CallsEffectInterpreter(private val dispatcher: ContractInterpretationDispatcher) : EffectDeclarationInterpreter {
    override fun tryInterpret(effectDeclaration: EffectDeclaration, ownerFunction: FunctionDescriptor): Functor? {
        if (effectDeclaration !is CallsEffectDeclaration) return null

        val variable = dispatcher.interpretVariable(effectDeclaration.variableReference) ?: return null
        val kind = effectDeclaration.kind
        return SubstitutingFunctor(ESCalls(variable, kind), ownerFunction)
    }
}

internal class ConditionalEffectInterpreter(private val dispatcher: ContractInterpretationDispatcher) : EffectDeclarationInterpreter {
    private val conditionInterpreter = ConditionInterpreter(dispatcher)
    private val outcomeInterpreters: List<OutcomeInterpreter> = listOf(
        ReturnsEffectInterpreter(dispatcher)
    )

    override fun tryInterpret(effectDeclaration: EffectDeclaration, ownerFunction: FunctionDescriptor): Functor? {
        if (effectDeclaration !is ConditionalEffectDeclaration) return null

        val effect = outcomeInterpreters.mapNotNull { it.tryInterpret(effectDeclaration.effect) }.singleOrNull() ?: return null
        val condition = effectDeclaration.condition.accept(conditionInterpreter, Unit) ?: return null

        return SubstitutingFunctor(ConditionalEffect(condition, effect), ownerFunction)
    }
}

internal class ReturnsEffectInterpreter(private val dispatcher: ContractInterpretationDispatcher) : OutcomeInterpreter {
    override fun tryInterpret(effectDeclaration: EffectDeclaration): SimpleEffect? {
        if (effectDeclaration !is ReturnsEffectDeclaration) return null
        val returnedValue = dispatcher.interpretConstant(effectDeclaration.value) ?: return null
        return ESReturns(returnedValue)
    }
}