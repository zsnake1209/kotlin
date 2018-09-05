@file:Suppress("EXPERIMENTAL_API_USAGE_ERROR")

package foo

@OptionalExpectation
expect annotation class Optional(val value: String)

expect class PlatformClass {
    val value: String
}

class CommonClass {
    @Optional("foo")
    fun commonFun() { }
}