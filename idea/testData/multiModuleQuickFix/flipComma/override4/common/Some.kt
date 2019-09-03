// "Flip ','" "true"
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

expect open class A() {
    open fun c(a: Int,<caret> b: String)
}

class C : A() {
    override fun c(a: Int, b: String) {}
}