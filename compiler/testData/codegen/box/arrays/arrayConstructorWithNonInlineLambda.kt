// WITH_RUNTIME

import kotlin.test.assertEquals


val size = 10

fun box(): String {

    val intArray = IntArray(size)

    val array = Array(size) { i -> { intArray[i]++ } }

    for (i in intArray) {
        assertEquals(0, i)
    }

    for (a in array) {
        a()
    }

    for (i in intArray) {
        assertEquals(1, i)
    }

//    val ia = simpleIntArray()
//    assertEquals(0, ia[0])
//    assertEquals(1, ia[1])
//    assertEquals(2, ia[2])
//
//    val da = simpleDoubleArray()
//    assertEquals(0.1, da[0])
//    assertEquals(1.1, da[1])
//    assertEquals(2.1, da[2])
//
//    val sa = simpleStringArray()
//    assertEquals("0", sa[0])
//    assertEquals("1", sa[1])
//    assertEquals("2", sa[2])

    return "OK"
}
