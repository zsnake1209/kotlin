package recursiveFrameLambda

fun main() {
    foo {
        6
    }
}

fun foo(i: () -> Int): Int {
    val k = i()
    val l = k
    return if (k > 1) foo {
        if (k == 2)
            //Breakpoint!
            Unit // like a conditional breakpoint
        k - 1
    } else 1
}

// EXPRESSION: l
// RESULT: 'l' is not captured
