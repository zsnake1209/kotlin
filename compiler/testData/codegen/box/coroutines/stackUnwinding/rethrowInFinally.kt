// IGNORE_BACKEND: JVM_IR
// WITH_RUNTIME
// WITH_COROUTINES
// COMMON_COROUTINES_TEST
import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*

class Controller {
    suspend fun suspendHere(): String = suspendCoroutineUninterceptedOrReturn { throw RuntimeException("fail") }
}

fun builder(c: suspend Controller.() -> Unit) {
    c.startCoroutine(Controller(), EmptyContinuation)
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
                    result = "FAIL: got into infinite loop"
                }
            }
        }
    } catch (x: Exception) {
        result += x.message
    }

    return result
}