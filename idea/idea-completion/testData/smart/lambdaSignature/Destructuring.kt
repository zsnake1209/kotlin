data class P(val left: Int, val right: Int)

fun foo(l: (P) -> Unit) {}

fun bar() {
    foo { <caret> }
}

// EXIST: "(left, right) ->"
// EXIST: "(left: Int, right: Int) ->"