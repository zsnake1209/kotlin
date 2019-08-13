// FILE: samConversionInGenericConstructorCall.kt
fun test1(f: (String) -> String) = C(f)

fun test2(x: Any) {
    x as (String) -> String
    C(x)
}

// TODO nested generic Java class.
// For some reason corresponding code fails to compile in tests (box and IR),
// but works fine in standalone project.

// FILE: J.java
public interface J<T1, T2> {
    T1 foo(T2 x);
}

// FILE: C.java
public class C<X> {
    public C(J<X, X> jxx) {}
}
