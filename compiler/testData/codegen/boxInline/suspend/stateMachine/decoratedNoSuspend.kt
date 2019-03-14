// IGNORE_BACKEND: JVM_IR
// FILE: inlined.kt
// COMMON_COROUTINES_TEST
// WITH_RUNTIME
// WITH_COROUTINES
// NO_CHECK_LAMBDA_INLINING
// CHECK_STATE_MACHINE

import helpers.*
import COROUTINES_PACKAGE.*

fun decorate(actualWork: suspend () -> Unit) = workDecorator(actualWork)

inline fun workDecorator(crossinline work: suspend () -> Unit) = suspend {
    work()
    work()
}

// FILE: inlineSite.kt
// COMMON_COROUTINES_TEST

import COROUTINES_PACKAGE.*
import helpers.*

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(CheckStateMachineContinuation)
}

fun box(): String {
    val decoratedWork = decorate {
        StateMachineChecker.suspendHere()
        StateMachineChecker.suspendHere()
    }
    builder {
        decoratedWork()
    }
    StateMachineChecker.check(4)
    return "OK"
}