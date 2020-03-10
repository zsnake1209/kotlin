fun fooInt(p: Int) = p
fun fooLong(p: Long) = p
fun fooByte(p: Byte) = p
fun fooShort(p: Short) = p

fun test() {
    fooInt(1 <!INAPPLICABLE_CANDIDATE!>plus<!> 1)
    fooByte(1 <!INAPPLICABLE_CANDIDATE!>plus<!> 1)
    fooLong(1 <!INAPPLICABLE_CANDIDATE!>plus<!> 1)
    fooShort(1 <!INAPPLICABLE_CANDIDATE!>plus<!> 1)

    fooInt(1 <!INAPPLICABLE_CANDIDATE!>times<!> 1)
    fooByte(1 <!INAPPLICABLE_CANDIDATE!>times<!> 1)
    fooLong(1 <!INAPPLICABLE_CANDIDATE!>times<!> 1)
    fooShort(1 <!INAPPLICABLE_CANDIDATE!>times<!> 1)

    fooInt(1 <!INAPPLICABLE_CANDIDATE!>div<!> 1)
    fooByte(1 <!INAPPLICABLE_CANDIDATE!>div<!> 1)
    fooLong(1 <!INAPPLICABLE_CANDIDATE!>div<!> 1)
    fooShort(1 <!INAPPLICABLE_CANDIDATE!>div<!> 1)

    fooInt(1 <!INAPPLICABLE_CANDIDATE!>rem<!> 1)
    fooByte(1 <!INAPPLICABLE_CANDIDATE!>rem<!> 1)
    fooLong(1 <!INAPPLICABLE_CANDIDATE!>rem<!> 1)
    fooShort(1 <!INAPPLICABLE_CANDIDATE!>rem<!> 1)
}