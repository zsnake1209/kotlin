// BAD_FIR_RESOLUTION
// IGNORE_BACKEND: JS_IR
// TODO: muted automatically, investigate should it be ran for JS or not
// DONT_RUN_GENERATED_CODE: JS
// IGNORE_BACKEND: JS

tailrec fun noTails() {
    // nothing here
}

fun box(): String {
    noTails()
    return "OK"
}
