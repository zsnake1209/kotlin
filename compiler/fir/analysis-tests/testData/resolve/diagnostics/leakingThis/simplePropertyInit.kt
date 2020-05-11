// !DUMP_CFG

class D {
    val p1 = memberCall1()
    val p2: String

    init {
        p2 = "dsadsa"
    }

    private fun memberCall1(): Int {
        return <!POSSIBLE_LEAKING_THIS_IN_CONSTRUCTOR!>p2<!>.length
    }
}