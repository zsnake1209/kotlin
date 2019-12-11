// PROBLEM: none

class A{
    val s: String?
    init{
        s ="ssss"
    }
    fun validateS(){
        val i = s<caret>!!.length
    }
}
