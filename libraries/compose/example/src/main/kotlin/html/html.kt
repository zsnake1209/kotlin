package html

import androidx.compose.Composable
import androidx.compose.DomComposer
import androidx.compose.SourceLocation
import androidx.compose.currentComposer
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event

val div = SourceLocation("div")

@Composable
fun Div(onClick: ((Event) -> Unit)? = null, block: @Composable () -> Unit) {
    val composer = (currentComposer as DomComposer)
    composer.emit(
        div,
        {
            composer.document.createElement("div").also {
                if (onClick != null) it.addEventListener("click", onClick)
                with((it as HTMLElement).style) {
                    position = "absolute"
                    width = "500px"
                    height = "500px"
                    border ="1px solid black"
                    asDynamic().overflow = "hidden"
                }
            }
        },
        {

        },
        { block() }
    )
}

val text = SourceLocation("text")

@Composable
fun Text(value: String) {
    val composer = (currentComposer as DomComposer)
    composer.emit(
        text,
        { composer.document.createTextNode(value) },
        { update(value) { textContent = it } }
    )
}