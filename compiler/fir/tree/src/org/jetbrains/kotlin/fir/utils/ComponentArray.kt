/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.utils

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class ComponentArrayOwner<K : Any, V : Any> {
    internal val componentArray: ComponentArray<V> = ComponentArray()
    protected abstract val typeRegistry: ComponentTypeRegistry<K, V>

    protected fun registerComponent(tClass: KClass<out K>, value: V) {
        componentArray[(typeRegistry.getId(tClass))] = value
    }
}


abstract class ComponentTypeRegistry<K : Any, V : Any> {
    private val idPerType = mutableMapOf<KClass<out K>, Int>()

    fun <T : V, KK : K> generateAccessor(kClass: KClass<KK>): ComponentArrayAccessor<K, V, T> {
        return ComponentArrayAccessor(kClass, getId(kClass))
    }

    fun <T : V, KK : K> generateNullableAccessor(kClass: KClass<KK>): NullableComponentArrayAccessor<K, V, T> {
        return NullableComponentArrayAccessor(kClass, getId(kClass))
    }

    fun <T : K> getId(kClass: KClass<T>): Int {
        return idPerType.getOrPut(kClass) { idPerType.size }
    }
}


abstract class AbstractComponentArrayAccessor<K : Any, V : Any, T : V>(
    protected val key: KClass<out K>,
    protected val id: Int
) {
    protected fun extractValue(thisRef: ComponentArrayOwner<K, V>): T? {
        @Suppress("UNCHECKED_CAST")
        return thisRef.componentArray[id] as T?
    }
}


class ComponentArrayAccessor<K : Any, V : Any, T : V>(
    key: KClass<out K>,
    id: Int
) : AbstractComponentArrayAccessor<K, V, T>(key, id), ReadOnlyProperty<ComponentArrayOwner<K, V>, V> {
    override fun getValue(thisRef: ComponentArrayOwner<K, V>, property: KProperty<*>): T {
        return extractValue(thisRef) ?: error("No '$key'($id) component in array owner: $thisRef")
    }
}

class NullableComponentArrayAccessor<K : Any, V : Any, T : V>(
    key: KClass<out K>,
    id: Int
) : AbstractComponentArrayAccessor<K, V, T>(key, id), ReadOnlyProperty<ComponentArrayOwner<K, V>, V?> {
    override fun getValue(thisRef: ComponentArrayOwner<K, V>, property: KProperty<*>): T? {
        return extractValue(thisRef)
    }
}

class ComponentArray<T : Any> {
    companion object {
        private const val DEFAULT_SIZE = 20
        private const val INCREASE_K = 2
    }

    private var data = arrayOfNulls<Any>(DEFAULT_SIZE)
    private fun ensureCapacity(index: Int) {
        if (data.size < index) {
            data = data.copyOf(data.size * INCREASE_K)
        }
    }

    operator fun set(index: Int, value: T) {
        ensureCapacity(index)
        data[index] = value
    }

    operator fun get(index: Int): T? {
        @Suppress("UNCHECKED_CAST")
        return data.getOrNull(index) as T?
    }
}