// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_TYPEALIAS_PARAMETER
// !LANGUAGE: +TrailingCommas

@Target(AnnotationTarget.TYPE)
annotation class Anno

class Foo1<T1,>

class Foo2<
        T1,
        T2: T1,
        > {
    fun <T1,
            T2, > foo2() {}

    internal inner class B<T,T2,>
}

interface A<T,>

fun <T,> foo1() {}

inline fun <reified T1, T2, reified T3,> foo2() {}

fun <T1,
        T2,
        > T2?.foo3() {}

val <T,> T.bar1 get() = null

var <
        T4,
        > T4?.bar2
    get() = null
    set(<!UNUSED_PARAMETER!>value<!>) {

    }

typealias Foo3<<!UNUSED_TYPEALIAS_PARAMETER!>T1<!>, @Anno T2,> = List<T2>
