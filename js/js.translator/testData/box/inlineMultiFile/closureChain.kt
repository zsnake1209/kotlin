<<<<<<< ed89b51be63b1723371a8a64e0b9053d286cd40e
// IGNORE_BACKEND: JS_IR
// EXPECTED_REACHABLE_NODES: 1285
=======
// EXPECTED_REACHABLE_NODES: 1113
>>>>>>> unmute
/*
 * Copy of JVM-backend test
 * Found at: compiler/testData/codegen/boxInline/complex/closureChain.1.kt
 */

// FILE: foo.kt
package foo

fun test1(): Int {
    val inlineX = Inline()
    return inlineX.foo({ z: Int -> "" + z}, 25, { -> this.length })
}

fun box(): String {
    if (test1() != 2) return "test1: ${test1()}"

    return "OK"
}


// FILE: bar.kt
package foo

class Inline() {

    inline fun foo(closure1 : (l: Int) -> String, param: Int, closure2: String.() -> Int) : Int {
        return closure1(param).closure2()
    }
}
