// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class Immutable<T>(private val value: T) {
    operator fun getValue() = value
}

class Mutable<T>(private var value: T) {
    operator fun getValue() = value
    operator fun setValue(newValue: T) { value = newValue }
}

val testTopLevelValByImmutable by Immutable(42)
val testTopLevelValByMutable by Mutable(42)
var testTopLevelVarByMutable by Mutable(42)

class NthElement(private val index: Int) {

    operator fun getValue(array: IntArray) = array[index]

    operator fun setValue(array: IntArray, newValue: Int) {
        array[index] = newValue
    }
}

var IntArray.topLevelExtension by NthElement(0)

fun host() {
    val testLocalValByImmutable by Immutable(42)
    val testLocalValByMutable by Mutable(42)
    var testLucalVarByMutable by Mutable(42)
}