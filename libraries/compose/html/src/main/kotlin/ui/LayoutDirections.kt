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
 * [Modifier] that changes the [LayoutDirection] of the wrapped layout to [LayoutDirection.Ltr].
 */
val Modifier.ltr: Modifier get() = this + LtrModifier

/**
 * [Modifier] that changes the [LayoutDirection] of the wrapped layout to [LayoutDirection.Rtl].
 */
val Modifier.rtl: Modifier get() = this + RtlModifier

private val LtrModifier = LayoutDirectionModifier(LayoutDirection.Ltr)

private val RtlModifier = LayoutDirectionModifier(LayoutDirection.Rtl)

private data class LayoutDirectionModifier(
    val prescribedLayoutDirection: LayoutDirection
) : LayoutModifier
