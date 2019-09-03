// "Flip ','" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

actual open class A {
    actual open fun c(a: Int, b: String) {}
}

open class B : A() {
    override fun c(a: Int,<caret> b: String) {}
}

open class D : B() {
    override fun c(a: Int, b: String) {}
}

fun test(a: Int, b: String) {
    A().c(a, b)
}