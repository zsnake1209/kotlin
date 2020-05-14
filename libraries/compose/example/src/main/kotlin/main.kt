/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

import androidx.compose.*
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
        window.setInterval(
            {
                counter++
                composer.compose {
                    HelloWorld("Ivan ${counter / 2}", counter)
                }
                composer.applyChanges()
            },
            1000
        )
    })
}

var counter = 0

@Composable
fun HelloWorld(name: String, i: Int) {
    repeat(i) {
        println("rendering")
        Span {
            Text("Hello, ")
            Text("world $name!")
        }
    }
}
