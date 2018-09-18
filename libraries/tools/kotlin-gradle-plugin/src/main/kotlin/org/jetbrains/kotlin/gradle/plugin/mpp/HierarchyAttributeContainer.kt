/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import java.util.*

abstract class AttributeTransformer {
    abstract fun <T> transform(key: Attribute<T>, value: T): T?

    companion object {
        fun create(transformFunction: (key: Attribute<*>, value: Any?) -> Any?) = object : AttributeTransformer() {
            @Suppress("UNCHECKED_CAST")
            override fun <T> transform(key: Attribute<T>, value: T): T? =
                key.type.cast(transformFunction(key, value))
        }
    }
}

fun <T> AttributeTransformer.fetchAndTransform(key: Attribute<T>, attributeContainer: AttributeContainer): T? {
    // Type safety wrapper, captures `T`
    val value = attributeContainer.getAttribute(key) ?: return null
    return transform(key, value)
}

// TODO better implementation: attribute invariants (no attrs with same name and different types allowed), thread safety?
class HierarchyAttributeContainer(
    val parent: AttributeContainer?,
    private val transformParentAttributes: AttributeTransformer = AttributeTransformer.create { _, value -> value }
) : AttributeContainer {
    private val attributesMap = Collections.synchronizedMap(mutableMapOf<Attribute<*>, Any>())

    override fun contains(key: Attribute<*>): Boolean =
        attributesMap.contains(key) || parent?.let { transformParentAttributes.fetchAndTransform(key, it) } != null

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getAttribute(key: Attribute<T>): T? =
        attributesMap.get(key) as T? ?: parent?.let { transformParentAttributes.fetchAndTransform(key, it) }

    override fun isEmpty(): Boolean = attributesMap.isEmpty() && parent?.isEmpty ?: false

    override fun keySet(): Set<Attribute<*>> = attributesMap.keys +
            parent?.keySet()?.filter { transformParentAttributes.fetchAndTransform(it, parent) != null }.orEmpty()

    override fun <T : Any?> attribute(key: Attribute<T>?, value: T): AttributeContainer {
        val checkedValue = requireNotNull(value as Any?) { "null values for attributes are not supported" }
        attributesMap[key as Attribute<*>] = checkedValue
        return this
    }

    override fun getAttributes(): AttributeContainer = this
}