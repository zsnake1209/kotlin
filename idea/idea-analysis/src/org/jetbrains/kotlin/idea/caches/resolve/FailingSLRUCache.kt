/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.SLRUCache
import java.util.*

abstract class FailingSLRUCache<K, V>(a: Int, b: Int) : StableSLRUCache<K, V>(a, b) {

    val random = Random(12345)
    val prob = 0.3


    override fun get(key: K): V {
        if (random.nextFloat() <= prob) {
            this.remove(key)
        }
        return super.get(key)
    }

    override fun getIfCached(key: K): V? {
        if (random.nextFloat() <= prob) {
            this.remove(key)
        }
        return super.getIfCached(key)
    }
}

abstract class StableSLRUCache<K, V>(a: Int, b: Int) : SLRUCache<K, V>(a, b) {

    private val delayedPurgeQueue = ContainerUtil.createConcurrentWeakValueMap<K, V>()

    override fun onDropFromCache(key: K, value: V) {
        delayedPurgeQueue[key] = value
    }

    override fun get(key: K): V {
        return delayedPurgeQueue[key] ?: super.get(key)
    }

    override fun getIfCached(key: K): V? {
        return delayedPurgeQueue[key] ?: super.getIfCached(key)
    }

    override fun entrySet(): MutableSet<MutableMap.MutableEntry<K, V>> {
        error("Unsupported")
    }

    override fun iterateKeys(keyConsumer: Consumer<K>?) {
        error("Unsupported")
    }
}