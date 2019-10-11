fun box(): String {
    var x = 0
    var s = ""
    for {
        if (++x > 3) break
        s = "$s$x;"
    }

    if (s != "1;2;3;") throw AssertionError(s)

    return "OK"
}