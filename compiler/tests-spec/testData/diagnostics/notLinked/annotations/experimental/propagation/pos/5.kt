// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_VARIABLE, -EXPERIMENTAL_IS_NOT_ENABLED

/*
 * KOTLIN DIAGNOSTICS NOT LINKED SPEC TEST (POSITIVE)
 *
 * SECTIONS: annotations, experimental, propagation
 * NUMBER: 5
 * DESCRIPTION: Type annotations on return type with unresolved reference in parameters.
 * UNEXPECTED BEHAVIOUR
 * ISSUES: KT-34156
 */

// TESTCASE NUMBER: 1
@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

interface A {
    operator fun plus(x: Int) = 1
}

interface B: A {
    @ExperimentalAPI
    override operator fun plus(x: Int) = 2
}

// TESTCASE NUMBER: 1
fun case_1(x: Any): Any? {
    if (x is A) {
        <!DEBUG_INFO_EXPRESSION_TYPE("A & kotlin.Any"), UNUSED_EXPRESSION!>x<!>
        if (x is B) {
            return <!DEBUG_INFO_EXPRESSION_TYPE("A & kotlin.Any"), DEBUG_INFO_SMARTCAST!>x<!> + 1
        }
    }

    return null
}
