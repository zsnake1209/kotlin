// FIX: Change type to 'A'
class A {
    fun unsafeCall() {}
}

fun unsafe() {
    val a: A? = A()
    a<caret>!!.unsafeCall()
}