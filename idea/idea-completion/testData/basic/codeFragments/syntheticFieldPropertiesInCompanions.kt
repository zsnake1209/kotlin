class Foo {
    companion object {
        val foo
            get() = 1
        @JvmField
        val bar = 3
        @JvmStatic
        val baz = 3
        @JvmStatic
        val quux
            get() = 7
    }
}

<caret>

// INVOCATION_COUNT: 1
// EXIST: foo
// ABSENT: foo_field
// EXIST: bar
// EXIST: bar_field
// EXIST: baz
// EXIST: baz_field
// EXIST: quux
// ABSENT: quux_field