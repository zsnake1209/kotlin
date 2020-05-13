package androidx.compose

import kotlin.browser.window

actual class Looper

val looper = Looper()

actual fun isMainThread(): Boolean = true

actual object LooperWrapper {
    actual fun getMainLooper(): Looper = looper
}

actual class Handler actual constructor(looper: Looper) {
    actual fun postAtFrontOfQueue(block: () -> Unit): Boolean {
        window.setTimeout({block()}, 0)
        return true
    }
}

actual interface ChoreographerFrameCallback {
    actual fun doFrame(frameTimeNanos: Long)
}

actual object Choreographer {
    actual fun postFrameCallback(callback: ChoreographerFrameCallback) {
    }

    actual fun postFrameCallbackDelayed(
        delayMillis: Long,
        callback: ChoreographerFrameCallback
    ) {
    }

    actual fun removeFrameCallback(callback: ChoreographerFrameCallback) {
    }
}

