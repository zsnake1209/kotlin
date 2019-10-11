// !LANGUAGE: +AllowContractsForCustomFunctions +UseReturnsEffect +NewPermissionsForContractsDeclaration
// !USE_EXPERIMENTAL: kotlin.contracts.ExperimentalContracts
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE

import kotlin.contracts.*

class Foo {
    fun foo(b: Boolean) {
        contract {
            returns() implies (b)
        }
    }

    inline fun bar(block: () -> Unit) {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }
        block()
    }
}

fun test(obj: Foo, x: String?) {
    obj.foo(x != null)
    <!DEBUG_INFO_SMARTCAST!>x<!>.length
    val y: String
    obj.bar {
        y = ""
    }
    y.length
}

