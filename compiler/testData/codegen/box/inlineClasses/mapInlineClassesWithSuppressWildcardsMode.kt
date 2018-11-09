// WITH_RUNTIME

class Foo<T>(val x: Int)

@JvmSuppressWildcards
class Bar {
    fun run(f: (Foo<String>) -> Foo<Long>): Foo<Long> {
        return f(Foo<String>(42))
    }

    fun test(): Foo<Long> {
        return run { f ->
            Foo<Long>(f.x + 1)
        }
    }
}

fun box(): String {
    val a = Bar().test().x
    return if (a == 43) "OK" else "Fail"
}