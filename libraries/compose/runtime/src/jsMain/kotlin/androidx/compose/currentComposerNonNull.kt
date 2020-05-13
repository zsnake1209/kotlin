package androidx.compose

import org.w3c.dom.Node
import kotlin.browser.window

internal actual fun createRecomposer(): Recomposer = object : Recomposer() {
    var timer: Int = 0

    override fun hasPendingChanges(): Boolean = timer != 0

    override fun scheduleChangesDispatch() {
        if (timer == 0) timer = window.setTimeout({
            dispatchRecomposes()
            timer = 0
        }, 0)
    }
}

internal actual fun createComposer(
    root: Any,
    context: Context,
    recomposer: Recomposer
): Composer<*> = TODO()//HtmlComposer(root, recomposer)

internal actual val currentComposerNonNull
    get() = currentComposer ?: emptyComposition()

private fun emptyComposition(): Nothing =
    error("Composition requires an active composition context")

//val composer get() = ViewComposition(currentComposerNonNull as ViewComposer)

internal actual var currentComposer: Composer<*>? = null
    private set

actual fun <T> Composer<*>.runWithCurrent(block: () -> T): T {
    val prev = currentComposer
    try {
        currentComposer = this
        return block()
    } finally {
        currentComposer = prev
    }
}