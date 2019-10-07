// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_TYPEALIAS_PARAMETER

@Target(AnnotationTarget.TYPE)
annotation class Anno

class Foo1<T1<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>

class Foo2<
        T1,
        T2: T1<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
        > {
    fun <T1,
            T2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> > foo2() {}

    internal inner class B<T,T2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>
}

interface A<T<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>>

fun <T<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>> foo1() {}

fun <T1,
        T2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
        > T2?.foo2() {}

val <T<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>> T.bar1 get() = null

var <
        T4<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
        > T4?.bar2
    get() = null
    set(<!UNUSED_PARAMETER!>value<!>) {

    }

typealias Foo3<<!UNUSED_TYPEALIAS_PARAMETER!>T1<!>, @Anno T2<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>> = List<T2>
