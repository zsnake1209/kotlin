// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class MyLazy<T>(private val fn: () -> T) {

    private var value: T? = null

    private var hasValue = false

    operator fun getValue(): T {
        if (!hasValue) {
            hasValue = true
            value = fn()
        }
        return value!!
    }
}

var lazyEvalCount = 0

val test by MyLazy {
    ++lazyEvalCount
    "test"
}

fun box(): String {
    if (test != "test") throw AssertionError()

    if (lazyEvalCount != 1) throw AssertionError()

    if (test != "test") throw AssertionError()

    if (lazyEvalCount != 1) throw AssertionError()

    return "OK"
}