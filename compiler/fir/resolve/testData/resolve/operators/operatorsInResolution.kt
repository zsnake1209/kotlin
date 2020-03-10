// ISSUE: KT-37303

fun takeDouble(x: Double) {}
fun takeInt(x: Int) {}

class Case_1 {
    val bar: Int = 1
    operator fun Double.invoke(): Double = 1.0 // (1)

    fun test_1() {
        val bar: Double = 2.0
        operator fun Int.invoke(): Int = 1  // (2)
        val res = bar() //  should resolve to (1)
        // TODO: currently broken because of KT-37375
        <!INAPPLICABLE_CANDIDATE!>takeDouble<!>(res)
    }
}

class Case_2 {
    val bar: Int = 1
    fun Double.invoke(): Double = 1.0 // (1)

    fun test_2() {
        val bar: Double = 2.0
        operator fun Int.invoke(): Int = 1  // (2)
        val res = bar() //  should resolve to (2)
        takeInt(res)
    }
}

class Case_3 {
    val bar: Int = 1
    fun Double.invoke(): Double = 1.0 // (1)

    fun test() {
        val bar: Double = 2.0
        <!INAPPLICABLE_CANDIDATE!>bar<!>() //  should resolve to (1) with INAPPLICABLE applicability
    }
}