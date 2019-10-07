// !DIAGNOSTICS: -UNUSED_PARAMETER

import java.util.ArrayList

fun <T> foo(a : T, b : Collection<T>, c : Int) {
}

fun <T> arrayListOf(vararg values: T): ArrayList<T> = throw Exception("$values")

val bar = foo("", arrayListOf()<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> <!NO_VALUE_FOR_PARAMETER!>)<!>
val bar2 = foo<String>("", arrayListOf()<!TRAILING_COMMA_IS_NOT_SUPPORTED_YET!>,<!> <!NO_VALUE_FOR_PARAMETER!>)<!>