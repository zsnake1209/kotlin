/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.coroutines

import kotlin.coroutines.*
import kotlin.test.*

class ContinuationInterceptorKeyTest {

    private val CoroutineContext.size get() = fold(0) { acc, _ -> acc + 1 }

    abstract class Dispatcher : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
        companion object Key :
            AbstractCoroutineContextKey<ContinuationInterceptor, Dispatcher>(ContinuationInterceptor, { it as? Dispatcher })

        override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = continuation
    }

    // "Legacy" code, EventLoop with ContinuationInterceptor key
    class EventLoop : Dispatcher() {
        override val key: CoroutineContext.Key<*>
            get() = ContinuationInterceptor
    }

    // "New" code with AbstractCoroutineContextKey
    class ExecutorDispatcher : Dispatcher() {
        companion object Key : AbstractCoroutineContextKey<Dispatcher, ExecutorDispatcher>(Dispatcher, { it as? ExecutorDispatcher })
    }

    // Irrelevant interceptor
    class CustomInterceptor() : ContinuationInterceptor {
        override val key: CoroutineContext.Key<*>
            get() = ContinuationInterceptor

        override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> = continuation
    }

    object IrrelevantElement : AbstractCoroutineContextElement(Key) {
        object Key : CoroutineContext.Key<IrrelevantElement>
    }

    @Test
    fun testDispatcherKeyIsNotOverridden() {
        val eventLoop = EventLoop()
        testDispatcherKeyIsNotOverridden(eventLoop, eventLoop)
        testDispatcherKeyIsNotOverridden(IrrelevantElement + eventLoop, eventLoop) // test for CombinedContext
    }

    private fun testDispatcherKeyIsNotOverridden(context: CoroutineContext, element: CoroutineContext.Element) {
        run {
            val interceptor: ContinuationInterceptor = context[ContinuationInterceptor]!!
            assertSame(element, interceptor)
        }
        run {
            val dispatcher: Dispatcher = context[Dispatcher]!!
            assertSame(element, dispatcher)
        }

        val subtracted = context.minusKey(ContinuationInterceptor)
        assertEquals(subtracted, context.minusKey(Dispatcher))
        assertNull(subtracted[ContinuationInterceptor])
        assertNull(subtracted[Dispatcher])
        assertEquals(context.size - 1, subtracted.size)
    }

    @Test
    fun testDispatcherKeyIsOverridden() {
        val executor = ExecutorDispatcher()
        testDispatcherKeyIsOverridden(executor, executor)
        testDispatcherKeyIsOverridden(IrrelevantElement + executor, executor) // test for CombinedContext
    }

    private fun testDispatcherKeyIsOverridden(context: CoroutineContext, element: CoroutineContext.Element) {
        testDispatcherKeyIsNotOverridden(context, element)
        val executor = context[ExecutorDispatcher]
        assertNotNull(executor)
        assertSame(element, executor)
        val subtracted = context.minusKey(ContinuationInterceptor)
        assertEquals(subtracted, context.minusKey(Dispatcher))
        assertEquals(subtracted, context.minusKey(ExecutorDispatcher))
        assertEquals(context.size - 1, subtracted.size)
    }

    @Test
    fun testInterceptorKeyIsNotOverridden() {
        val ci = CustomInterceptor()
        testInterceptorKeyIsNotOverridden(ci, ci)
        testInterceptorKeyIsNotOverridden(IrrelevantElement + ci, ci) // test for CombinedContext
    }

    private fun testInterceptorKeyIsNotOverridden(context: CoroutineContext, element: CoroutineContext.Element) {
        val interceptor = context[ContinuationInterceptor]
        assertNotNull(interceptor)
        assertSame(element, interceptor)
        assertNull(context[Dispatcher])
        assertNull(context[ExecutorDispatcher])
        assertEquals(context, context.minusKey(Dispatcher))
        assertEquals(context, context.minusKey(ExecutorDispatcher))
    }

    @Test
    fun testContextOperations() {
        val interceptor = CustomInterceptor()
        val dispatcher = EventLoop()
        val executor = ExecutorDispatcher()
        val e = IrrelevantElement
        run {
            assertEquals(interceptor, dispatcher + executor + interceptor)
            assertEquals(interceptor + e, dispatcher + executor + interceptor + e)
            assertEquals(interceptor, dispatcher + interceptor)
            assertEquals(interceptor, executor + interceptor)
        }

        run {
            assertEquals(dispatcher, executor + interceptor + dispatcher)
            assertEquals(dispatcher + e, executor + interceptor + dispatcher + e)
            assertEquals(dispatcher, executor + dispatcher)
            assertEquals(dispatcher, interceptor + dispatcher)
        }

        run {
            assertEquals(executor, interceptor + dispatcher + executor)
            assertEquals(executor + e, interceptor + dispatcher + executor + e)
            assertEquals(executor, dispatcher + executor)
            assertEquals(executor, interceptor + executor)
        }
    }
}
