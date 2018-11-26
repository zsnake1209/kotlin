// EXPECTED_REACHABLE_NODES: 1284
package foo

fun test(actual: DoubleArray, expect: DoubleArray): String {
    for (index in 0..(expect.size)) {
        val expectedElem = expect[index]
        val actualElem = actual[index]
        if (expectedElem != actualElem) {
            return "Content do not match: ${expect} vs. ${actual}"
        }
    }
    return "OK"
}

fun box(): String {
    val array = doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NaN, 1.0, Double.NaN, +0.0, Double.NaN, -0.0, Double.NaN, -1.0, Double.NaN, Double.NEGATIVE_INFINITY)
    array.sort()

    return test(array, doubleArrayOf(Double.NEGATIVE_INFINITY, -1.0, -0.0, +0.0, 1.0, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN))
}