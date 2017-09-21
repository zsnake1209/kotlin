fun Int.foo1(p1: Int, p2: Int){}

fun foo2(p1: Any, p2: Any){}

@ReplacementFor("p.foo1(p, p)")
fun bar1(p: Int){}

@ReplacementFor("foo2(p, p)")
fun bar2(p: Int){}

fun Int.f(a: Int, b: Int) {
    1.foo1(1, 1)
    1.foo1(2, 2)
    foo1(this, this)
    (a + b).foo1(a + b, a+b)

    foo2(this, this)
}
