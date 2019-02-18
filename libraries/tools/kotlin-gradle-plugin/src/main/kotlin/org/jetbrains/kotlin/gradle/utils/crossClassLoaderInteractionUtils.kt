/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils

import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.cast
import kotlin.reflect.full.memberProperties

/**
 * An [instance] of a type potentially loaded by a different class loader that we believe to be compatible with [representedAsClass],
 * i.e. we may reasonably call some of the API of [representedAsClass] on [instance]. See the operations
 * [ReflectedAccessScope.get], [ReflectedAccessScope.getNullable].
 */
internal class ReflectedInstance<T : Any>(val instance: Any, private val representedAsClass: KClass<T>) {
    companion object {
        /**
         * Wraps an [instance] into a [ReflectedInstance], first checking its type for being [E] or an equivalent one loaded by a different
         * class loader. If it is not, returns null.
         */
        inline fun <reified E : Any> tryWrapAcrossClassLoaders(instance: Any): ReflectedInstance<E>? =
            if (isInstanceAcrossClassLoaders<E>(instance))
                ReflectedInstance(instance, E::class)
            else null
    }

    /**
     * Get the reflected [instance] as an instance of the type [T] (exactly the one loaded by the class loader which loaded [T]).
     * Throws [ClassCastException] if the reflected instance's type is not loaded by this class loader.
     */
    fun extract(): T = representedAsClass.cast(instance)
}

/**
 * Checks the [instance] for [isInstanceAcrossClassLoaders] with the type [R].
 * Returns a [ReflectedInstance] typed with [R] wrapping the [instance] if the check succeeded, null otherwise.
 */
internal inline fun <reified R : Any> ReflectedInstance<*>.tryCastAcrossClassLoaders(): ReflectedInstance<R>? =
    ReflectedInstance.tryWrapAcrossClassLoaders<R>(instance)

/**
 * Check whether the [instance] is [T] or one of its supertypes, potentially loaded by a different class loader,
 * in which case only the type FQ names are compared.
 */
internal inline fun <reified T : Any> isInstanceAcrossClassLoaders(instance: Any): Boolean {
    if (T::class.isInstance(instance)) {
        // Loaded by the same class loader:
        return true
    }

    // or loaded by a different class loader that has a type under the same FQN:
    return instance::class.java.hasSupertypeNamedAs(T::class.java)
}

private fun Class<*>.hasSupertypeNamedAs(tClass: Class<*>): Boolean =
    canonicalName == tClass.canonicalName ||
            superclass?.hasSupertypeNamedAs(tClass) == true ||
            interfaces.any { iface -> iface.hasSupertypeNamedAs(tClass) }

/** Runs the [block] within [ReflectedAccessScope], catching any [ReflectedAccessException] and falling back to [onApiMismatch] */
internal inline fun <T> reflectedAccessAcrossClassLoaders(
    block: ReflectedAccessScope.() -> T,
    onApiMismatch: (e: ReflectedAccessException) -> T
): T =
    try {
        block(ReflectedAccessScope)
    } catch (e: ReflectedAccessException) {
        onApiMismatch(e)
    }

internal object ReflectedAccessScope {

    internal inline fun <reified T : Any> ReflectedInstance.Companion.wrapAcrossClassLoaders(instance: Any): ReflectedInstance<T> =
        tryWrapAcrossClassLoaders<T>(instance) ?: throw ReflectedInstanceTypeMismatchException(instance, T::class)

    private fun chooseRealKProperty(instance: Any, property: KProperty1<*, *>): KProperty1<*, *> =
        instance::class.memberProperties.find { it.name == property.name }
            ?: throw ReflectedInstanceApiNotFoundException(instance, property)

    /**
     * Gets the instance's value of [property] or the property of the same name in the type [T] loaded by a different class loader.
     * Throws [ReflectedInstanceApiNotFoundException] if the class loaded by a different class loader does not have such a property.
     */
    internal inline fun <T : Any, reified R : Any> ReflectedInstance<T>.get(
        property: KProperty1<T, R>
    ): ReflectedInstance<R> {
        val realInstanceProperty = chooseRealKProperty(instance, property)
        val result = realInstanceProperty.call(instance)!!
        return ReflectedInstance.wrapAcrossClassLoaders(result)
    }


    /**
     * Gets the instance's value of [property] or the one of the same name in the type [T] loaded by a different class loader.
     * If the property value is null, returns null.
     * Throws [ReflectedInstanceApiNotFoundException] if the class loaded by a different class loader does not have such a property.
     */
    internal inline fun <T : Any, reified R : Any> ReflectedInstance<T>.getNullable(
        property: KProperty1<T, R?>
    ): ReflectedInstance<R>? {
        val realInstanceProperty = chooseRealKProperty(instance, property)
        val result = realInstanceProperty.call(instance) ?: return null
        return ReflectedInstance.wrapAcrossClassLoaders(result)
    }

    /**
     * Transforms a [ReflectedInstance] of [Iterable] to an [Iterable] of [ReflectedInstance], lazily wrapping
     * each value with [wrapAcrossClassLoaders].
     */
    @Suppress("UNCHECKED_CAST")
    internal inline fun <reified T : Any> ReflectedInstance<out Iterable<T>>.iterate(): Iterable<ReflectedInstance<T>> =
        extract().asSequence().map { ReflectedInstance.wrapAcrossClassLoaders<T>(it) }.asIterable()

}

open class ReflectedAccessException(message: String) : Exception(message)

@Suppress("CanBeParameter")
class ReflectedInstanceTypeMismatchException(val instance: Any, val expectedClass: KClass<*>) :
    ReflectedAccessException(
        "Instance $instance has the class ${instance::class}, which is not in a hierarchy of reflected $expectedClass."
    )

@Suppress("CanBeParameter")
class ReflectedInstanceApiNotFoundException(val instance: Any, val expectedCallable: KCallable<*>) :
    ReflectedAccessException(
        "Instance $instance of type ${instance::class} was expected to have callable API: ${expectedCallable}"
    )
