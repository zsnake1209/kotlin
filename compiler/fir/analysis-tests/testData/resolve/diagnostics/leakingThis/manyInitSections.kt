// !DUMP_CFG

class A {
    val p1 = "kek"
    val p2: Int
    val p3: String

    init {
        p1.length
        p2 = <!POSSIBLE_LEAKING_THIS_IN_CONSTRUCTOR!>p3<!>.length
    }

    init {
        p3 = "asa"
    }


    fun checkConst(s: String) {
        val x = s

        var y = "empty"

        if (x == "const1") {
            y = "yes"
        }
        if (x == "const2") {
            y = "no"
        }
        return y
    }
}