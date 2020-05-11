// !DUMP_CFG

class A {
    val p1 = "asa"
    val p2: String

    init {
        inLineCatch { p1.length }
        p2 = "dsadsa"
//        notInline { p1.length }
    }

    private inline fun inLineCatch(f: () -> Int){
        <!POSSIBLE_LEAKING_THIS_IN_CONSTRUCTOR!>p2<!>.length
        f()
    }
    // todo
    private fun notInline(f: () -> Int){
        memberCall1()
        f()
        memberCall1()
    }

    private fun memberCall1(){}

}
