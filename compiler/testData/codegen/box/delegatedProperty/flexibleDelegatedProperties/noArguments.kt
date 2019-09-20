// !LANGUAGE: +FlexibleDelegatedPropertyConvention

class Immutable<T>(private val value: T) {
    operator fun getValue() = value
}

class Mutable<T>(private var value: T) {
    operator fun getValue() = value
    operator fun setValue(newValue: T) { value = newValue }
}

val test1 by Immutable(42)

val mut2 = Mutable(42)
val test2 by mut2

val mut3 = Mutable(42)
var test3 by mut3

fun box(): String {
    if (test1 != 42) throw AssertionError()
    
    if (test2 != 42) throw AssertionError()

    mut2.setValue(10)
    if (test2 != 10) throw AssertionError()
    
    if (test3 != 42) throw AssertionError()
    
    test3 = 1000
    if (test3 != 1000) throw AssertionError()
    if (mut3.getValue() != 1000) throw AssertionError()
    
    mut3.setValue(111)
    if (test3 != 111) throw AssertionError()
    
    return "OK"
}