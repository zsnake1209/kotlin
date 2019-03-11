class Outer<T1> {
    inner class Inner<T2>
}

fun testInner() = Outer<Int>().Inner<String>()