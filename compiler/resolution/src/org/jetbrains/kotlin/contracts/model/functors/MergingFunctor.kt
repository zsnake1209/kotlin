/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.model.functors

import org.jetbrains.kotlin.contracts.model.Computation
import org.jetbrains.kotlin.contracts.model.ESEffect
import org.jetbrains.kotlin.contracts.model.Functor

class MergingFunctor(private val childFunctors: List<Functor>) : Functor {
    override fun invokeWithArguments(arguments: List<Computation>): List<ESEffect> {
        return childFunctors.flatMap { it.invokeWithArguments(arguments) }
    }
}