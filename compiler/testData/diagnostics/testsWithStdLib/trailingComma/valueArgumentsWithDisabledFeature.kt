// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_VARIABLE, -UNUSED_ANONYMOUS_PARAMETER

@Target(AnnotationTarget.TYPE)
annotation class Anno1(val x: IntArray)

@Target(AnnotationTarget.TYPEALIAS)
annotation class Anno2(val x: DoubleArray)

fun foo1(vararg x: Any) {}
fun foo2(x: (Any, Any) -> Unit) {}
fun foo3(x: Any, y: () -> Unit) {}

open class A1(vararg x: Any) {
    operator fun get(x: Any, y: Any) = 10
}

open class A2(x: Int, y: () -> Unit) {}

class B(): A1({}<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {

}

@Anno2(
    [
        0.4,
        .1<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
    ]
)
typealias A3 = B

fun main1() {
    foo1(1, 2, 3<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)
    foo1({}<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)
    foo3(10<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {}

    val x1 = A1(1, 2, 3<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)
    val y1 = A1({}<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)
    val z1 = A2(10<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) {}

    foo2({ x, y -> kotlin.Unit }<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)

    val foo = listOf(
        println(1),
        "foo bar something"<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
        )

    val x2 = x1[
            1,
            2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
    ]

    val x3 = x1[{},{}<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>]

    val x4: @Anno1([
                      1, 2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
                  ]) Float = 0f

    foo1(object {}<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)
    foo1(fun () {}<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)
    foo1(if (true) 1 else 2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)

    <!UNREACHABLE_CODE!>foo1(<!>return<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!><!UNREACHABLE_CODE!>)<!>
}

fun main2(x: A1) {
    <!UNREACHABLE_CODE!>val x1 =<!> x[object {}, return<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> ]
    <!UNREACHABLE_CODE!>val x2 = x[fun () {}, throw Exception()<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> ]<!>
}
