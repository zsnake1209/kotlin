package sample

interface Bar<in I, out O> {
    fun foo(x: I): O
}

fun topLevel(): Int = 42