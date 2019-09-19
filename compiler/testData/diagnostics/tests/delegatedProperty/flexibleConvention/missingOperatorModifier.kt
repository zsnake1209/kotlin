// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class Immutable<T>(private val value: T) {
    fun getValue() = value
}

class Mutable<T>(private var value: T) {
    fun getValue() = value
    fun setValue(newValue: T) { value = newValue }
}

val testValByImmutable by <!DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE!>Immutable(42)<!>

var testVarByImmutable by <!DELEGATE_SPECIAL_FUNCTION_MISSING, DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE!>Immutable(42)<!>

val testValByMutable by <!DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE!>Mutable(42)<!>

var testVarByMutable by <!DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE, DELEGATE_SPECIAL_FUNCTION_NONE_APPLICABLE!>Mutable(42)<!>
