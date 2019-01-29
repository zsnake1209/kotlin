// IGNORE_BACKEND: JVM_IR
// FILE: inlined.kt
// COMMON_COROUTINES_TEST
// WITH_RUNTIME
// WITH_COROUTINES
// NO_CHECK_LAMBDA_INLINING
// CHECK_STATE_MACHINE

// FILE: 1.kt

interface Flow<T> {
    suspend fun consumeEach(consumer: FlowConsumer<T>)
}

interface FlowConsumer<T> {
    suspend fun consume(value: T)
}

// This functions cross-inlines action into an implementation of FlowConsumer interface
suspend inline fun <T> Flow<T>.consumeEach(crossinline action: suspend (T) -> Unit) =
    consumeEach(object : FlowConsumer<T> {
        override suspend fun consume(value: T) = action(value)
    })

inline fun <T> Flow<T>.onEach(crossinline action: suspend (T) -> Unit): Flow<T> = object : Flow<T> {
    override suspend fun consumeEach(consumer: FlowConsumer<T>) {
        this@onEach.consumeEach { value ->
            action(value)
            consumer.consume(value)
        }
    }
}

// FILE: 2.kt
import helpers.*
import COROUTINES_PACKAGE.*

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(CheckStateMachineContinuation)
}

fun box(): String {
    var log = ""
    builder {
        val f1 = object : Flow<Int> {
            override suspend fun consumeEach(consumer: FlowConsumer<Int>) {
                for (x in 1..10) consumer.consume(x)
            }
        }
        val f2 = f1.onEach {
            StateMachineChecker.suspendHere()
        }
        f2.consumeEach { value ->
            log += value
        }
    }
    StateMachineChecker.check(10)
    if (log != "12345678910") return log
    return "OK"
}
