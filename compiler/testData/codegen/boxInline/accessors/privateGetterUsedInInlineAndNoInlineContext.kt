// NO_CHECK_LAMBDA_INLINING
// FILE:  1.kt
package test

class Foo {

    fun test(): String {
        //will work if comment lambda body
        return {
            privateVal
        }()
    }


    internal inline fun foo(): String {
        return privateVal
    }

    private val privateVal: String
        get() = "OK"
}

// FILE: 2.kt

import test.Foo

fun box(): String {
    if (Foo().test() != "OK") return "fail 1"

    return Foo().foo()
}
