// WITH_RUNTIME

import kotlin.test.*

class MyClass

inline fun <T> runLogged(action: () -> T): T = action()

operator fun MyClass.provideDelegate(host: Any?, p: Any): String = ""
operator fun String.getValue(receiver: Any?, p: Any): String = ""

val testO by runLogged { MyClass() }

fun box(): String {
    return "OK"
}
