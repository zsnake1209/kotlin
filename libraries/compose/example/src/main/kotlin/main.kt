/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import androidx.compose.Composable
import androidx.compose.getValue
import androidx.compose.setValue
import androidx.compose.state
import demos.ripple.RippleDemo
import demos.tictactoe.Button
import demos.tictactoe.Div
import demos.tictactoe.Text
import demos.tictactoe.TicTacToeGame
import html.onLoad
import html.setContent
import kotlin.browser.document

fun main() {
    onLoad {
        document.body?.setContent {
            Demos()
        }
    }
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
        Div { Text("Compose for Web Demos:") }

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