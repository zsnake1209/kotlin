// !DIAGNOSTICS: -UNUSED_PARAMETER -UNUSED_EXPRESSION -UNUSED_VARIABLE

class Inv<T>(val x: T?)

fun <K> create(y: K) = Inv(y)
fun <K> createPrivate(y: K) = Inv(y)

fun takeInvInt(i: Inv<Int>) {}

fun <S> test(i: Int, s: S) {
    val a = Inv(s)

    <!DEBUG_INFO_EXPRESSION_TYPE("Inv<S>")!>a<!>

    val b = create(i)

    <!DEBUG_INFO_EXPRESSION_TYPE("Inv<kotlin.Int>")!>b<!>

    val c = createPrivate(i)

    <!DEBUG_INFO_EXPRESSION_TYPE("Inv<kotlin.Int>")!>c<!>

    takeInvInt(create(i))
}

// -----

class Foo<T>(x: T)
class Bar<S>
class OutBar<out S>
class InBar<in S>

fun <K> foo0(x: K?, y: Bar<K>) {}
fun <K> foo1(x: K?, y: Foo<Bar<K>>) {}
fun <K, T: K> foo2(x: K?, y: Foo<Bar<T>>) {}
fun <T, K: T> foo3(x: K?, y: Foo<Bar<T>>) {}
fun <K> foo4(x: K?, y: Foo<Bar<out K>>) {}
fun <K> foo5(x: K?, y: Bar<in K>) {}
fun <K> foo6(x: K?, y: OutBar<K>) {}
fun <K> foo7(x: K?, y: InBar<K>) {}
fun <T, K: T, S: K, M: S> foo8(x: T?, y: Foo<Bar<M>>) {}
fun <T, K: T, S: K, M: S> foo9(x: M?, y: Foo<Bar<T>>) {}
fun <T: J, K: T, S: K, M: S, J: L, L> foo10(x: L?, y: Foo<Bar<T>>, z: Bar<M>) {}
fun <T: J, K: T, S: K, M: S, J: L, L> foo11(x: M?, y: Foo<Bar<T>>, z: Bar<L>) {}
fun <K: Any> foo12(x: K?, y: Bar<K>) {}

class Foo13<T>(x: T) {
    fun <K: T> foo1(x: T?, y: Bar<K>) {}
    fun <K: T> foo2(x: K?, y: Bar<T>) {}
}

fun <K> foo14(x: K?, y: Bar<K>) where K: Comparable<K>, K: CharSequence {}
fun <K: T?, T> foo15(x: T, y: Bar<K>) {}
fun <K: T?, T> foo16(x: K, y: Bar<T>) {}

fun <L> main(x: L?, y: L) {
    // invariant
    val x00 = foo0(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    val x01 = foo0(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)

    // nested invariant
    val x10 = foo1(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)
    val x11 = foo1(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)

    // definitely not null type in arguemnts
    if (x != null && y != null) {
        val x12 = foo1(<!DEBUG_INFO_EXPRESSION_TYPE("L!! & L?")!>x<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<out Bar<L>>")!>Foo(Bar())<!>)
        val x13 = foo1(<!DEBUG_INFO_EXPRESSION_TYPE("L & L!!")!>y<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<out Bar<L>>")!>Foo(Bar())<!>)
    }

    // with dependent type parameter
    val x20 = foo2(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)
    val x21 = foo2(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)

    // with inversely dependent type parameter
    val x30 = foo3(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)
    val x31 = foo3(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)

    // use site convariant (OK in OI)
    val x40 = foo4(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<out L!!>>")!>Foo(<!TYPE_MISMATCH("Bar<out L!!>", "Bar<L>"), TYPE_MISMATCH("Bar<out L!!>", "Bar<L>")!>Bar()<!>)<!>)
    val x41 = foo4(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<out L!!>>")!>Foo(<!TYPE_MISMATCH("Bar<out L!!>", "Bar<L>"), TYPE_MISMATCH("Bar<out L!!>", "Bar<L>")!>Bar()<!>)<!>)

    // use site contravariant
    val x50 = foo5(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    val x51 = foo5(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)

    // declaration site convariant
    val x60 = foo6(x, <!DEBUG_INFO_EXPRESSION_TYPE("OutBar<L>")!>OutBar()<!>)
    val x61 = foo6(y, <!DEBUG_INFO_EXPRESSION_TYPE("OutBar<L>")!>OutBar()<!>)

    // declaration site contravariant
    val x70 = foo7(x, <!DEBUG_INFO_EXPRESSION_TYPE("InBar<L>")!>InBar()<!>)
    val x71 = foo7(y, <!DEBUG_INFO_EXPRESSION_TYPE("InBar<L>")!>InBar()<!>)

    // with deeply dependent type parameter
    val x80 = foo8(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)
    val x81 = foo8(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)

    // with deeply and inversely dependent type parameter
    val x90 = foo9(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)
    val x91 = foo9(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>)

    // with complex deeply dependent type parameters
    val x100 = foo10(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    val x101 = foo10(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)

    // with complex deeply and inversely dependent type parameters
    val x110 = foo11(x, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    val x111 = foo11(y, <!DEBUG_INFO_EXPRESSION_TYPE("Foo<Bar<L>>")!>Foo(Bar())<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)

    // definitely not null type in arguemnts and not-null upper bound (it doens't work in OI)
    if (x != null && y != null) {
        val x120 = foo12(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
        val x121 = foo12(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    }

    // not-null upper bound (it doens't work in OI, and worked before the fix)
    val x122 = foo12(<!TYPE_MISMATCH!>x<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>"), TYPE_MISMATCH, TYPE_MISMATCH!>Bar()<!>)
    val x123 = foo12(<!TYPE_MISMATCH!>y<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>"), TYPE_MISMATCH, TYPE_MISMATCH!>Bar()<!>)

    // with type parameter through class
    val x132 = Foo13(x).foo1(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L?>")!>Bar()<!>)
    val x133 = Foo13(x).foo2(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L?>")!>Bar()<!>)
    val x134 = Foo13(y).foo1(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    val x135 = Foo13(y).foo2(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    if (x != null) {
        val x136 = Foo13(<!DEBUG_INFO_SMARTCAST!>x<!>).foo2(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
        val x137 = Foo13(y).foo2(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    }
    if (y != null) {
        val x138 = Foo13(x).foo2(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L?>")!>Bar()<!>)
        val x139 = Foo13(<!DEBUG_INFO_SMARTCAST!>y<!>).foo2(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    }

    // simple type
    val x140 = foo14("y", <!DEBUG_INFO_EXPRESSION_TYPE("Bar<kotlin.String>")!>Bar()<!>)
    val x141 = foo14("x", <!DEBUG_INFO_EXPRESSION_TYPE("Bar<kotlin.String>")!>Bar()<!>)

    // upper bound as nullable another type parameter
    val x151 = foo15(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L?>")!>Bar()<!>)
    val x152 = foo15(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L?>")!>Bar()<!>)
    if (x != null && y != null) {
        val x153 = foo15(<!DEBUG_INFO_SMARTCAST!>x<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L?>")!>Bar()<!>)
        val x154 = foo15(<!DEBUG_INFO_SMARTCAST!>y<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L?>")!>Bar()<!>)
    }

    // upper bound as nullable another type parameter (inverse dependency)
    val x161 = foo16(x, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    val x162 = foo16(y, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    if (x != null && y != null) {
        val x163 = foo16(<!DEBUG_INFO_SMARTCAST!>x<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
        val x164 = foo16(<!DEBUG_INFO_SMARTCAST!>y<!>, <!DEBUG_INFO_EXPRESSION_TYPE("Bar<L>")!>Bar()<!>)
    }
}
