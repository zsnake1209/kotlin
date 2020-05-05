// PROBLEM: none
// !DUMP_CFG

class A {
    val p1 = "kek"
    val p2: Int
    val p3: String

    init {
        val localP1 = 1
        p1.length
        p3 = String("asa")
        p2 = p3.length
    }
}
