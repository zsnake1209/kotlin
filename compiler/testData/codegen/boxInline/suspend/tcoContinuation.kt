// IGNORE_BACKEND: JVM_IR
// FILE: flow.kt
// COMMON_COROUTINES_TEST
// FULL_JDK
// WITH_RUNTIME
// WITH_COROUTINES
package flow

interface FlowCollector<T> {
    suspend fun emit(value: T)
}

interface Flow<T> {
    suspend fun collect(collector: FlowCollector<T>)
}

public inline fun <T> flow(crossinline block: suspend FlowCollector<T>.() -> Unit) = object : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) = collector.block()
}

suspend inline fun <T> Flow<T>.collect(crossinline action: suspend (T) -> Unit): Unit =
    collect(object : FlowCollector<T> {
        override suspend fun emit(value: T) = action(value)
    })

public inline fun <T, R> Flow<T>.transform(crossinline transformer: suspend FlowCollector<R>.(value: T) -> Unit): Flow<R> {
    return flow {
        return@flow collect { value ->
            return@collect transformer(value)
        }
    }
}

public inline fun <T, R> Flow<T>.map(crossinline transformer: suspend (value: T) -> R): Flow<R> = transform { value -> return@transform emit(transformer(value)) }

// FILE: box.kt
// COMMON_COROUTINES_TEST

import flow.*

suspend fun foo() {
    flow<Int> {
        emit(1)
    }.map { it + 1 }
        .collect {
        }
}

fun box() : String {
    try {
        Class.forName("BoxKt\$foo\$\$inlined\$map\$1\$1")
        return "FAIL"
    } catch (ignored: ClassNotFoundException) {
        return "OK"
    }
}
