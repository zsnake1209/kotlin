// !DIAGNOSTICS: -UNUSED_VARIABLE, -UNUSED_EXPRESSION -NAME_SHADOWING

fun foo1(x: Any) = when (x) {
    Comparable::class,
    Iterable::class,
    String::class<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
        -> println(1)
    else -> println(3)
}

fun foo2(x: Any) {
    val z = when (val y: Int = x as Int) {
        1<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> println(1)
        else -> println(3)
    }
}

fun foo3(x: (Any) -> Any) {
    val z = when (val y: (Any) -> Any = x) {
        {x: Any<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> x}, {y: Any<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> y}<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> println(1)
        else -> println(3)
    }
}

fun foo4(x: Any) {
    val z = when (x) {
        is Int, is Double<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> println(1)
        else -> println(3)
    }
}

fun foo5(x: Int, y: IntArray, z: IntArray) {
    val u = when (x) {
        in y,
        in z<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!>
            -> println(1)
        else -> println(3)
    }
}

fun foo6(x: Boolean?) = when (x) {
    true, false<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> println(1)
    null<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> -> println(1)
}
