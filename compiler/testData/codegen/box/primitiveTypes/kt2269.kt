// IGNORE_BACKEND: JVM_IR
fun box() : String {
    // Just hard enough that the test won't get optimized away at compile time.
    val twoThirty = "230".toInt()
    val nine = "9".toInt()
    twoThirty?.toByte()?.hashCode()
    nine.hashCode()

    if(twoThirty.equals(nine.toByte())) {
       return "fail"
    }

    if(twoThirty == nine.toByte().toInt()) {
       return "fail"
    }
    return "OK"
}
