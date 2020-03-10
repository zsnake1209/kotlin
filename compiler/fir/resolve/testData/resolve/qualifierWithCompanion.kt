package my

class A {
    companion object X {
        fun foo() {}
    }
}

val xx = A()

fun test() {
    val x = A
    A.foo()
    A.X.foo()

    operator fun A.invoke() {}

    my.xx()
}