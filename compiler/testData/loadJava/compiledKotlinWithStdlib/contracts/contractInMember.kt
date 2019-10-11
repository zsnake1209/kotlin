// !LANGUAGE: +AllowContractsForCustomFunctions +UseReturnsEffect +NewPermissionsForContractsDeclaration
// !USE_EXPERIMENTAL: kotlin.contracts.ExperimentalContracts
// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_VARIABLE

package test

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