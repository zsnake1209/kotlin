// BAD_FIR_RESOLUTION
// IGNORE_BACKEND: JS_IR
// TODO: muted automatically, investigate should it be ran for JS or not
// DONT_RUN_GENERATED_CODE: JS
// IGNORE_BACKEND: JS

tailrec fun test(go: Boolean) : Unit {
    if (!go) return
    try {
        test(false)
    } catch (any : Exception) {
        test(false)
    } finally {
        test(false)
    }
}

fun box(): String {
    test(true)
    return "OK"
}
