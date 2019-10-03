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

class A {
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
    operator fun plus(x: B<Int>): T = x as T
}

// TESTCASE NUMBER: 1
fun case_1(x: A) = ((x.run { this.let { it } })) + y

// TESTCASE NUMBER: 2
fun case_2(x: A) = (when (x) {
        is A -> x
        else -> x as A
    }) + y

// TESTCASE NUMBER: 3
fun case_3(x: Any?) = (when (x) {
        is C -> x
        else -> x as B
    }) + y

// TESTCASE NUMBER: 4
fun case_4(x: Any?) = (when (x) {
        is C -> x
        else -> x as? B
    })!! + y

// TESTCASE NUMBER: 5
fun case_5(x: A<B<*>>?, y: B<Int>) = x?.run { a }!! + y
