// !DUMP_CFG
class D {
    val p1 = memberCall1()
    val p2: String

    init {
        p2 = "dsadsa"
    }

    private fun memberCall1(): String {
        return memberCall2()
    }

    private fun memberCall2(): String {
        if ( <!POSSIBLE_LEAKING_THIS_IN_CONSTRUCTOR!>p2<!>.length != 0){
            return p2 + "sadsa"
        }  else {
            return "empty"
        }
    }

}
