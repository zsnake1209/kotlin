// !API_VERSION: 1.3
// !JVM_TARGET: 1.8
// !JVM_DEFAULT_MODE: enable

interface Base {
    fun test(): String

    fun delegatedTest(): String {
        return "fail"
    }
}

interface SubA : Base

interface SubB : Base {
    @JvmDefault
    override fun test(): String
}

interface Test : SubA, SubB {
}


class Delegate : Test {
    override fun test(): String {
        return "Fail"
    }

    override fun delegatedTest(): String {
        return "K"
    }
}

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class TestClass<!>(val foo: Test) : Test by foo