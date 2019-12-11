// FIX: Replace !! with safe call and elvis

fun unsafe(string: String?) : Int {
    return string<caret>!!.length
}