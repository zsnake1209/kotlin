
// FILE: test.kt
inline fun foo(stringMaker: () -> String): String {
    return stringMaker()
}

fun box(): String {
    foo { "OK "}
    foo {
        "OK"
        // This line is hit by the old backend after line 13
    }
    return "OK"
}

// LINENUMBERS
// test.kt:8
// test.kt:4
// test.kt:8
// test.kt:9
// test.kt:4
// test.kt:10
// test.kt:13