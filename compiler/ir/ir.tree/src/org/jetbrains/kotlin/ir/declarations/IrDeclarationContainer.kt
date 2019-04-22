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

package org.jetbrains.kotlin.ir.declarations

import org.jetbrains.kotlin.ir.util.transformFlat

interface IrDeclarationParent

interface IrDeclarationContainer : IrDeclarationParent {
    val declarations: SimpleList<IrDeclaration>
}

interface SimpleList<T>: List<T> {

    fun add(element: T): Boolean

    fun addFirst(element: T)

    fun addAll(elements: Collection<T>): Boolean

    fun addFirstAll(elements: Collection<T>)

    operator fun plusAssign(element: T)

    operator fun plusAssign(elements: Collection<T>)

    fun removeAll(predicate: (T) -> Boolean): Boolean

    fun removeAll(elements: Collection<T>): Boolean

    fun clear()

    fun transform(transformation: (T) -> T)

    fun transformFlat(transformation: (T) -> List<T>?)

    fun remove(element: T): Boolean
}

class SimpleMutableList<T>(private val list: MutableList<T>): SimpleList<T>, List<T> by list {
    override fun add(element: T): Boolean = list.add(element)

    override fun addFirst(element: T) {
        list.add(0, element)
    }

    override fun addAll(elements: Collection<T>): Boolean = list.addAll(elements)

    override fun addFirstAll(elements: Collection<T>) {
        list.addAll(0, elements)
    }

    override fun plusAssign(element: T) {
        list += element
    }

    override fun plusAssign(elements: Collection<T>) {
        list += elements
    }

    override fun removeAll(predicate: (T) -> Boolean): Boolean = list.removeAll(predicate)

    override fun removeAll(elements: Collection<T>): Boolean = list.removeAll(elements)

    override fun clear() {
        list.clear()
    }

    override fun transform(transformation: (T) -> T) {
        list.replaceAll(transformation)
    }

    override fun transformFlat(transformation: (T) -> List<T>?) {
        list.transformFlat(transformation)
    }

    override fun remove(element: T): Boolean = list.remove(element)
}