// EXPECTED_REACHABLE_NODES: 1006
class A {
    lateinit var a: Any
}

fun box(): String {
    try {
        A().a
    }
    catch (e: kotlin.UninitializedPropertyAccessException) {
        if (e.message == "lateinit property a has not been initialized") return "OK"
    }
    return "fail"
}