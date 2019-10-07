// !DIAGNOSTICS: -UNUSED_VARIABLE, -UNUSED_TYPEALIAS_PARAMETER, -CAST_NEVER_SUCCEEDS

class Foo1<T1> {}

interface Foo2<T1<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>

fun <T1, T2, T3>foo3() {}

typealias Foo4<T1,T2,T3,T4> = Int

class Foo5<T, K: T<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>: Foo2<K<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>

fun <T> foo() {
    val x1 = Foo1<Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>()
    val x2: Foo2<Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>? = null
    val x3 = foo3<
            Int,
            String,
            Float<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
            >()
    val x4: Foo4<Comparable<Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>, Iterable<Comparable<Float<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>><!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>, Double, T<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
            >? = null as Foo4<Comparable<Int<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>, Iterable<Comparable<Float<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>><!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>, Double, T<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>
    val x5: (Float<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) -> Unit = {}
    val x6: Pair<(Float, Comparable<T<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>><!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) -> Unit, (Float<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) -> Unit<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>? = null
}

