// IGNORE_BACKEND: JVM_IR
// KOTLIN_CONFIGURATION_FLAGS: ASSERTIONS_MODE=jvm

inline fun inlineMe() = assert(true)

class A {
    fun inlineSite() {
        inlineMe()
    }
}

// 1 GETSTATIC A.\$assertionsDisabled
