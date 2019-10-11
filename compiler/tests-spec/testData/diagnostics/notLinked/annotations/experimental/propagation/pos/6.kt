// !DIAGNOSTICS: -UNUSED_VARIABLE, -EXPERIMENTAL_IS_NOT_ENABLED


/*
 * KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)
 *
 * SECTIONS: annotations, experimental, propagation
 * NUMBER: 4
 * DESCRIPTION: Type annotations on return type with unresolved reference in parameters.
 */

import kotlin.reflect.KProperty

// TESTCASE NUMBER: 1
@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

interface A {
    @ExperimentalAPI
    operator fun getValue(x: Nothing?, y: KProperty<*>): Int
}

object B : A {
    override operator fun <!EXPERIMENTAL_OVERRIDE!>getValue<!>(x: Nothing?, y: KProperty<*>): Int = 10
}

object C : A {
    override operator fun <!EXPERIMENTAL_OVERRIDE!>getValue<!>(x: Nothing?, y: KProperty<*>): Int = 10
}

// TESTCASE NUMBER: 1
fun case_1() {
    val x by <!EXPERIMENTAL_API_USAGE!>if (true) B else C<!>
}
