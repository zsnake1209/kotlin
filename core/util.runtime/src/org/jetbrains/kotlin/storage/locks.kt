/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.storage

import java.lang.UnsupportedOperationException
import java.util.*

enum class LockNames(val debugName: String, val lockFactory: (Int, String, Any) -> LockBlock) {
    SDK("sdk", { order, name, lock -> SdkLock(order, name, lock) }),
    Libraries("project libraries", { order, name, lock -> LibrariesLock(order, name, lock) }),
    NotUnderContentRootModuleInfo("NotUnderContentRootModuleInfo", { order, name, lock -> ModulesLock(order, name, lock) }),
    Modules("project source roots and libraries", { order, name, lock -> ModulesLock(order, name, lock) }),
    ScriptDependencies("dependencies of scripts", { order, name, lock -> ScriptDependenciesLock(order, name, lock) }),
    SpecialInfo("completion/highlighting in ", { order, name, lock -> SpecialInfoLock(order, name, lock) });

    val lockOrder: Int = ordinal
}

private object LockHelper {
    @JvmStatic
    private val lockNamesArray = LockNames.values()

    @JvmStatic
    private val locks: Array<Any> = lockNamesArray.map { Object() }.toTypedArray()

    @JvmStatic
    private fun lock(lockOrder: Int): Any {
        lockOrderCheck(lockOrder)
        return locks[lockOrder]
    }

    private fun lockOrderCheck(lockOrder: Int) {
        check(lockOrder in lockNamesArray.indices) {
            "lockOrder $lockOrder has to be in range of ${lockNamesArray.indices}"
        }
    }

    // overloaded method for the sake of java interop
    @JvmStatic
    fun resolveLock(name: String): LockBlock = resolveLock(null, name)

    // overloaded method for the sake of java interop
    @JvmStatic
    fun resolveLock(lockOrder: Int?, name: String): LockBlock = resolveLock(lockOrder, name, null)

    @JvmStatic
    fun resolveLock(lockOrder: Int?, name: String, sourceLockBlock: LockBlock?): LockBlock =
        lockOrder?.let { order ->
            lockOrderCheck(order)
            lockNamesArray[order].lockFactory?.invoke(order, name, sourceLockBlock?.lock ?: lock(lockOrder))
        }
            ?: SimpleLock(sourceLockBlock?.lock ?: Object())


}

interface LockBlock {
    val lock: Any
    fun <T> guarded(computable: () -> T?): T?
}

private object NoLockBlock : LockBlock {
    override val lock: Any
        get() = throw UnsupportedOperationException("NoLockBlock has no lock")

    override fun <Any> guarded(computable: () -> Any?): Any? {
        return computable()
    }
}

private class SimpleLock(override val lock: Any) : LockBlock {
    override fun <T> guarded(computable: () -> T?): T? =
        // Use `synchronized` as dead lock case will be handled by JVM and would be immediately visible rather with ReentrantLock
        synchronized(lock) {
            computable()
        }
}

abstract class TrackLock(val order: Int, val name: String, override val lock: Any) : LockBlock {

    protected var ownerName: String? = null

    protected inline fun <T> checkAndExecute(computable: () -> T?): T? {
        preLockCheck()

        // Use `synchronized` as dead lock case will be handled by JVM and would be immediately visible rather with ReentrantLock
        return synchronized(lock) {
            postLock()
            try {
                computable()
            } finally {
                preUnlock()
            }
        }
    }

    protected inline fun preLockCheck() {
        val stack: Stack<TrackLock> = acquiredLocks.get()
        if (stack.isEmpty()) return

        val peek = stack.peek()
        check(order <= peek.order) {
            ("${Thread.currentThread().name}: Incorrect lock order: $this tries to acquire lock after $peek")
        }

        // fail in case if diff lock instances
        check(!(order == peek.order && lock !== peek.lock)) {
            ("${Thread.currentThread().name}: Incorrect lock order: $this same lock order is already acquired for $peek")
        }
    }


    protected inline fun postLock() {
        ownerName = Thread.currentThread().name

        val stack: Stack<TrackLock> = acquiredLocks.get()
        stack.push(this)
    }

    protected inline fun preUnlock() {
        val stack: Stack<TrackLock> = acquiredLocks.get()
        stack.pop()
        ownerName = null
    }

    override fun toString(): String {
        return "${javaClass.simpleName} '$name'/$order@$lock${if (ownerName != null) "[Owner:$ownerName]" else ""}"
    }

    companion object {
        @JvmStatic
        val acquiredLocks = object : ThreadLocal<Stack<TrackLock>>() {
            override fun initialValue(): Stack<TrackLock> = Stack()
        }
    }
}

/**
 * The reason behind all those SdkLock, LibrariesLock etc is to keep <b>named trace</b> in thread dumps / stack traces
 */

private class SdkLock(order: Int, name: String, lock: Any) : TrackLock(order, name, lock) {
    override fun <T> guarded(computable: () -> T?): T? = checkAndExecute(computable)
}

private class LibrariesLock(order: Int, name: String, lock: Any) : TrackLock(order, name, lock) {
    override fun <T> guarded(computable: () -> T?): T? = checkAndExecute(computable)
}

private class ModulesLock(order: Int, name: String, lock: Any) : TrackLock(order, name, lock) {
    override fun <T> guarded(computable: () -> T?): T? = checkAndExecute(computable)
}

private class ScriptDependenciesLock(order: Int, name: String, lock: Any) : TrackLock(order, name, lock) {
    override fun <T> guarded(computable: () -> T?): T? = checkAndExecute(computable)
}

private class SpecialInfoLock(order: Int, name: String, lock: Any) : TrackLock(order, name, lock) {
    override fun <T> guarded(computable: () -> T?): T? = checkAndExecute(computable)
}