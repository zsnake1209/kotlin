// !WITH_NEW_INFERENCE
// !LANGUAGE: +NewInference
// FILE: J.java
public interface J<T> {
    public void foo(T r1, T r2);
}

// FILE: Runnable.java
public interface Runnable {
    void run();
}

// FILE: 1.kt
fun test(j: J<Runnable>, r: Runnable) {
    j.foo(r, r)
    j.foo(r, <!NI;TYPE_MISMATCH!>{}<!>)
    j.foo(<!NI;TYPE_MISMATCH!>{}<!>, r)
    j.foo(<!NI;TYPE_MISMATCH!>{}<!>, <!NI;TYPE_MISMATCH!>{}<!>)
}