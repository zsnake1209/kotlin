import androidx.compose.*
import kotlin.browser.document
import kotlin.browser.window

@Composable
//fun App() {
//    Div {
//        Text("Hello, world!")
//    }
//}

fun main() {
    window.addEventListener("load", {
        val recomposer = object: Recomposer() {
            override fun scheduleChangesDispatch() {
            }
        }
        val composer = HtmlComposer(document, document.body!!, recomposer)
        window.setInterval({
            composer.compose {
                render()
            }
            composer.applyChanges()
        }, 1000)
    })
}

var counter = 0

fun HtmlComposition.render() {
    println("frame")
    counter++

    val name = "Ivan ${counter / 2}"
    call(
        100,
        { cc.changed(name) },
        { helloWorld(name) }
    )
}

fun HtmlComposition.helloWorld(name: String) {
    println("rendering")
    span {
        text("Hello, ")
        text("world $name!")
    }
}