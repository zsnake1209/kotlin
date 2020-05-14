import androidx.compose.Composable
import androidx.compose.DomComposer
import androidx.compose.Recomposer
import html.Div
import html.Text
import html._composer
import ui.*
import kotlin.browser.document
import kotlin.browser.window

fun main() {
    window.addEventListener("load", {
        val recomposer = object : Recomposer() {
            override fun hasPendingChanges(): Boolean = false
            override fun recomposeSync() = Unit
            override fun scheduleChangesDispatch() = Unit
        }
        val composer = DomComposer(document, document.body!!, recomposer)
//        window.setInterval(
//            {
        composer.compose {
            render()
        }
        composer.applyChanges()
//            },
//            1000
//        )
    })
}

var counter = 0

fun DomComposer.render() {
    _composer = this
    println("frame")
    counter++

    val name = "Ivan ${counter / 2}"
    call(
        100,
        { changed(name) },
        { HelloWorld() }
    )
}

@Composable
fun HelloWorld() {
    Div(
        Modifier.padding(5.dp)
            .drawBackground(Color.Red)
            .padding(5.dp)
    ) {
        Text("Hello, world!")
    }
}