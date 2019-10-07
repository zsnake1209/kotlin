// !DIAGNOSTICS: -UNUSED_VARIABLE, -UNUSED_DESTRUCTURED_PARAMETER_ENTRY, -UNUSED_ANONYMOUS_PARAMETER

data class Foo1(val x: String, val y: String, val z: String = "")

fun main() {
    val (x1,y1,) = Pair(1,2)
    val (x2, y2: Number,) = Pair(1,2)
    val (x3,y3,z3<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) = Foo1("", ""<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> )
    val (x4,y4: CharSequence<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) = Foo1("", "", ""<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>)

    val x5: (Pair<Int, Int>, Int) -> Unit = { (x,y<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>),z<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> }
    val x6: (Foo1, Int) -> Any = { (x,y,z: CharSequence<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>), z1: Number<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> 1 }

    for ((i, j<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) in listOf(Pair(1,2))) {}
    for ((i: Any<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>) in listOf(Pair(1,2))) {}
}
