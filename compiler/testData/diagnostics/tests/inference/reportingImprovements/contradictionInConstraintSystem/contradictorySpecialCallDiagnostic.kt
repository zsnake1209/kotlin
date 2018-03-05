// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_PARAMETER

fun <T : Number> foo(x: T, l: () -> T) {}

fun testIf(i: Int) {
    foo(i) {
        <!CONTRADICTION_FOR_SPECIAL_CALL(should be conformed to: Number
should be a supertype of: String \(for parameter 'thenBranch'\), Int \(for parameter 'elseBranch'\); if), TYPE_MISMATCH!>if (true) "" else i<!>
    }
}

fun testElvis(i: Int, s: String?) {
    foo(i) {
        <!CONTRADICTION_FOR_SPECIAL_CALL(should be conformed to: Number
should be a supertype of: String \(for parameter 'left'\), Int \(for parameter 'right'\); elvis), TYPE_MISMATCH!>s ?: i<!>
    }
}

fun testWhen(i: Int, s: String?) {
    foo(i) {
        <!CONTRADICTION_FOR_SPECIAL_CALL(should be conformed to: Number
should be a supertype of: String? \(for parameter 'entry0'\), Int \(for parameter 'entry1'\); when), TYPE_MISMATCH!>when (true) {
            true -> s
            else -> i
        }<!>
    }
}

val test: Int = <!CONTRADICTION_FOR_SPECIAL_CALL(should be conformed to: Int \(expected type for '<given candidates>'\)
should be a supertype of: Nothing? \(for parameter 'thenBranch'\), Int \(for parameter 'thenBranch'\); if), TYPE_MISMATCH, TYPE_MISMATCH!>if (true) {
    when (2) {
        1 -> 1
        else -> null
    }
} else {
    2
}<!>
