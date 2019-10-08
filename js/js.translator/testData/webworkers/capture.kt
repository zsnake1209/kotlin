// KJS_WITH_FULL_RUNTIME
// IGNORE_BACKENDa: JS_IR

import kotlin.js.worker.*
import kotlin.js.Promise

fun box(): Promise<String> {
    val res = "OK"
    return worker<Unit, String> {
        res
    }.start()
}
