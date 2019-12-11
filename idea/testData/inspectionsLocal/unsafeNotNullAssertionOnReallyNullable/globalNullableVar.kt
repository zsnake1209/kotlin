// PROBLEM: none

class A{
    fun check(){

    }
}

var global: A? = null

fun main() {
    global!!<caret>.check()
}