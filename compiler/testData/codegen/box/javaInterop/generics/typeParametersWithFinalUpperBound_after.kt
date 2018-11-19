// !LANGUAGE: +ProperGenericSignatureWithPrimitiveUpperBounds
// IGNORE_BACKEND: JS, JS_IR, NATIVE
// WITH_RUNTIME
// FILE: Foo.kt
data class G<T>(val x: T)

fun <T : Int> fooInt(x: T) = x
fun <T : String> fooStr(x: T) = x
fun <T : Number> fooNum(x: T) = x
fun <T : Int> fooG(g: G<T>) = g
fun <T : Int> fooGInt(g: G<T>, x: T) = G(g.x + x)
fun <T : Int> fooArrInt(arr: Array<T>, x: T) = arr
fun <X : Y, Y : Int> fooXY(x: X, y: Y) = x
fun <X : Y, Y : Int> fooGXGY(gx: G<X>, gy: G<Y>) = G(gx.x + gy.x)
fun <T : Int> fooListInt(xs: List<T>, x: T) = xs

fun box(): String {
    if (Bar.barInt(42) != 42) throw AssertionError()
    if (Bar.barStr("abc") != "abc") throw AssertionError()
    if (Bar.barNum(3.14) != 3.14) throw AssertionError()
    if (Bar.genericBarG(G(10)) != G(10)) throw AssertionError()
    if (Bar.barGInt(G(10), 1) != G(11)) throw AssertionError()
    if (Bar.barArrInt(arrayOf(1), 2).toList() != listOf(1)) throw AssertionError()
    if (Bar.barXY(1, 2) != 1) throw AssertionError()
    if (Bar.barGXGY(G(10), G(1)) != G(11)) throw AssertionError()
    if (Bar.barListInt(listOf(1, 2, 3), 4) != listOf(1, 2, 3)) throw AssertionError()

    return "OK"
}

// FILE: Bar.java
import java.util.*;

public class Bar {
    public static int barInt(int x) {
        return FooKt.<Integer>fooInt(x);
    }

    public static String barStr(String x) {
        return FooKt.<String>fooStr(x);
    }

    public static Number barNum(Number x) {
        return FooKt.<Number>fooNum(x);
    }

    public static <T extends Integer> G<T> genericBarG(G<T> gx) {
        return FooKt.<T>fooG(gx);
    }

    public static G<Integer> barGInt(G<Integer> gx, Integer x) {
        return FooKt.<Integer>fooGInt(gx, x);
    }

    public static Integer[] barArrInt(Integer[] xs, Integer x) {
        return FooKt.<Integer>fooArrInt(xs, x);
    }

    public static Integer barXY(Integer x, Integer y) {
        return FooKt.<Integer, Integer>fooXY(x, y);
    }

    public static G<Integer> barGXGY(G<Integer> gx, G<Integer> gy) {
        return FooKt.<Integer, Integer>fooGXGY(gx, gy);
    }

    public static List<Integer> barListInt(List<Integer> xs, int x) {
        return FooKt.<Integer>fooListInt(xs, x);
    }
}
