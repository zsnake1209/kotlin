// !WITH_NEW_INFERENCE
// !LANGUAGE: +NewInference +SamConversionForKotlinFunctions
// FILE: Runnable.java
public interface Runnable {
    void run();
}

// FILE: 1.kt
interface K<T> {
    fun foo(t1: T, t2: T)
}

fun test(k: K<Runnable>, r: Runnable) {
    k.foo(r, r)
    k.foo(r, <!NI;TYPE_MISMATCH!>{}<!>)
    k.foo(<!NI;TYPE_MISMATCH!>{}<!>, r)
    k.foo(<!NI;TYPE_MISMATCH!>{}<!>, <!NI;TYPE_MISMATCH!>{}<!>)
}