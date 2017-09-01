// FILE: 1.kt
inline fun foo(f: () -> Unit) {
    f()
}

// FILE: 2.kt
//NO_CHECK_LAMBDA_INLINING
fun box(): String = (l@ {
    foo { return@l "OK" }
    "fail"
}) ()
