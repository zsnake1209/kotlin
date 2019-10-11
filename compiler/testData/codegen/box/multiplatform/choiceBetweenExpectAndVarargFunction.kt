// !LANGUAGE: +MultiPlatformProjects
// TARGET_BACKEND: JVM
// WITH_RUNTIME
// Issue:
// FILE: common.kt

@file:JvmMultifileClass
@file:JvmName("Test")
package test

@Experimental(Experimental.Level.WARNING)
@Target(AnnotationTarget.FUNCTION)
annotation class ExperimentalAPI

@ExperimentalAPI
expect fun foo(): Any

fun foo(vararg x: String) = 1

/*
 * Must be resolved to `expect fun foo()`, but actually to `foo(vararg x: String)`,
 * so there isn't a warning about experimental usage
 */
fun bar() = foo()

// FILE: jvm.kt

@file:JvmMultifileClass
@file:JvmName("Test")
package test

@ExperimentalAPI
actual fun foo(): Any = 2

fun box(): String {
    return if (bar() == 2/* must be 1 */) "OK" else "ERROR"
}
