fun box(): String {
    var x = 0
    var s = ""
    for {
        if (++x > 3) break
        if (x < 2) continue
        s = "$s$x;"
    }

    if (s != "2;3;") throw AssertionError(s)

    return "OK"
}