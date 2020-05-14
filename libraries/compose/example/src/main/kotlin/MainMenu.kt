/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import androidx.compose.*
import demos.ripple.RippleDemo
import demos.tictactoe.TicTacToeGame
import demos.tictactoe.Div
import demos.tictactoe.Button
import demos.tictactoe.Text
import kotlin.browser.document
import kotlin.browser.window

fun main() {
    window.addEventListener("load", {
        val recomposer = JSRecomposer()

        val composition = compositionFor(document, recomposer, null) { st, r ->
            DomComposer(document, document.body!!, st, r)
        }

        FrameManager.ensureStarted()
        composition.setContent {
            Demos()
        }
    })
}

class Demo(val title: String, val content: @Composable () -> Unit)

val mainMenu = Demo("") {}
val allDemos: List<Demo> = listOf(
    Demo("Ripple") { RippleDemo() },
    Demo("Tic-tac-toe") { TicTacToeGame() }
)

@Composable
fun Demos() {
    var currentDemo: Demo by state { mainMenu }

    if (currentDemo == mainMenu) {
        Div { Text("Compose for Web Demos: ${currentDemo.title}") }

        for (demo in allDemos) {
            Div {
                Button(onClick = { currentDemo = demo }) {
                    Text(demo.title)
                }
            }
        }
    } else {
        Div {
            Button(onClick = { currentDemo = mainMenu }) {
                Text("Back")
            }
        }
        Div { Text("Demo: ${currentDemo.title}") }

        Div {
            currentDemo.content()
        }
    }
}