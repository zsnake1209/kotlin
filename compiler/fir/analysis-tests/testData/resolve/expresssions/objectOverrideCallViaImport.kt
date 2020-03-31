import Derived.foo

interface Base {
    fun foo() {}
}

object Derived : Base {
    fun bar() {
        foo()
    }
}

fun test() {
    // See KT-35730
    foo() // Derived.foo is more correct here
    Derived.foo()
}