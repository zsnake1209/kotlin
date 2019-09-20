// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class NthElement(private val index: Int) {

    operator fun getValue(array: IntArray) = array[index]

    operator fun setValue(array: IntArray, newValue: Int) {
        array[index] = newValue
    }
}

var IntArray.firstElement by NthElement(0)

fun box(): String {
    val array = IntArray(4) { it }

    if (array.firstElement != 0) throw AssertionError()

    array.firstElement = 42
    if (array.firstElement != 42) throw AssertionError()

    return "OK"
}