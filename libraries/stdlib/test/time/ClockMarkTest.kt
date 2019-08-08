/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */
@file:UseExperimental(ExperimentalTime::class)
package test.time

import kotlin.test.*
import kotlin.time.*

class ClockMarkTest {

    @Test
    fun adjustment() {
        val clock = TestClock(unit = DurationUnit.NANOSECONDS)

        fun ClockMark.assertIsInFuture(inFuture: Boolean) {
            assertEquals(inFuture, this.isInFuture(), "Expected mark in future value")
            assertEquals(!inFuture, this.isInPast(), "Expected mark in past value")

            assertEquals(inFuture, this.elapsed() < Duration.ZERO, "Mark elapsed: ${this.elapsed()}")
        }

        val mark = clock.mark()
        val markFuture1 = (mark + 1.milliseconds).apply { assertIsInFuture(true) }
        val markFuture2 = (mark - (-1).milliseconds).apply { assertIsInFuture(true) }

        val markPast1 = (mark - 1.milliseconds).apply { assertIsInFuture(false) }
        val markPast2 = (markFuture1 + (-2).milliseconds).apply { assertIsInFuture(false) }

        clock += 500_000.nanoseconds

        val elapsed = mark.elapsed()
        val elapsedFromFuture = elapsed - 1.milliseconds
        val elapsedFromPast = elapsed + 1.milliseconds

        assertEquals(0.5.milliseconds, elapsed)
        assertEquals(elapsedFromFuture, markFuture1.elapsed())
        assertEquals(elapsedFromFuture, markFuture2.elapsed())

        assertEquals(elapsedFromPast, markPast1.elapsed())
        assertEquals(elapsedFromPast, markPast2.elapsed())

        markFuture1.assertIsInFuture(true)
        markPast1.assertIsInFuture(false)

        clock += 1.milliseconds

        markFuture1.assertIsInFuture(false)
        markPast1.assertIsInFuture(false)

    }
}