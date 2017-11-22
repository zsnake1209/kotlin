// !WITH_NEW_INFERENCE
// !CHECK_TYPE
interface Tr<T> {
    var v: Tr<T>
}

fun test(t: Tr<*>) {
    <!OI;SETTER_PROJECTED_OUT!>t.<!NI;SETTER_PROJECTED_OUT!>v<!><!> = t
    t.v checkType { _<Tr<*>>() }
}