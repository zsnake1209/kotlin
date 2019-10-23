package asyncInlineFunctions

suspend fun main() {
    val a = 228
    foo()
}

suspend inline fun foo() {
    val b = 239
    bar()
    val e = 1488
}

suspend inline fun bar() {
    val c = 322
    delay()
    //Breakpoint!
    val d = 1337
}

suspend fun delay() {
}
