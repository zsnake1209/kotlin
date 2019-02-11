class C {
    private var x = 3

    fun f() {
        val <caret>y = 5
        x + 3
    }
}

// NAME: x