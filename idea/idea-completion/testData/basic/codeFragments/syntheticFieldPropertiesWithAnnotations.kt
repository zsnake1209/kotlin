class Foo {
    val foo: Int = 3
        get() = field + 1
    val bar
        get() = 4
    @JvmField
    val baz = "lorem"

    fun foo() {
        <caret>
    }
}

// INVOCATION_COUNT: 1
// EXIST: foo
// EXIST: foo_field
// EXIST: bar
// ABSENT: bar_field
// EXIST: baz
// EXIST: baz_field