fun f() {
    val f: (Int) -> Int = { <caret>it + it }
}

// NAME: y
// LAMBDA