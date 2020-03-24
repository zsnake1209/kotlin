// FILE: test.kt
// WITH_RUNTIME
// IGNORE_BACKEND: JVM_IR

fun box() {
    val b: String

    val a = "foo".let {
        b = it + it
    }

    b.length
}

// 1 LINENUMBER 65100