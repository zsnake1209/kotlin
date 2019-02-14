/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.base.util

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.net.URI
import java.net.URL
import java.util.*

internal class ClassPath(input: Sequence<URI>) {
    private val hash: Long
    private val locations = LinkedHashSet<URI>()

    init {
        var computedHash = 0L
        for (location in input) {
            locations.add(location)
            computedHash = 31 * computedHash + location.hashCode()
        }
        hash = computedHash
    }

    fun toArray(): Array<URL> {
        return locations.map { it.toURL() }.toTypedArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClassPath) return false

        if (hash != other.hash) return false
        if (locations != other.locations) return false

        return true
    }

    override fun hashCode() = hash.hashCode()
}

internal fun classPathOf(gen: suspend SequenceScope<URI>.() -> Unit) = ClassPath(sequence(gen))

// Copied from com.intellij.ide.ClassUtilCore
internal fun clearJarURLCache() {
    fun clearMap(cache: Field) {
        cache.isAccessible = true

        if (!Modifier.isFinal(cache.modifiers)) {
            cache.set(null, hashMapOf<Any, Any>())
        } else {
            val map = cache.get(null) as MutableMap<*, *>
            map.clear()
        }
    }

    try {
        val jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory")

        clearMap(jarFileFactory.getDeclaredField("fileCache"))
        clearMap(jarFileFactory.getDeclaredField("urlCache"))
    } catch (ignore: Exception) {
    }
}
