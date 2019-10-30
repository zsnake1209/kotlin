/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.utils

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import kotlin.reflect.KProperty

internal operator fun <T> Provider<T>.getValue(thisRef: Any?, property: KProperty<*>) = get()

internal operator fun <T> Property<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    set(value)
}

internal fun <T : Any> Project.newProperty(initialize: (() -> T)? = null): Property<T> =
    @Suppress("UNCHECKED_CAST")
    (project.objects.property(Any::class.java) as Property<T>).apply {
        if (initialize != null)
            set(provider(initialize))
    }