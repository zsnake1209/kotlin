/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.storage;

/**
 * A storage for the value that should exist and be accessible in the single thread.
 *
 * Unlike ThreadLocal, thread doesn't store a reference to the value that makes it inaccessible globally, but simplifies memory
 * management.
 *
 * The other difference from ThreadLocal is inability to have different values per each thread, so SingleThreadValue instance
 * should be protected with external lock from rewrites.
 *
 * @param <T>
 */
class SingleThreadValue<T> {
    private final T value;

    SingleThreadValue(T value) {
        this.value = value;
    }

    public boolean hasValue() {
        return true;
    }

    public T getValue() {
        if (!hasValue()) throw new IllegalStateException("No value in this thread (hasValue should be checked before)");
        return value;
    }
}
