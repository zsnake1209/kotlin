// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_VARIABLE, -EXPERIMENTAL_IS_NOT_ENABLED

/*
 * KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)
 *
 * SECTIONS: annotations, experimental, propagation
 * NUMBER: 3
 * DESCRIPTION: Type annotations on return type with unresolved reference in parameters.
 */

// TESTCASE NUMBER: 1
@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

class A {
    @ExperimentalAPI
    operator fun plus(x: B) = this
    @ExperimentalAPI
    operator fun minus(x: B) = this
}

class B {
    operator fun plus(x: A) = this
    operator fun minus(x: A) = this
}

fun foo(x: A, y: B) {
    var a = x
    var b = y
    a <!EXPERIMENTAL_API_USAGE!>+=<!> y
    b -= x
    val c = a <!EXPERIMENTAL_API_USAGE!>+<!> y
    val d = b - x
}
