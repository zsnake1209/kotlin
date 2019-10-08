// KJS_WITH_FULL_RUNTIME
// IGNORE_BACKENDa: JS_IR

import kotlin.js.worker.*
import kotlin.js.Promise

fun box(): Promise<String> {
    val o: Promise<String> = worker<Unit, String> { "O" }.start()
    val ok: Promise<Array<Promise<String>>> = o.then { o -> arrayOf(Promise { r, _ -> r(o) }, worker<Unit, String> { "K" }.start()) }
    val resa: Promise<Promise<Array<out String>>> = ok.then { Promise.all(it) }
    val res: Promise<String> = resa.then { it.joinToString(separator = "") }
    return res
}
