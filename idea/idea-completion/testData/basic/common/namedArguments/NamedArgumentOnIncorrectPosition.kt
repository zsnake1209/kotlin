fun foo(paramFirst: Int, paramSecond: Long) {}

fun usage(longParam: Long) {
    foo(paramSecond = 10, <caret>)
}

// LANGUAGE_VERSION: 1.4
// ABSENT: longParam
// EXIST: { itemText: "paramFirst =" }
// NOTHING_ELSE