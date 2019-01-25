// IGNORE_BACKEND: JVM_IR
// WITH_RUNTIME
// WITH_COROUTINES
// COMMON_COROUTINES_TEST
import helpers.*
import COROUTINES_PACKAGE.*
import COROUTINES_PACKAGE.intrinsics.*
import kotlin.test.assertEquals

suspend fun suspendHere(): String = suspendCoroutineUninterceptedOrReturn { x ->
    x.resume("OK")
    COROUTINE_SUSPENDED
}

suspend fun suspendWithException(): String = suspendCoroutineUninterceptedOrReturn { x ->
    x.resumeWithException(RuntimeException("OK"))
    COROUTINE_SUSPENDED
}

fun builder(c: suspend () -> String): String {
    var fromSuspension: String? = null

    c.startCoroutine(object: ContinuationAdapter<String>() {
        override val context: CoroutineContext
            get() =  EmptyCoroutineContext

        override fun resumeWithException(exception: Throwable) {
            fromSuspension = "Exception: " + exception.message!!
        }

        override fun resume(value: String) {
            fromSuspension = value
        }
    })

    return fromSuspension as String
}

fun box(): String {
    var res = builder { "OK" }
    if (res != "OK") return res
    res = builder { suspendHere() }
    if (res != "OK") return res

    res = builder { throw RuntimeException("OK") }
    if (res != "Exception: OK") return "fail 6"
    res = builder { suspendWithException() }
    if (res != "Exception: OK") return "fail 7"

    return "OK"
}
