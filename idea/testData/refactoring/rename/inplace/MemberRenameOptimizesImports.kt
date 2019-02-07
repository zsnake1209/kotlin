package foo

import foo.A

class B {
    fun <caret>x() {
        x()
    }
}

class A

// NAME: y