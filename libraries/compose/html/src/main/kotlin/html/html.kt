package androidx.compose.html

import androidx.compose.Composable
import androidx.compose.DomComposer
import androidx.compose.SourceLocation
import androidx.compose.currentComposer

private inline val composer get() = currentComposer as DomComposer


private val element = SourceLocation("element")
private val text = SourceLocation("text")

@Composable
fun Element(localName: String, block: @Composable () -> Unit) {
    with(composer) {
        emit(
            element,
            { document.createElement(localName) },
            {},
            { block() }
        )
    }
}

@Composable
fun Text(textContent: String) {
    with(composer) {
        emit(
            text,
            { composer.document.createTextNode(textContent) },
            {
                update(textContent) { this@update.textContent = it }
            }
        )
    }
}

fun Div(block: DomComposer.() -> Unit) {
    Element("div") {

    }
}