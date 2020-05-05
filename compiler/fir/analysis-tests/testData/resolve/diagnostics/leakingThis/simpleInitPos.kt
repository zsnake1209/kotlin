// !DUMP_CFG

class A {
    val p1 = "kek"
    val p2: Int
    val p3: String

    init {
        p1.length
        <!POSSIBLE_LEAKING_THIS_IN_CONSTRUCTOR!>p3<!>.length
        p2 = <!POSSIBLE_LEAKING_THIS_IN_CONSTRUCTOR!>p3<!>.length
        p3 = String("asa")
    }
}
