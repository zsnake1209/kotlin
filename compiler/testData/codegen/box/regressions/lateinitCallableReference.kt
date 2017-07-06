// WITH_RUNTIME

fun test() {
    simple(::some)
}

fun some(s: String): Int = 0

fun <T, R> simple(x: (T) -> R): R { return null!! }
//fun <T, R> simple(x: (T) -> R) {  }

fun box(): String {
    return "OK"
}