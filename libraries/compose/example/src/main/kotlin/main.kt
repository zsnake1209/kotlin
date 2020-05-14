/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import androidx.compose.*
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
    var x by state { 1 }

    Span(onClick = { x++ }) {
        Text("Hello ${x}, ")
        Text("world $name!")
    }
}
