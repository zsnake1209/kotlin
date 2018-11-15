// IGNORE_BACKEND: JVM_IR
// WITH_RUNTIME
// WITH_COROUTINES
// COMMON_COROUTINES_TEST
import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

// Note: This test for issue KT-28207 about infinite loop after throwing exception from finally block

suspend fun suspendHere(): String = suspendCoroutineUninterceptedOrReturn { throw RuntimeException("fail") }

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

fun box(): String {
    var result = ""

    var count = 0
    try {
        builder {
            try {
                suspendHere()
            } finally {
                if (count == 0) {
                    count++
                    result = "O"
                    throw Exception("K")
                } else {
                    result = "FAIL: execution gets into infinite loop"
                }
            }
        }
    } catch (x: Exception) {
        result += x.message
    }

    return result
}