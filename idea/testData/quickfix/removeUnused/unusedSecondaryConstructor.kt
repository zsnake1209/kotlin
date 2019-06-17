// "Safe delete constructor" "false"
// ACTION: Make internal
// ACTION: Make protected
// ACTION: Make public
private enum class Check() {
    DOO, MOOD(1);

    private constr<caret>uctor(i: Int) : this()
}