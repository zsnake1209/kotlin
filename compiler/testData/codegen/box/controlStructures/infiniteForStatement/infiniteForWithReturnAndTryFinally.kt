var s = ""

fun test() {
    var x = 0
    try {
        for {
            if (++x > 3) return
            s = "$s$x;"
        }
    } finally {
        s = "$s{finally}"
    }
}


fun box(): String {
    test()
    if (s != "1;2;3;{finally}") throw AssertionError(s)

    return "OK"
}