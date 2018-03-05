// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

class Inv1<K>

fun <T : Any> foo1(receiver: Inv1<T>) {}
fun <K> test(c: Inv1<K>) {
    foo1(<!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; fun <T : Any> foo1\(receiver: Inv1<T>\): Unit
should be a subtype of: Any \(declared upper bound T\)
should be equal to: K \(for parameter 'receiver'\)
)!>c<!>)
}

class Inv2<T, K>
fun <T : Any, K : Any> foo2(a: Inv2<T, K>) {}
fun <S, V> test(c: Inv2<S, V>) {
    foo2(<!CONTRADICTION_IN_CONSTRAINT_SYSTEM(K; fun <T : Any, K : Any> foo2\(a: Inv2<T, K>\): Unit
should be a subtype of: Any \(declared upper bound K\)
should be equal to: V \(for parameter 'a'\)
), TYPE_MISMATCH!>c<!>)
}


fun <K> subCallNullableUpperBound(): Inv1<K> = TODO()
fun <K : Any> subCallNullable(): Inv1<K?> = TODO()

fun <S> test() {
    foo1(<!CONTRADICTION_IN_CONSTRAINT_SYSTEM(K; fun <K> subCallNullableUpperBound\(\): Inv1<K>
should be a subtype of: Any \(for parameter 'receiver'\)
should be equal to: S
), TYPE_MISMATCH!>subCallNullableUpperBound<S>()<!>)
    foo1(<!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; fun <T : Any> foo1\(receiver: Inv1<T>\): Unit
should be a subtype of: Any \(declared upper bound T\)
should be equal to: S? \(for parameter 'receiver'\)
)!>subCallNullable<S>()<!>)
}
