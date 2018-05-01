// !WITH_NEW_INFERENCE
// !LANGUAGE: +NewInference
// FILE: Runnable.java
public interface Runnable {
    void run();
}

// FILE: 1.kt
interface K {
    fun foo1(r: Runnable)
    fun foo2(r1: Runnable, r2: Runnable)
}
fun test(k: K, r: Runnable) {
    k.foo1(r)
    k.foo1(<!NI;TYPE_MISMATCH!>{}<!>)

    k.foo2(r, r)
    k.foo2(<!NI;TYPE_MISMATCH!>{}<!>, <!NI;TYPE_MISMATCH!>{}<!>)
    k.foo2(r, <!NI;TYPE_MISMATCH!>{}<!>)
    k.foo2(<!NI;TYPE_MISMATCH!>{}<!>, r)
}