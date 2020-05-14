/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import androidx.compose.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.browser.document
import kotlin.browser.window

var counter = 0

fun main() {
    window.addEventListener("load", {
        val recomposer = JSRecomposer()

        val composition = compositionFor(document, recomposer, null) { st, r ->
            DomComposer(document, document.body!!, st, r)
        }

        FrameManager.ensureStarted()
        composition.setContent {
            HelloWorld("world")
        }
    })
}

@Composable
fun HelloWorld(name: String) {
    var t by state { 0 }

    fun tick() {
        t++
        window.requestAnimationFrame { tick() }
    }

    tick()

    Div {
        Ripple(100, 100, t/100, 0.5f)
    }
}


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
                    width = "100%"
                    height = "100%"
                }
            }
        },
        {

        },
        { block() }
    )
}

val ripple = SourceLocation("Ripple")

@Composable
fun Ripple(x: Int, y: Int, radius: Int, a: Float) {
    val composer = (currentComposer as DomComposer)
    composer.emit(
        ripple,
        { composer.document.createElement("div") },
        {
            val node = node as HTMLElement
            node.style.background = "rgba(0,0,0,$a)"

            update(x) { node.style.left = "${x}px" }
            update(y) { node.style.top = "${x}px" }
            update(radius) {
                node.style.borderRadius = "${radius}px"
                node.style.width = "${radius}px"
                node.style.height = "${radius}px"
            }
        }
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