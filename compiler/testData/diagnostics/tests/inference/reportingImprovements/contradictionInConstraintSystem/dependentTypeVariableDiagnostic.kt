// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

fun <T : S, S : Number> foo(x: T, y: S) {}

fun test1(i: Int) {
    foo(i, <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(S; fun <T : S, S : Number> foo\(x: T, y: S\): Unit
should be a subtype of: Number \(declared upper bound S\)
should be a supertype of: Int \(for parameter 'x'\), String \(for parameter 'y'\)
)!>""<!>)
}

fun test2(i: Int) {
    foo(<!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; fun <T : S, S : Number> foo\(x: T, y: S\): Unit
should be a subtype of: Number \(declared upper bound S\)
should be a supertype of: String \(for parameter 'x'\)
), TYPE_MISMATCH!>""<!>, i)
}

class Inv<T>
fun <T : S, S : K, K> bar(x: T, y: S, z: Inv<K>) {}

fun test3(inv: Inv<Double>) {
    bar("", "", <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(K; fun <T : S, S : K, K> bar\(x: T, y: S, z: Inv<K>\): Unit
should be a subtype of: Double \(for parameter 'z'\)
should be a supertype of: String \(for parameter 'x'\)
), TYPE_MISMATCH, TYPE_MISMATCH, TYPE_MISMATCH!>inv<!>)
}

fun <T : S, S> bar(x: Inv<T>, y: Inv<S>) {}

fun test4(a: Inv<Int>, b: Inv<String>) {
    bar(a, <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(S; fun <T : S, S> bar\(x: Inv<T>, y: Inv<S>\): Unit
should be a subtype of: String \(for parameter 'y'\)
should be a supertype of: Int \(for parameter 'x'\)
), TYPE_MISMATCH, TYPE_MISMATCH!>b<!>)
}