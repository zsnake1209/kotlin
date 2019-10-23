package asyncInlineFunctionsWithThis

suspend fun main() {
        A().foo()
}

class A {
    suspend inline fun inlineFun(s: () -> Unit) {
        val k = 0
        delay()
        //Breakpoint!
        val m = 31415926
    }

    suspend fun foo() {
        var s = 1
        A().inlineFun {
            var zzz = 2
            delay()
            zzz++
        }
    }
}

suspend fun delay() {
}
