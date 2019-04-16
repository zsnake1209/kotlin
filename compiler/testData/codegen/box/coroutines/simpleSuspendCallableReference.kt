// !LANGUAGE: +ReleaseCoroutines
// IGNORE_BACKEND: JVM_IR
// WITH_RUNTIME
// WITH_COROUTINES
import helpers.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(EmptyContinuation)
}

suspend fun id(s: String) = s

suspend fun String.idExt() = this

class A {
    suspend fun id(s: String) = s
}

suspend fun run(block: suspend () -> String) = block()

inline suspend fun runInline(block: suspend () -> String) = block()

suspend fun O(block: suspend (String) -> String) = block("O")

inline suspend fun K(block: suspend (String) -> String) = block("K")

fun box(): String {
    var result = ""

    builder {
        result = O(::id) + K(::id)
        result += run("O"::idExt) + runInline("K"::idExt)

        val a = A()
        result += O(a::id) + K(a::id)
    }

    if (result != "OKOKOK") return "fail: $result"

    return "OK"
}
