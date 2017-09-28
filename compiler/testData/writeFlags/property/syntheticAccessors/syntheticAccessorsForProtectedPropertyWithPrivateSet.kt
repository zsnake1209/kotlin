// FILE: test.kt
import base.*

class Derived : Base() {
    fun foo() = { x }()
}

// FILE: Base.kt
package base

open class Base {
    protected var x = "OK"; private set
}

// TESTED_OBJECT_KIND: function
// TESTED_OBJECTS: Derived, access$setX$p
// ABSENT: TRUE

// TESTED_OBJECT_KIND: function
// TESTED_OBJECTS: Derived, access$getX$p
// FLAGS: ACC_PUBLIC, ACC_FINAL, ACC_STATIC, ACC_SYNTHETIC