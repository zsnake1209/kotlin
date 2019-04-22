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
import kotlin.collections.ArrayList

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
//    private val changePoints = TreeMap<Int, MutableList<T>>(mapOf(0 to mutableListOf<T>()))

    private val proxy = DumbPersistentMutableList<T>(mutableListOf<Wrapper<T>>())

    fun get(): MutableList<T> {
        return proxy
    }

    /*{
        val stage = stageController.currentStage
        var result = changePoints[0]!!
//        if (result == null) {
//            val file = try {
//                fileFn()
//            } catch (t: Throwable) {
//                return changePoints[0]!!
//            }
//            if (file == null) return changePoints[0]!!
//
//            stageController.lowerUpTo(file, stage)
//            result = mutableListOf<T>()
//            result.addAll(changePoints.lowerEntry(stage)!!.value)
//            changePoints[stage] = result
//        }
        return result
    }*/
}

class Wrapper<T>(val value: T,
                 val addedOn: Int = stageController.currentStage,
                 var removedOn: Int = Int.MAX_VALUE) {
    val alive: Boolean get() = addedOn <= stageController.currentStage && stageController.currentStage < removedOn
}

class DumbPersistentMutableList<T>(val innerList: MutableList<Wrapper<T>>): MutableList<T> {
    override val size: Int
        get() = innerList.count { it.alive }

    override fun add(element: T): Boolean {
        return innerList.add(Wrapper(element))
    }

    private fun skipNAlive(n: Int): Int {
        var result = 0
        var skipped = 0
        while (skipped < n) {
            while (!innerList[result].alive) ++result
            ++skipped
            ++result
        }

        return result
    }

    override fun add(index: Int, element: T) {
        return innerList.add(skipNAlive(index), Wrapper(element))
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        return innerList.addAll(skipNAlive(index), elements.map { Wrapper(it) })
    }

    override fun addAll(elements: Collection<T>): Boolean {
        return innerList.addAll(elements.map { Wrapper(it) })
    }

    override fun clear() {
        innerList.forEach {
            it.removedOn = stageController.currentStage
        }
    }

    override fun contains(element: T): Boolean {
        return innerList.find { it.alive && it.value == element } != null
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return elements.all { contains(it) }
    }

    override fun get(index: Int): T {
        return innerList[skipNAlive(index + 1) - 1].value
    }

    override fun indexOf(element: T): Int {
        var translatedIndex = -1;
        for (i in 0 until innerList.size) {
            val w = innerList[i]
            if (w.alive) {
                ++translatedIndex
                if (w.value == element) return translatedIndex
            }
        }
        return -1
    }

    override fun isEmpty(): Boolean {
        return innerList.none { it.alive }
    }

    override fun iterator(): MutableIterator<T> {
        return listIterator()
    }

    override fun lastIndexOf(element: T): Int {
        var translatedIndex = -1;
        var result = -1
        for (i in 0 until innerList.size) {
            val w = innerList[i]
            if (w.alive) {
                ++translatedIndex
                if (w.value == element) result = translatedIndex
            }
        }
        return result
    }

    override fun listIterator(): MutableListIterator<T> {
        return listIterator(0)
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        val result = object : MutableListIterator<T> {
            var innerIndex = skipNAlive(index)
            var aliveBefore = index

            var lastIndex = -1

            private fun advance() {
                while (innerIndex < innerList.size && !innerList[innerIndex].alive) ++innerIndex
            }

            override fun add(element: T) {
                lastIndex = -1
                innerList.add(innerIndex, Wrapper(element))
                ++innerIndex
                ++aliveBefore
            }

            override fun hasNext(): Boolean {
                advance()
                return innerIndex < innerList.size
            }

            override fun hasPrevious(): Boolean {
                return aliveBefore > 0
            }

            override fun next(): T {
                advance()
                val result = innerList[innerIndex].value
                lastIndex = innerIndex
                ++innerIndex
                ++aliveBefore
                return result
            }

            override fun nextIndex(): Int {
                return aliveBefore
            }

            override fun previous(): T {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun previousIndex(): Int {
                return aliveBefore - 1
            }

            override fun remove() {
                val w = innerList[lastIndex]
                w.removedOn = stageController.currentStage
                lastIndex = -1
            }

            override fun set(element: T) {
                val w = innerList[lastIndex]
                if (w.value !== element) {
                    w.removedOn = stageController.currentStage
                    innerList.add(lastIndex, Wrapper(element))
                }
                lastIndex = -1
            }
        }

        return result
    }

    override fun remove(element: T): Boolean {
        var result = false
        innerList.forEach {
            if (it.value == element) {
                it.removedOn = stageController.currentStage
                result = true
            }
        }
        return result
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var result = false
        innerList.forEach {
            if (it.value in elements) {
                it.removedOn = stageController.currentStage
                result = true
            }
        }
        return result
    }

    override fun removeAt(index: Int): T {
        val translatedIndex = skipNAlive(index + 1) - 1
        innerList[translatedIndex].value
        val w = innerList[translatedIndex]
        w.removedOn = stageController.currentStage
        return w.value
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        var result = false
        innerList.forEach {
            if (it.alive && it.value !in elements) {
                it.removedOn = stageController.currentStage
                result = true
            }
        }
        return result
    }

    override fun set(index: Int, element: T): T {
        val translatedIndex = skipNAlive(index + 1) - 1
        val w = innerList[translatedIndex]
        if (w.value !== element) {
            w.removedOn = stageController.currentStage
            add(index, element)
            return w.value
        } else {
            return element
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        TODO("not implemented")
    }
}