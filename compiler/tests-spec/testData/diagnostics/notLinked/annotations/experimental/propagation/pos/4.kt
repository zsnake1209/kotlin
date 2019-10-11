// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_VARIABLE, -EXPERIMENTAL_IS_NOT_ENABLED

/*
 * KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)
 *
 * SECTIONS: annotations, experimental, propagation
 * NUMBER: 4
 * DESCRIPTION: Type annotations on return type with unresolved reference in parameters.
 */

// TESTCASE NUMBER: 1,2,3,4
@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

open class A {
    @ExperimentalAPI
    operator fun plus(x: A) = x
    @ExperimentalAPI
    operator fun minus(x: A) = x
}

class B: A()
class C: A()

open class D<out T>(val a: T)

open class E<T> {
    @ExperimentalAPI
    operator fun plus(x: B): T = x <!UNCHECKED_CAST!>as T<!>
}

// TESTCASE NUMBER: 1
fun case_1(x: A) = ((x.run { this.let { it } })) <!EXPERIMENTAL_API_USAGE!>+<!> B()

// TESTCASE NUMBER: 2
fun case_2(x: Any, y: C) = (when (x) {
        is A -> <!DEBUG_INFO_SMARTCAST!>x<!>
        else -> x as A
    }) <!EXPERIMENTAL_API_USAGE!>+<!> y

// TESTCASE NUMBER: 3
fun case_3(x: Any?, y: A) = (when (x) {
        is C -> <!DEBUG_INFO_SMARTCAST!>x<!>
        else -> x as B
    }) <!EXPERIMENTAL_API_USAGE!>+<!> y

// TESTCASE NUMBER: 4
fun case_4(x: Any?, y: C) = (when (x) {
        is C -> <!DEBUG_INFO_SMARTCAST!>x<!>
        else -> x as? B
    })!! <!EXPERIMENTAL_API_USAGE!>+<!> y

// TESTCASE NUMBER: 5
fun case_5(x: A?, y: B) = x?.run { this }!! <!EXPERIMENTAL_API_USAGE!>+<!> y
