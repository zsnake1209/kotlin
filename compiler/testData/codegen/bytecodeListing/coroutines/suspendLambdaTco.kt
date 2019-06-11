// WITH_RUNTIME
// COMMON_COROUTINES_TEST

val c: suspend () -> Unit = {}

suspend fun dummy() {}

val d: suspend () -> Unit = { dummy() }

fun test() {
    var i = 0
    val c: suspend () -> Unit = { i++ }
    suspend fun local() {
        return dummy()
    }
}
