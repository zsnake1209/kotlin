// !LANGUAGE: +InlineClasses
// WITH_RUNTIME
// FULL_JDK
// TARGET_BACKEND: JVM
import java.io.PrintWriter
import java.io.StringWriter

fun f1(): List<Result<Int>> =
    listOf(runCatching { 10 })

fun box(): String {
    val t0 = f1()

    val s0a = buildString { append(t0) }
    if (s0a != "[Success(10)]") throw AssertionError(s0a)

    val s0b = StringWriter().apply { PrintWriter(this).print(t0) }.toString()
    if (s0b != "[Success(10)]") throw AssertionError(s0b)

    val t1 = f1()[0]

    val s1a = buildString { append(t1) }
    if (s1a != "Success(10)") throw AssertionError(s1a)

    val s1b = StringWriter().apply { PrintWriter(this).print(t1) }.toString()
    if (s1b != "Success(10)") throw AssertionError(s1b)

    val t2 = runCatching { 10 }

    val s2a = buildString { append(t2) }
    if (s2a != "Success(10)") throw AssertionError(s2a)

    val s2b = StringWriter().apply { PrintWriter(this).print(t2) }.toString()
    if (s2b != "Success(10)") throw AssertionError(s2b)

    val ss = StringWriter().apply { PrintWriter(this).apply {
        println(t0)
        println(t1)
        println(t2)
    }}.toString()
    if (ss != """[Success(10)]
Success(10)
Success(10)
""") throw AssertionError(ss)

    return "OK"
}