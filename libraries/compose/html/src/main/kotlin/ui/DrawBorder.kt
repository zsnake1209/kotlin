/*
 * Copyright 2020 The Android Open Source Project
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

import androidx.compose.Composable
import androidx.compose.remember

/**
 * Returns a [Modifier] that adds border with appearance specified with a [border] and a [shape]
 *
 * @sample androidx.ui.foundation.samples.BorderSample()
 *
 * @param border [Border] class that specifies border appearance, such as size and color
 * @param shape shape of the border
 */
@Deprecated(
    "Use Modifier.drawBorder",
    replaceWith = ReplaceWith(
        "Modifier.drawBorder(border, shape)",
        "androidx.ui.core.Modifier",
        "androidx.ui.foundation.drawBorder",
        "androidx.ui.foundation.shape.RectangleShape",
        "androidx.ui.foundation.Border"
    )
)
@Composable
fun DrawBorder(border: Border, shape: Shape = RectangleShape): Modifier =
    Modifier.drawBorder(size = border.size, brush = border.brush, shape = shape)

/**
 * Returns a [Modifier] that adds border with appearance specified with [size], [color] and a
 * [shape]
 *
 * @sample androidx.ui.foundation.samples.BorderSampleWithDataClass()
 *
 * @param size width of the border. Use [Dp.Hairline] for a hairline border.
 * @param color color to paint the border with
 * @param shape shape of the border
 */
@Deprecated(
    "Use Modifier.drawBorder",
    replaceWith = ReplaceWith(
        "Modifier.drawBorder(size, color, shape)",
        "androidx.ui.core.Modifier",
        "androidx.ui.foundation.drawBorder",
        "androidx.ui.foundation.shape.RectangleShape"
    )
)
@Composable
fun DrawBorder(size: Dp, color: Color, shape: Shape = RectangleShape): Modifier =
    Modifier.drawBorder(size, SolidColor(color), shape)

/**
 * Returns a [Modifier] that adds border with appearance specified with [size], [brush] and a
 * [shape]
 *
 * @sample androidx.ui.foundation.samples.BorderSampleWithBrush()
 *
 * @param size width of the border. Use [Dp.Hairline] for a hairline border.
 * @param brush brush to paint the border with
 * @param shape shape of the border
 */
@Deprecated(
    "Use Modifier.drawBorder",
    replaceWith = ReplaceWith(
        "Modifier.drawBorder(size, brush, shape)",
        "androidx.ui.core.Modifier",
        "androidx.ui.foundation.drawBorder",
        "androidx.ui.foundation.shape.RectangleShape"
    )
)
@Composable
fun DrawBorder(size: Dp, brush: Brush, shape: Shape): DrawBorder =
    DrawBorder(shape, size, brush)

/**
 * Returns a [Modifier] that adds border with appearance specified with a [border] and a [shape]
 *
 * @sample androidx.ui.foundation.samples.BorderSample()
 *
 * @param border [Border] class that specifies border appearance, such as size and color
 * @param shape shape of the border
 */
fun Modifier.drawBorder(border: Border, shape: Shape = RectangleShape) =
    drawBorder(size = border.size, brush = border.brush, shape = shape)

/**
 * Returns a [Modifier] that adds border with appearance specified with [size], [color] and a
 * [shape]
 *
 * @sample androidx.ui.foundation.samples.BorderSampleWithDataClass()
 *
 * @param size width of the border. Use [Dp.Hairline] for a hairline border.
 * @param color color to paint the border with
 * @param shape shape of the border
 */
fun Modifier.drawBorder(size: Dp, color: Color, shape: Shape = RectangleShape) =
    drawBorder(size, SolidColor(color), shape)

/**
 * Returns a [Modifier] that adds border with appearance specified with [size], [brush] and a
 * [shape]
 *
 * @sample androidx.ui.foundation.samples.BorderSampleWithBrush()
 *
 * @param size width of the border. Use [Dp.Hairline] for a hairline border.
 * @param brush brush to paint the border with
 * @param shape shape of the border
 */
fun Modifier.drawBorder(size: Dp, brush: Brush, shape: Shape): Modifier =
    DrawBorder(shape, size, brush)

data class DrawBorder internal constructor(
    private val shape: Shape,
    private val borderWidth: Dp,
    private val brush: Brush
) : DrawModifier