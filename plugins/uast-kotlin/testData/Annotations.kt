package test.pkg

annotation class TestType

class IntDefTest {
    fun test() {
        wantInt(100)
    }

    private fun wantInt(@TestType input: Int) {}
}
