// EXPECTED_REACHABLE_NODES: 1281
// CALL_MAIN

var ok: String = "OK"

fun main(args: Array<out String>) {
    ok = "Fail OUT"
}

fun main(args: Array<in String>) {
    ok = "Fail IN"
}

fun box() = ok