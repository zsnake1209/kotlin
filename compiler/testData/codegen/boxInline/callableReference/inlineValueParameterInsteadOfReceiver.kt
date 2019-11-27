// FILE: 1.kt
class Z

class Q {
    inline fun f(z: Z) = "OK"
}

// FILE: 2.kt
fun box(): String {
    return Z().run(Q()::f)
}
