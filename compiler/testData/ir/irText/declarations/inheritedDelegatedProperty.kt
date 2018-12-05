import kotlin.reflect.KProperty

class Delegate {
    operator fun getValue(t: Any?, p: KProperty<*>): Int = 1
}

class B: A() {}

open class A {
    val prop: Int by Delegate()
}
