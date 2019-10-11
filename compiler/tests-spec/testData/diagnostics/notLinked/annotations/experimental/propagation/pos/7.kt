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

class A {
    operator fun get(x: Int, y: Int): A = A()
}

@ExperimentalAPI
operator fun A?.get(x: Int, y: Int): A {
    return A()
}

operator fun A.get(x: Int): A? {
    return A()
}

fun case_1() {
    val x = A()
    println(x[9, 1][1][1, 5])
}
