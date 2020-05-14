/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package demos.ripple

import androidx.compose.*
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
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
            RippleDemo()
        }
    })
}

class Ripple(val x: Double, val y: Double, val t0: Double)

val n = 1000

@Composable
fun RippleDemo() {
    var t by state { 0.0 }
    var ripple by state<Ripple?> { null }

    Div { Text("Click somewhere on page") }

    fun tick(newt: Double) {
        t = newt
        val ripple0 = ripple
        if (ripple0 == null) return

        console.log(t, ripple0.t0, t - ripple0.t0)
        if (t - ripple0.t0 > n) ripple = null
        else {
            window.requestAnimationFrame {
                FrameManager.framed {
                    tick(it)
                }
            }
        }
    }

    fun addRipple(e: MouseEvent) {
        t = window.performance.now()
        ripple = Ripple(e.x, e.y, t)
        tick(t)
    }

    Div(onClick = { addRipple(it as MouseEvent) }) {
//        Text(t.toString())
    }

    ripple?.let {
        val dt = t - it.t0
        Ripple(it.x, it.y, dt / 5.0, (1-dt/n.toDouble()).toFloat())
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
fun Ripple(x: Double, y: Double, radius: Double, a: Float) {
    val composer = (currentComposer as DomComposer)
    composer.emit(
        ripple,
        { composer.document.createElement("div") },
        {
            val node = node as HTMLElement

            node.style.position = "absolute"
            node.style.background = "rgba(0,0,0,$a)"
            node.style.transform = "transform: translate3d(0);"
            node.style.asDynamic().pointerEvents = "none"

            val rx = x - radius
            val ry = y - radius

            update(rx) { node.style.left = "${rx}px" }
            update(ry) { node.style.top = "${ry}px" }
            update(radius) {
                node.style.borderRadius = "${radius}px"
                node.style.width = "${radius*2}px"
                node.style.height = "${radius*2}px"
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