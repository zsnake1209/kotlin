// !DIAGNOSTICS: -UNUSED_EXPRESSION -UNUSED_PARAMETER -UNCHECKED_CAST -UNUSED_VARIABLE -UNREACHABLE_CODE -DEBUG_INFO_CONSTANT
// !LANGUAGE: +NewInference

// FILE: Test.java
public class Test {
    public static B foo() { return null; }
    public static <T> T bar() { return null; }
    public static <T> T bar2(T x) { return null; }
}

// FILE: main.kt
class A<T>(x: T)
class B

fun <T> select(vararg x: T): T = null as T

//fun case_1() {
//    val x = Test.foo() // B!
//
//    val result_1 = select(A(x), A(B()))
//    val result_2 = select(A(B()), A(x), A(if (true) B() else null))
//    val result_3 = select(A(x), A(if (true) B() else null))
//
//    <!DEBUG_INFO_EXPRESSION_TYPE("A<(B..B?)>")!>result_1<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("A<(B..B?)>")!>result_2<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("A<(B..B?)>")!>result_3<!>
//}
//
//fun case_2() {
//    val x = Test.bar<Any>() // Any!
//    val y: Any? = null
//
//    val result = select(A(Any()), A(y), A(x))
//
//    <!DEBUG_INFO_EXPRESSION_TYPE("A<(kotlin.Any..kotlin.Any?)>")!>result<!>
//}
//
//fun <T> case_3() {
//    val x = Test.bar<T>() // T!
//    val y: T? = null
//
//    val result = select(A(y), A(x), A(null as T))
//
//    <!DEBUG_INFO_EXPRESSION_TYPE("A<(T..T?)>")!>result<!>
//}
//
//fun case_4() {
//    val x = Test.bar<Nothing>() // Nothing!
//    val y = null // Nothing?
//
//    val result = select(A(x), A(y), A(return))
//
//    <!DEBUG_INFO_EXPRESSION_TYPE("A<(kotlin.Nothing..kotlin.Nothing?)>")!>result<!>
//}
//
class C<T, K, L>(x: T, y: K, z: L)
//
//fun case_5() {
//    val x = Test.foo() // B!
//    val y: B? = null
//
//    val result_1 = select(C(x, B(), 10), C(B(), x, 10))
//    val result_2 = select(C(B(), x, y), C(x, B(), y), C(y, x, B()), C(x, y, B()), C(y, B(), x), C(B(), y, x))
//
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<(B..B?), (B..B?), kotlin.Int>")!>result_1<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<(B..B?), (B..B?), (B..B?)>")!>result_2<!>
//}
//
//fun case_6() {
//    val x1 = Test.bar<C<C<A<Float>, B, Int>, B, B>>() // C<C<A<Float>, B, Int>, B, B>!
//    val x2 = C(null as C<A<Float?>, B?, Int>?, null as B?, B()) // C<C<A<Float?>, B?, Int>?, B?, B>
//    val x3 = C(C(A(Test.bar2(1f)), B(), Test.bar2(1)), Test.bar2(B()), B()) // C<C<A<Float!>, B, Int!>, B!, B>
//    val x4 = C(C(A(null as Float?), null as B?, null as Int?), null as B?, null as B?) // C<C<A<Float?>?, B?, Int?>, B?, B?>
//    val x5 = C(Test.bar2(C(A(1f), Test.bar2(B()), 1)), B(), Test.bar2(B())) // C<C<A<Float>, B!, Int>!, B, B!>
//    val x6 = Test.bar2(C(select(C(A(Test.bar2(1f)), null as B?, null as Int?), null), Test.bar2(B()), null as B?)) // C<C<A<Float!>, B?, Int?>?, B!, B?>!
//    val x7 = C(C(Test.bar2(A(1f)), B(), 1), B(), B()) // C<C<A<Float>!, B, Int>, B, B>
//    val x8 = C(C(null as A<Float?>?, B(), null as Int?), null as B?, B()) // C<C<A<Float?>?, B, Int?>, B?, B>
//    val x9 = null as C<C<A<Float>, B?, Int>, B, B>? // C<C<A<Float>, B?, Int>, B, B>?
//
//    val result_1 = select(x1, x2, x3, x4, x5, x6, x7, x8, x9)
//    val result_2 = select(x9, x8, x7, x6, x5, x4, x3, x2, x1)
//    val result_3 = select(x5, x7, x9, x3, x1, x2, x8, x4, x6)
//    val result_4 = select(x3, x1, x9, x7, x5)
//    val result_5 = select(x6, x2, x4, x8, x6)
//
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<out C<out A<(kotlin.Float..kotlin.Float?)>?, (B..B?), (kotlin.Int..kotlin.Int?)>?, (B..B?), (B..B?)>?")!>result_1<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<out C<out A<(kotlin.Float..kotlin.Float?)>?, (B..B?), (kotlin.Int..kotlin.Int?)>?, (B..B?), (B..B?)>?")!>result_2<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<out C<out A<(kotlin.Float..kotlin.Float?)>?, (B..B?), (kotlin.Int..kotlin.Int?)>?, (B..B?), (B..B?)>?")!>result_3<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<out (C<(A<kotlin.Float>..A<kotlin.Float>?), (B..B?), (kotlin.Int..kotlin.Int?)>..C<(A<kotlin.Float>..A<kotlin.Float>?), (B..B?), (kotlin.Int..kotlin.Int?)>?), (B..B?), (B..B?)>?")!>result_4<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("(C<out C<out A<(kotlin.Float..kotlin.Float?)>?, out B?, out kotlin.Int?>?, (B..B?), out B?>..C<out C<out A<(kotlin.Float..kotlin.Float?)>?, out B?, out kotlin.Int?>?, (B..B?), out B?>?)")!>result_5<!>
//}

fun case_7() {
    val x1 = C(A(Test.bar2(1)), B(), B())
    val x2 = C(Test.bar2(A(1)), B(), B())
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<A<(kotlin.Int..kotlin.Int?)>, B, B>")!>x1<!>
//    <!DEBUG_INFO_EXPRESSION_TYPE("C<(A<kotlin.Int>..A<kotlin.Int>?), B, B>")!>x2<!>

    val result_5 = select(x1, x2)
    <!DEBUG_INFO_EXPRESSION_TYPE("C<(A<kotlin.Int>..A<kotlin.Int>?), B, B>")!>result_5<!>
}