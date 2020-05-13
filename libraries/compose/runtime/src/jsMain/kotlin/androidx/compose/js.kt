package androidx.compose

actual abstract class Context

internal actual fun recordSourceKeyInfo(key: Any) {
}

actual fun keySourceInfoOf(key: Any): String? {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
}

actual fun identityHashCode(instance: Any?): Int =
    instance?.hashCode() ?: 0

actual inline fun <R> synchronized(lock: Any, block: () -> R): R = block()

actual open class ThreadLocal<T> actual constructor() {
    private var value: T? = initialValue()

    actual fun get(): T? = value

    actual fun set(value: T?) {
        this.value = value
    }

    protected actual open fun initialValue(): T? = null
}

actual class WeakReference<T> actual constructor(val instance: T) {
    actual fun get(): T? = instance
}

@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR
)
actual annotation class MainThread actual constructor()

@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR
)
actual annotation class TestOnly actual constructor()

@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
actual annotation class CheckResult actual constructor(actual val suggest: String)