// "Flip ','" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

actual open class A {
    actual open fun c(a: Int, b: String) {}
}

class B : A() {
    override fun c(a: Int,<caret> b: String) {}
}