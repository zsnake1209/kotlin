// !USE_EXPERIMENTAL: kotlin.ExperimentalStdlibApi
// IGNORE_BACKEND_FIR: JVM_IR
// TARGET_BACKEND: JVM
// WITH_RUNTIME

import kotlin.reflect.KClass
import kotlin.reflect.cast
import kotlin.reflect.safeCast
import kotlin.test.*

fun testInstance(value: Any?, klass: KClass<*>) {
    assertTrue(klass.isInstance(value))
    assertEquals(value, klass.safeCast(value))
    assertEquals(value, klass.cast(value))
}

fun testNotInstance(value: Any?, klass: KClass<*>) {
    assertFalse(klass.isInstance(value))
    assertNull(klass.safeCast(value))
    try {
        klass.cast(value)
        fail("Value should not be an instance of $klass: $value")
    }
    catch (e: Exception) { /* OK */ }
}

fun box(): String {
    testInstance(arrayOf(""), Array<String>::class)
    testInstance(arrayOf(""), Array<Any>::class)
    testNotInstance(arrayOf(Any()), Array<String>::class)

    testInstance(42, Int::class)
    testInstance(42, Int::class.javaPrimitiveType!!.kotlin)
    testInstance(42, Int::class.javaObjectType!!.kotlin)

    testNotInstance(3.14, Int::class)

    return "OK"
}
