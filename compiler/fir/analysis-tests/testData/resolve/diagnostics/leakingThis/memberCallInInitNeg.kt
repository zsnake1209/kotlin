// PROBLEM: none

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
        memberCall3()
    }

    fun memberCall3() {
        p3.length
    }

}