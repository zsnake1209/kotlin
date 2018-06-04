/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CollectionsKt")

package kotlin.collections

import kotlin.*

internal actual object EmptySet : Set<Nothing>, Serializable {
    private const val serialVersionUID: Long = 3406603774387020532

    actual override fun equals(other: Any?): Boolean = other is Set<*> && other.isEmpty()
    actual override fun hashCode(): Int = 0
    actual override fun toString(): String = "[]"

    actual override val size: Int get() = 0
    actual override fun isEmpty(): Boolean = true
    actual override fun contains(element: Nothing): Boolean = false
    actual override fun containsAll(elements: Collection<Nothing>): Boolean = elements.isEmpty()

    actual override fun iterator(): Iterator<Nothing> = EmptyIterator

    private fun readResolve(): Any = EmptySet
}

/**
 * Returns an immutable list containing only the specified object [element].
 * The returned list is serializable.
 * @sample samples.collections.Collections.Lists.singletonReadOnlyList
 */
public fun <T> listOf(element: T): List<T> = java.util.Collections.singletonList(element)


/**
 * Returns a list containing the elements returned by this enumeration
 * in the order they are returned by the enumeration.
 * @sample samples.collections.Collections.Lists.listFromEnumeration
 */
@kotlin.internal.InlineOnly
public inline fun <T> java.util.Enumeration<T>.toList(): List<T> = java.util.Collections.list(this)


@kotlin.internal.InlineOnly
internal actual inline fun copyToArrayImpl(collection: Collection<*>): Array<Any?> =
    kotlin.jvm.internal.collectionToArray(collection)

@kotlin.internal.InlineOnly
internal actual inline fun <T> copyToArrayImpl(collection: Collection<*>, array: Array<T>): Array<T> =
    kotlin.jvm.internal.collectionToArray(collection, array as Array<Any?>) as Array<T>

// copies typed varargs array to array of objects
internal actual fun <T> Array<out T>.copyToArrayOfAny(isVarargs: Boolean): Array<out Any?> =
    if (isVarargs && this.javaClass == Array<Any?>::class.java)
    // if the array came from varargs and already is array of Any, copying isn't required
        @Suppress("UNCHECKED_CAST") (this as Array<Any?>)
    else
        java.util.Arrays.copyOf(this, this.size, Array<Any?>::class.java)
