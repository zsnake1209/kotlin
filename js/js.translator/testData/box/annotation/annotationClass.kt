// EXPECTED_REACHABLE_NODES: 1280
package foo

annotation class bar

public annotation class Baz(val a: String)

fun box(): String {
    1L
    "aaa" + "aaa" + 1
    return "OK"
}
