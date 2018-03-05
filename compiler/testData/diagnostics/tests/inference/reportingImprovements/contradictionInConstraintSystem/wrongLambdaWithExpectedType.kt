// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

fun <T> foo(l: () -> T): T = l()

fun testSimple(): Int {
    return <!TYPE_MISMATCH!>foo {
        <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: Int \(expected type for 'foo'\)
should be a supertype of: String)!>"abc"<!>
    }<!>
}

fun <T> subCall(x: T): T = x

fun testSubCall(): Int {
    return <!TYPE_MISMATCH!>foo {
        <!CONTRADICTION_IN_CONSTRAINT_SYSTEM(T; should be a subtype of: Int
should be a supertype of: String \(for parameter 'x'\)), TYPE_MISMATCH!>subCall("abc")<!>
    }<!>
}

fun testSpecialCall(): Int {
    return <!TYPE_MISMATCH!>foo {
        <!CONTRADICTION_FOR_SPECIAL_CALL(should be conformed to: Int
should be a supertype of: Int \(for parameter 'thenBranch'\), String \(for parameter 'elseBranch'\); if), TYPE_MISMATCH!>if (true) 123 else "abc"<!>
    }<!>
}
