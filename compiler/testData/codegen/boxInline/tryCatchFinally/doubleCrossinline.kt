// FILE: 1.kt

fun error(s: String): Nothing = throw IllegalStateException(s)

interface Flow<T> {
    fun consumeEach(consumer: FlowConsumer<T>)
}

interface FlowConsumer<T> {
    fun consume(value: T)
}

// This functions cross-inlines action into an implementation of FlowConsumer interface
inline fun <T> Flow<T>.consumeEach(crossinline action: (T) -> Unit) =
    consumeEach(object : FlowConsumer<T> {
        override fun consume(value: T) = action(value)
    })

inline fun <T> Flow<T>.onEach(crossinline action: (T) -> Unit): Flow<T> = object : Flow<T> {
    override fun consumeEach(consumer: FlowConsumer<T>) {
        this@onEach.consumeEach { value ->
            action(try { value } catch (e: Exception) { error("") })
            consumer.consume(value)
        }
    }
}

// FILE: 2.kt

fun box(): String {
    var log = ""
    val f1 = object : Flow<Int> {
        override fun consumeEach(consumer: FlowConsumer<Int>) {
            for (x in 1..10) consumer.consume(x)
        }
    }
    val f2 = f1.onEach {
        log += it
    }
    f2.consumeEach {
    }
    if (log != "12345678910") return log
    return "OK"
}
