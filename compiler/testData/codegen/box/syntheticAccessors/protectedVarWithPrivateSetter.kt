// FILE: test.kt
import base.*

class Derived : Base() {
    fun foo() = { x }()
}

fun box() =
        Derived().foo()

// FILE: Base.kt
package base

open class Base {
    protected var x = "OK"; private set
}