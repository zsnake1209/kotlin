// !LANGUAGE: +NewInference

// FILE: Test2.java
public class Test2<T> {
    public static B foo() { return null; }
}

// FILE: main.kt
class A<T>(x: T)
class B

fun bar(x: Boolean) {
    val z: MutableMap<String, A<in B?>> = mutableMapOf()
    val y = Test2.foo()

    val r2 = when (x) {
        true -> A(y)
        false -> A(B())
    } // A<B>

    <!DEBUG_INFO_EXPRESSION_TYPE("")!>r2<!>
}