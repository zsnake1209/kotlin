// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER, -UNUSED_ANONYMOUS_PARAMETER

fun <T> foo(x: T, l: (T) -> Unit) {}

fun testWrongParameterTypeOfLambda() {
    foo(
        "",
        <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: Byte?
should be a supertype of: String \(for parameter 'x'\))!>{ x: Byte? -> }<!>
    )
}

// -------

fun <T : Number> fooReturn(x: T, l: () -> T) {}

fun myTest() {
    fooReturn(1) {
        val someExpr = ""
        <!CONTRADICTION_FOR_SPECIAL_CALL(should be conformed to: Number
should be a supertype of: String \(for parameter 'thenBranch'\), Int \(for parameter 'elseBranch'\); if), TYPE_MISMATCH!>if (true) someExpr else 2<!>
    }
}


fun testLambdaLastExpression() {
    fooReturn(1) {
        val longLongLambda = ""
        <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: Number \(declared upper bound T\)
should be a supertype of: Int \(for parameter 'x'\), String)!>longLongLambda<!>
    }

    fooReturn(<!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: Number \(declared upper bound T\)
should be a supertype of: String \(for parameter 'x'\))!>""<!>) {
    val long = 3
    long
}
}

// -------

fun <T : Number> onlyLambda(x: () -> T) {}

fun testOnlyLambda() {
    onlyLambda {
        val longLong = 123
        <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: Number \(declared upper bound T\)
should be a supertype of: String)!>longLong.toString()<!>
    }
}

fun testLambdaWithReturnIfExpression(): Int {
    return <!TYPE_MISMATCH, TYPE_MISMATCH!>onlyLambda {
        if (3 > 2) {
            return@onlyLambda <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: Number \(declared upper bound T\)
should be a supertype of: String)!>"not a number"<!>
        }
        if (3 < 2) {
            <!RETURN_NOT_ALLOWED!>return<!> <!TYPE_MISMATCH!>"also not an int"<!>
        }
        <!CONTRADICTION_FOR_SPECIAL_CALL(should be conformed to: Number
should be a supertype of: String \(for parameter 'thenBranch'\), Int \(for parameter 'elseBranch'\); if)!>if (true) "" else 123<!>
    }<!>
}