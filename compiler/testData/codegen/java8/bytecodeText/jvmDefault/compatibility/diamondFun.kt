// !API_VERSION: 1.3
// !JVM_DEFAULT_MODE: compatibility
// JVM_TARGET: 1.8
// WITH_RUNTIME
interface Base {
    fun value(): String
}

interface SubA : Base

interface SubB : Base {
    @JvmDefault
    override fun value(): String = "OK"
}

interface Test : SubA, SubB {}

// 1 INVOKESTATIC Test.access\$value\$jd
// 1 INVOKESPECIAL Test.value
