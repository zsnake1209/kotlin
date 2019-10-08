// KJS_WITH_FULL_RUNTIME
// IGNORE_BACKENDa: JS_IR

import kotlin.js.worker.*
import kotlin.js.Promise

fun Char.asDynamic(): dynamic = this

fun box(): Promise<String> {
    val w = worker { a: dynamic ->
        // TODO: `a` is Object { _value_0: 97 }, but 'a': Char is Char { _value_0: 97 }
        if (a == 'a'.asDynamic()) return@worker "1"
        if (a == 'b'.asDynamic()) return@worker "b"
        "OK"
    }
    return w.send('a'.asDynamic()).then { r ->
        Promise.all(arrayOf(w.send('b'.asDynamic()), Promise { a, b -> a(r) }))
    }.then { (b, r) ->
        Promise.all(arrayOf(w.send(' '.asDynamic()), Promise { a, c -> a(b + r) }))
    }.then { (a, b) ->
        if (a + b != "OKb1") "FAIL: " + a + b
        else "OK"
    }
}
