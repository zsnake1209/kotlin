package nonCapturedVariables

fun main() {
    val a = 5

    inlineBlock {
        //Breakpoint!
        val b = 6

        inlineBlock {
            //Breakpoint!
            val c = 7

            block {
                //Breakpoint!
                val d = 8

                inlineBlock {
                    //Breakpoint!
                    val e = 9

                    block {
                        //Breakpoint!
                        val f = 10

                        block {
                            //Breakpoint!
                            val g = 11

                            //Breakpoint!
                            val g2 = 12

                            //Breakpoint!
                            val g3 = 13

                            //Breakpoint!
                            val g4 = 14
                        }
                    }
                }
            }
        }
    }
}

private fun block(block: () -> Unit) {
    block()
}

private inline fun inlineBlock(block: () -> Unit) {
    block()
}

// EXPRESSION: a
// RESULT: 5: I

// EXPRESSION: b
// RESULT: 6: I

// EXPRESSION: c
// RESULT: 7: I

// EXPRESSION: d
// RESULT: 8: I

// EXPRESSION: e
// RESULT: 9: I

// EXPRESSION: f
// RESULT: 10: I

// EXPRESSION: e
// RESULT: 9: I

// EXPRESSION: d
// RESULT: 8: I

// EXPRESSION: c
// RESULT: 7: I