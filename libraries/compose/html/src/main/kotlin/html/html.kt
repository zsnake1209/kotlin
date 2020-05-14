/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package html

import androidx.compose.Composable
import androidx.compose.DomComposer
import androidx.compose.SourceLocation
import androidx.compose.currentComposer
import org.w3c.dom.HTMLElement
import org.w3c.dom.css.CSSStyleDeclaration
import ui.Color
import ui.Dp
import ui.LayoutModifier
import ui.Modifier

var _composer: DomComposer? = null
private inline val composer get() = _composer!!// currentComposer as DomComposer

private val element = SourceLocation("element")
private val text = SourceLocation("text")

fun Dp.css() = "${value}px"

fun Color.css() = "rgba(${red*255},${green*255},${blue*255},${alpha})"

class StyleBuilder(val style: CSSStyleDeclaration)
interface StyleModifier {
    fun apply(styleBuilder: StyleBuilder)
}

@Composable
fun Element(localName: String, modifier: Modifier = Modifier, block: @Composable () -> Unit) {
    with(composer) {
        emit(
            element,
            { document.createElement(localName) },
            {
                val styleBuilder = StyleBuilder((node as HTMLElement).style)
                modifier.foldOut(Unit) { element, _ ->
                    when (element) {
                        is StyleModifier -> element.apply(styleBuilder)
                        else -> error("")
                    }
                }
            },
            { block() }
        )
    }
}

@Composable
fun Text(textContent: String) {
    with(composer) {
        emit(
            text,
            { composer.document.createTextNode(textContent) },
            {
                update(textContent) { this@update.textContent = it }
            }
        )
    }
}

@Composable
fun Div(modifier: Modifier = Modifier, block: @Composable () -> Unit) {
    Element( "div", modifier) {
        block()
    }
}