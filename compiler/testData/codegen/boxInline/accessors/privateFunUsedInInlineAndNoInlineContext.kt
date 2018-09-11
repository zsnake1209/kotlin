// NO_CHECK_LAMBDA_INLINING
// FILE:  1.kt
package test

class Foo {

    fun test(): String {
        //will work if comment lambda body
        return {
            privateFun()
        }()
    }


    internal inline fun foo(): String {
        return privateFun()
    }

    private fun privateFun(): String = "OK"
}

// FILE: 2.kt

import test.Foo

fun box(): String {
    if (Foo().test() != "OK") return "fail 1"

    return Foo().foo()
}
