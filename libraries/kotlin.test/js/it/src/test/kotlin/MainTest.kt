import kotlin.test.*

var value = 5

class SimpleTest {

    @BeforeTest fun beforeFun() {
        value *= 2
    }

    @AfterTest fun afterFun() {
        value /= 2
    }

    @Test fun testFoo() {
        assertNotEquals(value, foo())
    }

    @Test fun testBar() {
        assertEquals(value, foo())
    }

    @Ignore @Test fun testFooWrong() {
        assertEquals(20, foo())
    }

}

@Ignore
class TestTest {
    @Test fun emptyTest() {
    }
}

actual class PlatformTest {
    @Test actual fun platformTest() {
        assertEquals("common", commonFun())
        assertEquals("js", platformFun())
    }
}

var someVar = false

interface TestyInterface {
    @Test fun someVarTest() = assertTrue { someVar }
}

abstract class AbstractTest : TestyInterface {
    @Test abstract fun abstractTest()

    @Test fun someTest() {
        assertTrue { true }
    }
}

interface FlipSomeVar {
    @BeforeTest @AfterTest fun flip() {
        someVar = !someVar
    }
}


class InheritedTest: AbstractTest(), FlipSomeVar {
    @Test override fun abstractTest() {
        assertTrue { someVar }
    }
}