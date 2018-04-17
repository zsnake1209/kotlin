class In<in I>

fun <S> select(x: S, <!UNUSED_PARAMETER!>y<!>: S): S = x

fun <T> foo(a: Array<In<T>>, b: Array<In<String>>) =
    select(a, b)[0].<!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>ofType<!>(true)


inline fun <reified K> In<K>.ofType(y: Any?) =
    y is K