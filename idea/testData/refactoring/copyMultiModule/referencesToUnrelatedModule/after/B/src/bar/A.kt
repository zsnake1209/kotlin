package bar

import foo.B

class A {
    val a: A = A()
    val b: foo.B = foo.B()
}