/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.coroutines

import kotlin.coroutines.*
import kotlin.test.*

class AbstractCoroutineContextElementTest {

    private val CoroutineContext.size get() = fold(0) { acc, _ -> acc + 1 }

    abstract class CancelToken : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<CancelToken>

        override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = getPolymorphicElement(key)
        override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = minusPolymorphicKey(key)
    }

    class StdlibJob : CancelToken() {
        // No custom key
    }

    class Job : CancelToken() {
        companion object Key : AbstractCoroutineContextKey<CancelToken, Job>(CancelToken, { it as? Job })
    }

    object IrrelevantElement : AbstractCoroutineContextElement(Key) {
        object Key : CoroutineContext.Key<IrrelevantElement>
    }

    @Test
    fun testCancelToken() {
        testCancelToken(EmptyCoroutineContext, StdlibJob()) // Single element
        testCancelToken(IrrelevantElement, StdlibJob()) // Combined context
    }

    @Test
    fun testCancelTokenOverridesJob() {
        val context = Job() + StdlibJob()
        assertEquals(1, context.size)
        assertTrue(context[CancelToken] is StdlibJob)
        assertNull(context[Job])
        assertEquals(EmptyCoroutineContext, context.minusKey(CancelToken))
        assertSame(context, context.minusKey(Job))
    }

    private fun testCancelToken(context: CoroutineContext, element: CoroutineContext.Element) {
        val ctx = context + element
        assertEquals(context.size + 1, ctx.size)
        assertSame(element, ctx[CancelToken]!!)
        assertNull(ctx[Job])
        assertEquals(context, ctx.minusKey(CancelToken))
        assertSame(ctx, ctx.minusKey(Job))
    }

    @Test
    fun testJob() {
        testJob(EmptyCoroutineContext, Job()) // Single element
        testJob(IrrelevantElement, Job()) // Combined context
    }

    private fun testJob(context: CoroutineContext, element: CoroutineContext.Element) {
        val ctx = context + element
        assertEquals(context.size + 1, ctx.size)
        assertSame(element, ctx[CancelToken]!!)
        assertSame(element, ctx[Job]!!)
        assertEquals(context, ctx.minusKey(CancelToken))
        assertEquals(context, ctx.minusKey(Job))
    }

    @Test
    fun testJobOverridesCancelToken() {
        val context = StdlibJob() + Job()
        assertEquals(1, context.size)
        assertTrue { context[CancelToken] is Job }
        assertTrue { context[Job] is Job }
        assertEquals(EmptyCoroutineContext, context.minusKey(CancelToken))
        assertEquals(EmptyCoroutineContext, context.minusKey(Job))
    }
}
