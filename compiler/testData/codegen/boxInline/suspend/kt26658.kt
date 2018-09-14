// IGNORE_BACKEND: JVM_IR
// FILE: inlined.kt
// LANGUAGE_VERSION: 1.3
// WITH_RUNTIME
// NO_CHECK_LAMBDA_INLINING
import kotlin.coroutines.*

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(object : Continuation<Unit>{
        override fun resumeWith(result: Result<Unit>) {
            result.getOrThrow()
        }

        override val context: CoroutineContext
            get() = EmptyCoroutineContext

    })
}

inline fun execute(crossinline action: suspend () -> Unit) {
    builder { action() }
}

// FILE: inlineSite.kt
import kotlin.coroutines.*

suspend fun withDefaultParameter(s: String, lazy: Boolean = false) = s

fun box(): String {
    var res = ""
    execute {
        res = withDefaultParameter("OK")
    }
    return res
}
