// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_ANONYMOUS_PARAMETER

class Foo1(x: Int = 10, y: Float = 0f<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)

class Foo2(
    val x: Int = 10,
    var y: Float<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
) {
    constructor(
        x: Float,
        y: Int = 10<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
        ): this(1, 1f) {

    }

    var x1: Int
        get() = 10
        set(value<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {

        }

    var x2: Int
        get() = 10
        set(
            x2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
        ) {}

    var x3: (Int) -> Unit
        get() = {}
        set(x2: (Int) -> Unit<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {}
}

enum class Foo3(x: Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> )

fun foo4(x: Int, y: Comparable<Float><!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {}

fun foo5(x: Int = 10<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {}

fun foo6(vararg x: Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {}

fun foo7(y: Float, vararg x: Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {}

val foo8: (Int, Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) -> Int = fun(
    x,
    y<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
    ): Int {
    return x + y
}

val foo9: (Int, Int, Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) -> Int =
    fun (x, y: Int, z<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>): Int {
        return x + y
    }