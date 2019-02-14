/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.base.util

import java.net.URL
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal class SpeculativeClassloader private constructor(
    urls: Array<URL>, parent: ClassLoader, nextClassLoader: SpeculativeClassloader?
) : CacheInvalidatingURLClassLoader(urls, parent) {
    constructor(urls: Array<URL>, parent: ClassLoader) : this(urls, parent, SpeculativeClassloader(urls, parent, null))

    sealed class State {
        class Active(val loader: SpeculativeClassloader) : State()
        class Idle(val loader: SpeculativeClassloader) : State()
        object Flipped : State() // no link to next loader, resources closed
    }

    private val state = AtomicReference(nextClassLoader?.let { State.Active(it) } ?: State.Flipped)
    private val lock = java.lang.Object()
    private val queue: Deque<String> = LinkedList()
    private val loadedClasses: MutableSet<String> = LinkedHashSet()

    // multi-threaded loading doesn't really help
    private val loaderHelper = thread(start = nextClassLoader != null, name = "speculative class loader") {
        val thread = Thread.currentThread()
        while (thread.isAlive && loop()) {
        }
    }

    fun flip(): SpeculativeClassloader {
        val oldState = state.getAndSet(State.Flipped)
        val result = when (oldState) {
            is State.Active -> {
                try {
                    synchronized(lock) {
                        queue.clear()
                        lock.notify()
                    }
                    loaderHelper.join()
                } finally {
                    super.close()
                }
                oldState.loader
            }
            is State.Idle -> {
                oldState.loader
            }
            is State.Flipped -> return SpeculativeClassloader(urLs, parent).also {
                println("already flipped, create a copy")
            }
        }
        synchronized(lock) {
            result.queue.addAll(loadedClasses)
        }
        result.state.set(State.Active(SpeculativeClassloader(urLs, parent, null)))
        return result.also {
            it.loaderHelper.start()
        }
    }

    override fun close() {
        try {
            var shouldWait = state.get() is State.Active && loaderHelper.isAlive
            if (shouldWait) {
                synchronized(lock) {
                    queue.clear()
                    val currentState = state.get()
                    if (currentState is State.Active) {
                        state.compareAndSet(currentState, State.Idle(currentState.loader))
                        lock.notify()
                    } else {
                        shouldWait = false
                    }
                }
            }
            if (shouldWait) {
                loaderHelper.join()
            }
        } finally {
            super.close()
        }
    }

    private fun add(name: String) {
        synchronized(lock) {
            // TODO: use non-blocking queue
            queue.add(name)
            lock.notify()
        }
    }

    private fun loop(): Boolean {
        if (state.get() !is State.Active) {
            return false
        }

        var name: String? = null
        var classLoader: ClassLoader? = null
        var active = false
        synchronized(lock) {
            if (queue.isEmpty()) {
                try {
                    lock.wait(200) // prevent erroneous forever loop
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }

            val currentState = state.get()
            if (currentState is State.Active) {
                active = true
                classLoader = currentState.loader

                name = queue.poll()?.also {
                    loadedClasses.add(it)
                }
            }
        }
        if (!active) {
            return false
        }
        if (name != null) {
            try {
                Class.forName(name, false, classLoader)
            } catch (_: ClassNotFoundException) {
            }
        }
        return true
    }

    override fun findClass(name: String): Class<*> {
        if (state.get() is State.Active) {
            add(name)
        }
        return super.findClass(name)
    }
}
