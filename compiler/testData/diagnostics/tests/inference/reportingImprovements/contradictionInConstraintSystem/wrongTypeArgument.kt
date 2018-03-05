// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

class Inv1<K>
fun <T> foo1(a: Inv1<T>, b: T) {}

fun <S, P> test1(a: Inv1<S>, b: P) {
    foo1(a, <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be equal to: S \(for parameter 'a'\)
should be a supertype of: P \(for parameter 'b'\))!>b<!>)
}

fun <S> test2(a: Inv1<S>, b: S?) {
    foo1(a, <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be equal to: S \(for parameter 'a'\)
should be a supertype of: S? \(for parameter 'b'\))!>b<!>)
}

fun <T> foo2(a: T, b: Inv1<T>) {}

fun <S, P> test3(a: S, b: Inv1<P>) {
    foo2(a, <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: P \(for parameter 'b'\)
should be a supertype of: S \(for parameter 'a'\)), TYPE_MISMATCH!>b<!>)
}

fun <S> subCall(): S = TODO()

fun <K, S> test4() {
    foo1(subCall<Inv1<K>>(), <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(S; should be a subtype of: K \(for parameter 'b'\)
should be equal to: S), TYPE_MISMATCH, TYPE_MISMATCH!>subCall<S>()<!>)
}

class Inv2<K, V>
fun <K, V> foo3(a: Inv2<K, V>, key: K) {}

fun <T, S> test(a: Inv2<T, S>, v: S) {
    foo3(a, <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(K; should be equal to: T \(for parameter 'a'\)
should be a supertype of: S \(for parameter 'key'\))!>v<!>)
}