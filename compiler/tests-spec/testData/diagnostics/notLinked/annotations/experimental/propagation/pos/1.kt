// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_VARIABLE, -EXPERIMENTAL_IS_NOT_ENABLED

/*
 * KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)
 *
 * SECTIONS: annotations, experimental, propagation
 * NUMBER: 1
 * DESCRIPTION: Type annotations on return type with unresolved reference in parameters.
 */

// TESTCASE NUMBER: 1
@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

class A {
    @ExperimentalAPI
    operator fun plus(x: A) {}
    @ExperimentalAPI
    operator fun plusAssign(x: A) {}
    @ExperimentalAPI
    operator fun minus(x: A) {}
    @ExperimentalAPI
    operator fun minusAssign(x: A) {}
}

fun foo(x: A, y: A) {
    val a = x <!EXPERIMENTAL_API_USAGE!>+<!> y
    x <!EXPERIMENTAL_API_USAGE!>+=<!> y
    val b = x <!EXPERIMENTAL_API_USAGE!>-<!> y
    x <!EXPERIMENTAL_API_USAGE!>-=<!> y

    x.<!EXPERIMENTAL_API_USAGE!>plus<!>(y)
    x.<!EXPERIMENTAL_API_USAGE!>plusAssign<!>(y)
    x.<!EXPERIMENTAL_API_USAGE!>minus<!>(y)
    x.<!EXPERIMENTAL_API_USAGE!>minusAssign<!>(y)
}
