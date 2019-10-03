// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_VARIABLE, -EXPERIMENTAL_IS_NOT_ENABLED

/*
 * KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)
 *
 * SECTIONS: annotations, experimental, propagation
 * NUMBER: 5
 * DESCRIPTION: Type annotations on return type with unresolved reference in parameters.
 */

// TESTCASE NUMBER: 1
@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

interface A

interface B {
    @ExperimentalAPI
    operator fun plus(x: Int) = x
}


// TESTCASE NUMBER: 1
fun case_1(x: Any): Any? {
    if (x is B) {
        if (x is A) {
            return x + 1
        }
    }

    return null
}