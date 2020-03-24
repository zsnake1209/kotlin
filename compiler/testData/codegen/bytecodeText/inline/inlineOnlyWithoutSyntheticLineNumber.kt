// FILE: test.kt
// WITH_RUNTIME
// !LANGUAGE: -GenerateSyntheticLineNumbers

fun box() {
    val b: String

    val a = "foo".let {
        b = it + it
    }

    b.length
}

// 0 LINENUMBER 65100