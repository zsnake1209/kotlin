// EXPECTED_REACHABLE_NODES: 1281
// CALL_MAIN

var ok: String = "OK"

fun main(args: Array<Int>) {
    ok = "Fail Int"
}

fun main(args: Array<in String>) {
    ok = "Fail IN"
}

fun box() = ok