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

/**
 * Creates a scoped drawing environment with the provided [Canvas]. This provides a
 * declarative, stateless API to draw shapes and paths without requiring
 * consumers to maintain underlying [Canvas] state information.
 * The bounds for drawing within [CanvasScope] are provided by the call to
 * [CanvasScope.draw] and are always bound to the local translation. That is the left and
 * top coordinates are always the origin and the right and bottom coordinates are always the
 * specified width and height respectively. Drawing content is not clipped,
 * so it is possible to draw outside of the specified bounds.
 *
 * @sample androidx.ui.graphics.samples.canvasScopeSample
 */
open class CanvasScope
/**
 * Represents how the shapes should be drawn within a [CanvasScope]
 */
sealed class DrawStyle

/**
 * Default [DrawStyle] indicating shapes should be drawn completely filled in with the
 * provided color or pattern
 */
object Fill : DrawStyle()

/**
 * [DrawStyle] that provides information for drawing content with a stroke
 */
data class Stroke(
    /**
     * Configure the width of the stroke in pixels
     */
    val width: Float = 0.0f,

    /**
     * Set the paint's stroke miter value. This is used to control the behavior of miter
     * joins when the joins angle si sharp. This value must be >= 0.
     */
    val miter: Float = 4.0f,

//    /**
//     * Return the paint's Cap, controlling how the start and end of stroked
//     * lines and paths are treated. The default is [StrokeCap.butt]
//     */
//    val cap: StrokeCap = StrokeCap.butt,
//
//    /**
//     * Set's the treatment where lines and curve segments join on a stroked path.
//     * The default is [StrokeJoin.miter]
//     */
//    val join: StrokeJoin = StrokeJoin.miter,
//
//    /**
//     * Effect to apply to the stroke, null indicates a solid stroke line is to be drawn
//     */
//    val pathEffect: PathEffect? = null
) : DrawStyle()