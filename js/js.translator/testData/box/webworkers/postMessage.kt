// TARGET_BACKEND: JS_IR

import kotlin.worker.*

fun sleep(millis: Int) {
    val date = js("Date").now()
    var current = js("Date").now()
    do {
        current = js("Date").now()
    } while (current - date < millis)
}

fun box(): String {
    var res: String? = null
    val worker = worker {
        postMessage("OK")
    }
    worker.onmessage {
        res = it as String
    }
    sleep(100)
    return res!!
}
