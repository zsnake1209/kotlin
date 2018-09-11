// NO_CHECK_LAMBDA_INLINING
// FILE:  1.kt
package test

class Foo {

    fun test(): String {
        //will work if comment lambda body
        return {
            privateVar = "abc"
            privateVar
        }()
    }


    internal inline fun foo(): String {
        privateVar = "OK"
        return privateVar
    }

    private var privateVar: String = "fail 0"
}

// FILE: 2.kt

import test.Foo

fun box(): String {
    if (Foo().test() != "abc") return "fail 1"

    return Foo().foo()
}
