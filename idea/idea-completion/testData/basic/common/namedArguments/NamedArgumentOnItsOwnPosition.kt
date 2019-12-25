fun foo(paramFirst: Int, paramSecond: Int, paramThird: Long) {}

fun usage(longParam: Long) {
    foo(paramFirst = 10, 20, <caret>)
}

// LANGUAGE_VERSION: 1.4
// EXIST: longParam
// EXIST: { itemText: "paramThird =" }
// ABSENT: { itemText: "paramFirst =" }
// ABSENT: { itemText: "paramSecond =" }
