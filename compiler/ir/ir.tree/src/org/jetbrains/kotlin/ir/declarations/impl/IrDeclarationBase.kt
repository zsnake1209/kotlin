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
import org.jetbrains.kotlin.ir.util.transformFlat
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
    private val proxy = DumbPersistentList<T>(mutableListOf<Wrapper<T>>())

    fun get(): SimpleList<T> {
        return proxy
    }
}

class Wrapper<T>(
    val value: T,
    val addedOn: Int = stageController.currentStage,
    var removedOn: Int = Int.MAX_VALUE
) {
    val alive: Boolean get() = addedOn <= stageController.currentStage && stageController.currentStage < removedOn
}

class DumbPersistentList<T>(val innerList: MutableList<Wrapper<T>>): SimpleList<T> {
    override fun add(element: T): Boolean = innerList.add(Wrapper(element))

    override fun addFirst(element: T) {
        innerList.add(0, Wrapper(element))
    }

    override fun addAll(elements: Collection<T>): Boolean = innerList.addAll(elements.map { Wrapper(it) })

    override fun addFirstAll(elements: Collection<T>) {
        innerList.addAll(0, elements.map { Wrapper(it) })
    }

    override fun plusAssign(element: T) {
        add(element)
    }

    override fun plusAssign(elements: Collection<T>) {
        addAll(elements)
    }

    override fun removeAll(predicate: (T) -> Boolean): Boolean {
        var result = false
        innerList.forEach {
            if (it.alive && predicate(it.value)) {
                it.removedOn = stageController.currentStage
                result = true
            }
        }
        return result
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        return removeAll { it in elements }
    }

    override fun clear() {
        removeAll { true }
    }

    override fun transform(transformation: (T) -> T) {
        innerList.transformFlat {
            if (it.alive) {
                val newValue = transformation(it.value)
                if (newValue === it.value) null else {
                    it.removedOn = stageController.currentStage
                    listOf(it, Wrapper(newValue))
                }
            } else null
        }
    }

    override fun transformFlat(transformation: (T) -> List<T>?) {
        innerList.transformFlat {
            if (!it.alive) null else {
                transformation(it.value)?.let {newElements ->
                    val result = mutableListOf(it)

                    var preserved = false

                    for (e in newElements) {
                        if (it.value === e) {
                            preserved = true
                        } else {
                            result += Wrapper(e)
                        }
                    }

                    if (!preserved) {
                        it.removedOn = stageController.currentStage
                    }

                    result
                }
            }
        }
    }

    override fun remove(element: T): Boolean {
        innerList.forEach {
            if (it.alive && it.value == element) {
                it.removedOn = stageController.currentStage
                return true
            }
        }

        return false
    }

    override val size: Int
        get() = innerList.count { it.alive }

    override fun contains(element: T): Boolean {
        return innerList.find { it.alive && it.value == element } != null
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return elements.all { contains(it) }
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
        return size == 0
    }

    override fun iterator(): Iterator<T> {
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

    override fun listIterator(): ListIterator<T> {
        return listIterator(0)
    }

    override fun listIterator(index: Int): ListIterator<T> {
        return object : ListIterator<T> {
            val innerIterator = innerList.listIterator().also {
                for (i in 0..index) {
                    next()
                }
            }

            var aliveBefore = index

            override fun hasNext(): Boolean {
                while (innerIterator.hasNext()) {
                    val n = innerIterator.next()
                    if (n.alive) {
                        innerIterator.previous()
                        return true
                    }
                }
                return false
            }

            override fun hasPrevious(): Boolean {
                return aliveBefore > 0
            }

            override fun next(): T {
                while (innerIterator.hasNext()) {
                    val n = innerIterator.next()
                    if (n.alive) {
                        ++aliveBefore
                        return n.value
                    }
                }
                throw NoSuchElementException()
            }

            override fun nextIndex(): Int {
                return aliveBefore
            }

            override fun previous(): T {
                while (innerIterator.hasPrevious()) {
                    val p = innerIterator.previous()
                    if (p.alive) {
                        --aliveBefore
                        return p.value
                    }
                }
                throw NoSuchElementException()
            }

            override fun previousIndex(): Int {
                return aliveBefore - 1
            }
        }
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}