// !WITH_NEW_INFERENCE
/*
 * RELEVANT SPEC SENTENCES (spec version: 0.1-155, test type: pos):
 *  - expressions, when-expression -> paragraph 5 -> sentence 1
 *  - expressions, when-expression, exhaustive-when-expressions -> paragraph 1 -> sentence 1
 *  - expressions, when-expression, exhaustive-when-expressions -> paragraph 2 -> sentence 1
 *  - type-system, introduction-1 -> paragraph 5 -> sentence 2
 *  - expressions, conditional-expression -> paragraph 4 -> sentence 1
 *  - expressions, conditional-expression -> paragraph 5 -> sentence 1
 */

val test: Int = <!NI;TYPE_MISMATCH, NI;TYPE_MISMATCH!>if (true) {
    when (2) {
        1 -> 1
        else -> <!OI;NULL_FOR_NONNULL_TYPE!>null<!>
    }
}
else {
    2
}<!>