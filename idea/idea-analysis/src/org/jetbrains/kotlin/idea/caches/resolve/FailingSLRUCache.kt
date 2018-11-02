/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.util.containers.SLRUCache
import java.util.*

abstract class FailingSLRUCache<K, V>(a: Int, b: Int) : SLRUCache<K, V>(a, b) {

    val random = Random(12345)

    override fun get(key: K): V {
        if (random.nextFloat() > 0.7) {
            this.remove(key)
        }
        return super.get(key)
    }

    override fun getIfCached(key: K): V? {
        if (random.nextFloat() > 0.7) {
            this.remove(key)
        }
        return super.getIfCached(key)
    }
}