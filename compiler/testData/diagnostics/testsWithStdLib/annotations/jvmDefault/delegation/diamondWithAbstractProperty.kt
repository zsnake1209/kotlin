// !API_VERSION: 1.3
// !JVM_TARGET: 1.8
// !JVM_DEFAULT_MODE: enable

interface Base {
    val test: String

    val testDelegated: String
        get() = "fail"

}

interface SubA : Base

interface SubB : Base {
    @JvmDefault
    override val test: String
}

interface Test : SubA, SubB {}


class Delegate : Test {
    override val test: String
        get() = "fail"

    override val testDelegated: String
        get() = "K"
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class TestClass<!>(val foo: Test) : Test by foo

