// IGNORE_BACKEND_FIR: JVM_IR
// WITH_RUNTIME

import kotlin.test.*

var log: String = ""

class MyClass(val value: String)

fun runLogged(entry: String, action: () -> String): String {
    log += entry
    return action()
}

fun runLogged2(entry: String, action: () -> MyClass): MyClass {
    log += entry
    return action()
}

operator fun MyClass.provideDelegate(host: Any?, p: Any): String =
        runLogged("tdf(${this.value});") { this.value }

operator fun String.getValue(receiver: Any?, p: Any): String =
        runLogged("get($this);") { this }


fun box(): String {
    val testK by runLogged("K;") { "K" }
    return "OK"
}
