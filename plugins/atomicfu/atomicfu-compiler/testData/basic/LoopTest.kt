import kotlinx.atomicfu.*
import kotlin.test.*

class LoopTest {
    private val a = atomic(0)
    private val r = atomic<A>(A("aaaa"))

    private class A(val s: String)

    private inline fun casLoop(to: Int): Int {
        a.loop { cur ->
            if (a.compareAndSet(cur, to)) return a.value
        }
    }

    private inline fun AtomicInt.extensionLoop(to: Int): Int {
        loop { cur ->
            if (compareAndSet(cur, to)) return value
        }
    }

    private inline fun AtomicInt.returnExtensionLoop(to: Int): Int =
        loop { cur ->
            lazySet(cur + 10)
            return if (compareAndSet(cur, to)) value else incrementAndGet()
        }

    private inline fun AtomicRef<A>.casLoop(to: A): A = loop { cur ->
        if (compareAndSet(cur, to)) return value
    }

    fun testIntExtensionLoops() {
        check(casLoop(5) == 5)
        check(a.extensionLoop(66) == 66)
        check(a.returnExtensionLoop(777) == 77)
    }

    fun testRefExtensionLoops() {
        assertEquals("bbbbb", r.casLoop(A("bbbbb")).s)
    }
}

fun box(): String {
    val testClass = LoopTest()
    testClass.testIntExtensionLoops()
    testClass.testRefExtensionLoops()
    return "OK"
}