import kotlin.reflect.KProperty

class Delegate<T>(val v: T) {
    operator fun getValue(t: Any?, p: KProperty<*>): T = v
}

class B: A<Int>(1) {}

open class A<T>(vv: T) {
    val prop: T by Delegate(vv)
}
