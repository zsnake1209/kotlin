
interface I {
    val checkMember: Boolean
}

val I.checkExt get() = true

fun useI(i: I): Unit {}

fun test(iN: I?) {
    if (iN?.checkExt == true) {
        <!INAPPLICABLE_CANDIDATE!>useI<!>(iN) // smart-cast here
    }
}

fun test_2(iN: I?) {
    if (iN?.checkMember == true) {
        <!INAPPLICABLE_CANDIDATE!>useI<!>(iN) // smart-cast here
    }
}