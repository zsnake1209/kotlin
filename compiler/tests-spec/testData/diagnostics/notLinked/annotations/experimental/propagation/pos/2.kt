// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_VARIABLE, -EXPERIMENTAL_IS_NOT_ENABLED

/*
 * KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)
 *
 * SECTIONS: annotations, experimental, propagation
 * NUMBER: 2
 * DESCRIPTION: Type annotations on return type with unresolved reference in parameters.
 */

// TESTCASE NUMBER: 1
@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

class A {
    @ExperimentalAPI
    operator fun plus(x: A) = x
    @ExperimentalAPI
    operator fun minus(x: A) = x
}

// TESTCASE NUMBER: 1
fun case_1(x: A, y: A, z: Any?) {
    var a = x
    a <!EXPERIMENTAL_API_USAGE!>+=<!> y
    a <!EXPERIMENTAL_API_USAGE!>-=<!> y

    val b = x <!EXPERIMENTAL_API_USAGE!>+<!> ((y <!EXPERIMENTAL_API_USAGE!>-<!> (y <!EXPERIMENTAL_API_USAGE!>-<!> x))) <!EXPERIMENTAL_API_USAGE!>+<!> (y) <!EXPERIMENTAL_API_USAGE!>-<!> x
    val c = ((x.run { this.let { it } })) <!EXPERIMENTAL_API_USAGE!>+<!> y
    val d = (when (z) {
        is A -> <!DEBUG_INFO_SMARTCAST!>z<!>
        else -> z as A
    }) <!EXPERIMENTAL_API_USAGE!>+<!> y
}
