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
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.ir.expressions.IrCall
import java.util.*
import kotlin.collections.ArrayList

abstract class IrDeclarationBase(
    startOffset: Int,
    endOffset: Int,
    override var origin: IrDeclarationOrigin
) : IrElementBase(startOffset, endOffset),
    IrDeclaration,
    HasStageController {

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

    override var stageController: StageController = NoopController()
}

interface HasStageController {
    var stageController: StageController
}

interface StageController {
    val currentStage: Int
}

class NoopController : StageController {
    override val currentStage: Int = 0
}

class ListManager<T>(val hasStageController: HasStageController) {

    private val changePoints = TreeMap<Int, MutableList<T>>(mapOf(0 to mutableListOf<T>()))

    fun get(): MutableList<T> {
        val stage = hasStageController.stageController.currentStage
        var result = changePoints[stage]
        if (result == null) {
            result = mutableListOf<T>()
            result.addAll(changePoints.lowerEntry(stage)!!.value)
            changePoints[stage] = result
        }
        return result
    }
}