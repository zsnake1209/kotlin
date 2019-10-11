// !LANGUAGE: +AllowContractsForCustomFunctions +UseReturnsEffect +NewInference +BoundSmartcastsInContracts
// !USE_EXPERIMENTAL: kotlin.contracts.ExperimentalContracts
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER -UNUSED_EXPRESSION
//
// ISSUE: KT-32575

import kotlin.contracts.*

fun myRequire(value: Boolean) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw IllegalArgumentException()
    }
}

class A(
    val nullableS: String?,
    val notNullS: String
)

fun test_1(a: A?) {
    myRequire(a?.nullableS != null)
    <!DEBUG_INFO_SMARTCAST!><!DEBUG_INFO_SMARTCAST!>a<!>.nullableS<!>.length
}

fun test_2(a: A?) {
    myRequire(a?.notNullS != null)
    <!DEBUG_INFO_SMARTCAST!>a<!>.notNullS.length
}

fun test_3(a: A) {
    myRequire(a.nullableS != null)
    <!DEBUG_INFO_SMARTCAST!>a.nullableS<!>.length
}

class B(val a: A?)

fun test_4(b: B?) {
    require(b?.a?.nullableS != null)
    <!DEBUG_INFO_SMARTCAST!><!DEBUG_INFO_SMARTCAST!><!DEBUG_INFO_SMARTCAST!>b<!>.a<!>.nullableS<!>.length
}