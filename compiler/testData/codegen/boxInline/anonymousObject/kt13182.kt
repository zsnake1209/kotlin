// IGNORE_BACKEND: JVM_IR
// IGNORE_BACKEND_MULTI_MODULE: JVM_IR, JVM_IR_SERIALIZE
// FILE: 1.kt

package test

inline fun test(cond: Boolean, crossinline cif: () -> String): String {
    return if (cond) {
        { cif() }()
    }
    else {
        cif()
    }
}
// FILE: 2.kt

import test.*

fun box(): String {
    val s = "OK"
    return test(true) {
        {
            s
        }()
    }
}

