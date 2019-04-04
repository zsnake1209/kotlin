fun f(s: String?, t: String): String {
    return s.plus(t)
}

fun g(s: String, t: Any?): String {
    return "$s$t"
}

fun h(s: String, t: Any?): String {
    return s + t
}

fun box(): String {
    if (f("O", "K") != "OK") return "Fail"
    if (g("O", "K") != "OK") return "Fail"
    if (h("O", "K") != "OK") return "Fail"
    return "OK"
}
