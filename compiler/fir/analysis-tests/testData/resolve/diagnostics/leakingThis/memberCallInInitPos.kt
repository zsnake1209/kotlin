class C {
    val p1 = "kek"
    val p2: Int
    val p3: String


    init {
        memberCall1()
        p3 = "ura"
        p2 = p3.length
    }

    fun memberCall1() {
        memberCall2()
    }

    fun memberCall2() {
        if (p3?.length > 0)
            <!POSSIBLE_LEAKING_THIS_IN_CONSTRUCTOR!>p3<!>.length
    }

}