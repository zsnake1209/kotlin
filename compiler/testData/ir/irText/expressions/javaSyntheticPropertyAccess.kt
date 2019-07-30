// FILE: javaSyntheticPropertyAccess.kt
class K : J()

fun test(j: J, k: K) {
    j.foo = k.foo
    j.foo++
    j.foo += 1
    k.foo = j.foo
    k.foo++
    k.foo += 1
}

// FILE: J.java
public class J {
    private int foo = 42;

    public int getFoo() { return foo; }
    public void setFoo(int x) {}
}