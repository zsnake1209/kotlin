import androidx.compose.*
import kotlin.browser.document
import kotlin.browser.window

//@Composable
//fun App() {
//    Div {
//        Text("Hello, world!")
//    }
//}

fun main() {
    window.addEventListener("load", {
        val recomposer = object : Recomposer() {
            override fun hasPendingChanges(): Boolean = false
            override fun recomposeSync() = Unit
            override fun scheduleChangesDispatch() = Unit
        }
        val composer = DomComposer(document, document.body!!, recomposer)
        window.setInterval(
            {
                composer.compose {
                    render()
                }
                composer.applyChanges()
            },
            1000
        )
    })
}

var counter = 0

fun DomComposer.render() {
    println("frame")
    counter++

    val name = "Ivan ${counter / 2}"
    call(
        100,
        { changed(name) },
        { helloWorld(name) }
    )
}

fun DomComposer.helloWorld(name: String) {
    println("rendering")
    span {
        text("Hello, ")
        text("world $name!")
    }
}