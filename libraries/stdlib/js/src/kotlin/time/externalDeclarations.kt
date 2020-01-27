/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.time

// TODO: This file is temporary hack for extracting browser API from JS stdlib.

internal external abstract class Performance : EventTarget {
    open val timing: PerformanceTiming
    open val navigation: PerformanceNavigation
    fun now(): Double
}

internal external interface GlobalPerformance {
    val performance: Performance
}

internal external abstract class PerformanceTiming {
    open val navigationStart: Number
    open val unloadEventStart: Number
    open val unloadEventEnd: Number
    open val redirectStart: Number
    open val redirectEnd: Number
    open val fetchStart: Number
    open val domainLookupStart: Number
    open val domainLookupEnd: Number
    open val connectStart: Number
    open val connectEnd: Number
    open val secureConnectionStart: Number
    open val requestStart: Number
    open val responseStart: Number
    open val responseEnd: Number
    open val domLoading: Number
    open val domInteractive: Number
    open val domContentLoadedEventStart: Number
    open val domContentLoadedEventEnd: Number
    open val domComplete: Number
    open val loadEventStart: Number
    open val loadEventEnd: Number
}

internal external abstract class EventTarget {
    fun addEventListener(type: String, callback: EventListener?, options: dynamic = definedExternally)
    fun addEventListener(type: String, callback: ((Event) -> Unit)?, options: dynamic = definedExternally)
    fun removeEventListener(type: String, callback: EventListener?, options: dynamic = definedExternally)
    fun removeEventListener(type: String, callback: ((Event) -> Unit)?, options: dynamic = definedExternally)
    fun dispatchEvent(event: Event): Boolean
}

internal external abstract class PerformanceNavigation {
    open val type: Short
    open val redirectCount: Short

    companion object {
        val TYPE_NAVIGATE: Short
        val TYPE_RELOAD: Short
        val TYPE_BACK_FORWARD: Short
        val TYPE_RESERVED: Short
    }
}

internal external open class Event(type: String, eventInitDict: EventInit = definedExternally) {
    open val type: String
    open val target: EventTarget?
    open val currentTarget: EventTarget?
    open val eventPhase: Short
    open val bubbles: Boolean
    open val cancelable: Boolean
    open val defaultPrevented: Boolean
    open val composed: Boolean
    open val isTrusted: Boolean
    open val timeStamp: Number
    fun composedPath(): Array<EventTarget>
    fun stopPropagation()
    fun stopImmediatePropagation()
    fun preventDefault()
    fun initEvent(type: String, bubbles: Boolean, cancelable: Boolean)

    companion object {
        val NONE: Short
        val CAPTURING_PHASE: Short
        val AT_TARGET: Short
        val BUBBLING_PHASE: Short
    }
}

internal external interface EventInit {
    var bubbles: Boolean? /* = false */
        get() = definedExternally
        set(value) = definedExternally
    var cancelable: Boolean? /* = false */
        get() = definedExternally
        set(value) = definedExternally
    var composed: Boolean? /* = false */
        get() = definedExternally
        set(value) = definedExternally
}

internal external interface EventListener {
    fun handleEvent(event: Event)
}
