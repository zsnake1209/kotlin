/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ui

import html.StyleBuilder
import html.css

/**
 * Draws [shape] with a solid [color] behind the content.
 *
 * @sample androidx.ui.foundation.samples.DrawBackgroundColor
 *
 * @param color color to paint background with
 * @param shape desired shape of the background
 */
fun Modifier.drawBackground(
    color: Color,
    shape: Shape = RectangleShape,
    alpha: Float = 1f,
    style: DrawStyle = Fill,
//    colorFilter: ColorFilter? = null,
//    blendMode: BlendMode = CanvasScope.DefaultBlendMode
) = this + DrawBackground(
    shape,
    color
)

private data class DrawBackground internal constructor(
    private val shape: Shape,
    private val color: Color,
) : DrawModifier {
    override fun apply(styleBuilder: StyleBuilder) {
        styleBuilder.style.backgroundColor = color.css()
    }
}