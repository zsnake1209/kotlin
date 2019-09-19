// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class Immutable<T>(private val value: T) {
    operator fun getValue() = value
}

class Mutable<T>(private var value: T) {
    operator fun getValue() = value
    operator fun setValue(newValue: T) { value = newValue }
}

val testValByImmutable by Immutable(42)

var testVarByImmutable by <!DELEGATE_SPECIAL_FUNCTION_MISSING!>Immutable(42)<!>

val testValByMutable by Mutable(42)

var testVarByMutable by Mutable(42)
