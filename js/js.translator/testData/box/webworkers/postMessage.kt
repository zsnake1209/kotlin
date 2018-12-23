// TARGET_BACKEND: JS_IR

import kotlin.worker.*

fun sleep(millis: Int) {
    val date: Int = js("Date").now()
    var current: Int = js("Date").now()
    do {
        current = js("Date").now()
    } while (current - date < millis)
}

fun box(): String {
    var res = "FAIL"
    val worker = worker {
        postMessage("OK")
    }
    worker.onmessage {
        res = it as String
    }
    worker.postMessage("Ping")
    sleep(100)
    return res
}
