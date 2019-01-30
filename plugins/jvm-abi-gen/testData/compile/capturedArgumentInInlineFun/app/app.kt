package app

import lib.*

fun runAppAndReturnOk(): String {
    val value2 = lambdaIncrementer(5)(4)
    if (value2 != 9) error("value2 is expected to be '9', but got '$value2' instead")


    val value1 = anonymousIncrementer(3).apply(4)
    if (value1 != 7) error("value1 is expected to be '7', but got '$value1' instead")


    return "OK"
}
