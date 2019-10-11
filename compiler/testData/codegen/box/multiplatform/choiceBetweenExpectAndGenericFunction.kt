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
expect fun foo(): Int

fun <T>foo(): T = 1 as T

/*
 * Must be resolved to `expect fun foo()`, but actually to `foo(vararg x: String)`,
 * so there isn't a warning about experimental usage
 */
fun bar(): Any = foo()

// FILE: jvm.kt

@file:JvmMultifileClass
@file:JvmName("Test")
package test

@ExperimentalAPI
actual fun foo(): Int = 2

fun box(): String {
    return if (bar() == 2/* must be 1 */) "OK" else "ERROR"
}
