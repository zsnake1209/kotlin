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
        val recomposer = object : Recomposer() {
            override fun hasPendingChanges(): Boolean = false
            override fun recomposeSync() = Unit
            override fun scheduleChangesDispatch() = Unit
        }

        val composition = compositionFor(document, recomposer, null) { st, r ->
            DomComposer(document, document.body!!, st, r)
        }

        window.setInterval(
            {
                println("tick")
                counter++
                composition.setContent {
                    HelloWorld("Ivan ${counter / 2}", counter % 3)
                }
            },
            1000
        )
    })
}

@Composable
fun HelloWorld(name: String, i: Int) {
    repeat(i) {
        Span {
            Text("Hello 123, ")
            Text("world $name!")
        }
    }
}
