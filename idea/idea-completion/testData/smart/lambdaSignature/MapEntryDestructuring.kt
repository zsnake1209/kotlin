
fun foo(l: (Map.Entry<Int, Int>) -> Unit) {}


fun bar() {
    foo { <caret> }
}

// WITH_RUNTIME
// EXIST: "(key, value) ->"
// EXIST: "(key: Int, value: Int) ->"