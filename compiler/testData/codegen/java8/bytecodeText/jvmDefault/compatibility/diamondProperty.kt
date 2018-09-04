// !API_VERSION: 1.3
// !JVM_DEFAULT_MODE: compatibility
// JVM_TARGET: 1.8
// WITH_RUNTIME

interface Base {
    var test: String
}

interface SubA : Base

interface SubB : Base {
    @JvmDefault
    override var test: String
        get() = "OK"
        set(value) {}
}

interface Test : SubA, SubB {}

// 1 INVOKESTATIC Test.access\$getTest\$jd
// 1 INVOKESTATIC Test.access\$setTest\$jd

// 1 INVOKESPECIAL Test.getTest
// 1 INVOKESPECIAL Test.setTest
