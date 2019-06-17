// "Safe delete constructor" "false"
// ACTION: Make internal
// ACTION: Make protected
// ACTION: Make public
sealed class Gbb() {
    constructor<caret>(i: Int) : this()
}

object M : Gbb()
object B : Gbb(2)