// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_ANONYMOUS_PARAMETER, -UNUSED_VARIABLE
// !LANGUAGE: +TrailingCommas

class Foo1(x: Int = 10, y: Float = 0f,)

class Foo2(
    val x: Int = 10,
    var y: Float,
) {
    constructor(
        x: Float,
        y: Int = 10,
        ): this(1, 1f) {

    }
}

enum class Foo3(x: Int, )

fun foo4(x: Int, y: Comparable<Float>,) {}

fun foo5(x: Int = 10,) {}

fun foo6(vararg x: Int,) {}

fun foo7(y: Float, vararg x: Int,) {}

val foo8: (Int, Int,) -> Int = fun(
    x,
    y,
    ): Int {
    return x + y
}

val foo9: (Int, Int, Int,) -> Int =
    fun (x, y: Int, z,): Int {
        return x + y
    }

fun main() {
    val x1 = {
            x: Comparable<Comparable<Number>>,
            y: Iterable<Iterable<Number>>,
        ->
        println("1")
    }
    val x2 = { x: Comparable<Comparable<Number>>,
        -> println("1")
    }
    val x3: ((Int,) -> Int) -> Unit = { x: (Int,) -> Int, -> println("1") }
    val x4: ((Int,) -> Int) -> Unit = { x, -> println("1") }
}
