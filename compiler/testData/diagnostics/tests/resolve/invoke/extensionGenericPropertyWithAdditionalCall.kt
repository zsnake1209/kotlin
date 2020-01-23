// !DIAGNOSTICS: -UNUSED_PARAMETER

class Wrapper<K>(val value: K) {
    operator fun invoke(other: K) {}
}

val <T> T.foo: Wrapper<T> get() = Wrapper(this)
fun <T> T.bar(): Wrapper<T> = Wrapper(this)

fun main(args: Array<String>) {
    "abc".foo("abc")
    "abc".bar()("abc")
}