// IGNORE_BACKEND: JVM_IR
// FILE: inlined.kt
// COMMON_COROUTINES_TEST
// WITH_RUNTIME
// WITH_COROUTINES
// NO_CHECK_LAMBDA_INLINING
// CHECK_STATE_MACHINE

inline suspend fun crossinlineMe1(crossinline c: suspend () -> Unit) {
    c(); c()
}

inline suspend fun crossinlineMe2(crossinline c: suspend () -> Unit) {
    val l = suspend {
        c()
        c()
    }
    l()
    l()
}

// FILE: box.kt
// COMMON_COROUTINES_TEST

import COROUTINES_PACKAGE.*
import helpers.*

fun builder(c: suspend () -> Unit) {
    c.startCoroutine(CheckStateMachineContinuation)
}

fun box(): String {
    val lambda = suspend {
        StateMachineChecker.suspendHere()
        StateMachineChecker.suspendHere()
    }
    builder {
        crossinlineMe1(lambda)
    }
    StateMachineChecker.check(4)
    StateMachineChecker.reset()
    builder {
        crossinlineMe2(lambda)
    }
    StateMachineChecker.check(8)
    return "OK"
}
