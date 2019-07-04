package sample

interface A<T : A<T>> {
    fun foo(): T
}

interface B : A<B>

fun test(b: B) {
    b.foo()
    b.foo().foo()
}
