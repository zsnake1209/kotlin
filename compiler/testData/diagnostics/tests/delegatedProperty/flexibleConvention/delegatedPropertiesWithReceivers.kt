// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class NthElement(private val index: Int) {

    operator fun getValue(array: IntArray) = array[index]

    operator fun setValue(array: IntArray, newValue: Int) {
        array[index] = newValue
    }
}

var IntArray.firstElement by NthElement(0)