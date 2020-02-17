// WITH_RUNTIME

import kotlin.test.assertEquals

fun strangeLoop() {
    for (i in 10 until 0) {
        throw AssertionError("This loop should not be executed")
    }
}

fun box(): String {
    strangeLoop()
    return "OK"
}