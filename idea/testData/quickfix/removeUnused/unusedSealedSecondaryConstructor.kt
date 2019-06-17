// "Safe delete constructor" "true"
sealed class Gbb() {
    constructor<caret>(i: Int) : this()
}

object M : Gbb()