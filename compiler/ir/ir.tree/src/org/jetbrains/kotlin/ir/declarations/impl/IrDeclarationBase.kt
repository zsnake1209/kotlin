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

package org.jetbrains.kotlin.ir.declarations.impl

import org.jetbrains.kotlin.ir.IrElementBase
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.absoluteValue

abstract class IrDeclarationBase(
    startOffset: Int,
    endOffset: Int,
    override var origin: IrDeclarationOrigin
) : IrElementBase(startOffset, endOffset),
    IrDeclaration{

    override lateinit var parent: IrDeclarationParent

    override val annotations: MutableList<IrCall> = ArrayList()

    override val metadata: MetadataSource?
        get() = null
//
//    var createdOn: Int = 0
//
//    var loweredUpTo: Int = 0
//
//    var removedAt: Int = Integer.MAX_VALUE
}

// TODO hack
var stageController: StageController = NoopController()

interface StageController {
    val currentStage: Int

    fun lowerUpTo(file: IrFile, stageNonInclusive: Int)
}

class NoopController : StageController {
    override val currentStage: Int = 0

    override fun lowerUpTo(file: IrFile, stageNonInclusive: Int) {}
}

class ListManager<T>(val fileFn: () -> IrFile?) {
    private val changePoints = TreeMap<Int, MutableList<T>>(mapOf(0 to mutableListOf<T>()))

    fun get(): MutableList<T> {
        val stage = stageController.currentStage
        var result = changePoints[stage]
        if (result == null) {
            val file = try {
                fileFn()
            } catch (t: Throwable) {
                return changePoints[0]!!
            }
            if (file == null) return changePoints[0]!!

            stageController.lowerUpTo(file, stage)
            result = mutableListOf<T>()
//            if (stage - 1 !in changePoints) {
//                println("!!!!")
//            }
            result.addAll(changePoints.lowerEntry(stage)!!.value)
            changePoints[stage] = result
        }
        return result
    }
}