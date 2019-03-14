// IGNORE_BACKEND: JVM_IR
// FILE: inlined.kt
// COMMON_COROUTINES_TEST
// WITH_RUNTIME
// WITH_COROUTINES
// NO_CHECK_LAMBDA_INLINING
// CHECK_STATE_MACHINE

import helpers.*
import COROUTINES_PACKAGE.*

interface Factory {
    fun create(): suspend () -> Unit
}

interface Factory1 {
    fun create(): Factory
}

inline fun inlineMe(crossinline c: suspend () -> Unit) = object: Factory {
    override fun create() = suspend { c(); c() }
}

inline fun inlineMe1(crossinline c: suspend () -> Unit) = object: Factory1 {
    override fun create() = object: Factory {
        override fun create() = suspend { c(); c() }
    }
}


// FILE: inlineSite.kt
// COMMON_COROUTINES_TEST

import COROUTINES_PACKAGE.*
import helpers.*

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(CheckStateMachineContinuation)
}

fun box(): String {
    builder {
        val lambda = suspend {
            StateMachineChecker.suspendHere()
            StateMachineChecker.suspendHere()
        }
        inlineMe(lambda).create()()
    }
    StateMachineChecker.check(4)
    StateMachineChecker.reset()
    builder {
        val lambda = suspend {
            StateMachineChecker.suspendHere()
            StateMachineChecker.suspendHere()
        }
        inlineMe1(lambda).create().create()()
    }
    StateMachineChecker.check(4)
    return "OK"
}