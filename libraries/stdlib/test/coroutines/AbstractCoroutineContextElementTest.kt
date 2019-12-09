/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package test.coroutines

import kotlin.coroutines.*
import kotlin.test.*

class AbstractCoroutineContextElementTest {

    private val CoroutineContext.size get() = fold(0) { acc, _ -> acc + 1 }

    abstract class Base : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Base>

        override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? = getPolymorphicElement(key)
        override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext = minusPolymorphicKey(key)
    }

    class DerivedWithoutKey : Base() {
        // No custom key
    }

    open class DerivedWithKey : Base() {
        companion object Key : AbstractCoroutineContextKey<Base, DerivedWithKey>(Base, { it as? DerivedWithKey })
    }

    class SubDerivedWithKey : DerivedWithKey() {
        companion object Key : AbstractCoroutineContextKey<Base, SubDerivedWithKey>(Base, { it as? SubDerivedWithKey })
    }

    class SubDerivedWithKeyAndDifferentBase : DerivedWithKey() {
        // Note how different base class is used
        companion object Key :
            AbstractCoroutineContextKey<DerivedWithKey, SubDerivedWithKeyAndDifferentBase>(
                DerivedWithKey,
                { it as? SubDerivedWithKeyAndDifferentBase })
    }

    object IrrelevantElement : AbstractCoroutineContextElement(Key) {
        object Key : CoroutineContext.Key<IrrelevantElement>
    }

    @Test
    fun testDerivedWithoutKey() {
        testDerivedWithoutKey(EmptyCoroutineContext, DerivedWithoutKey()) // Single element
        testDerivedWithoutKey(IrrelevantElement, DerivedWithoutKey()) // Combined context
    }

    @Test
    fun testDerivedWithoutKeyOverridesDerived() {
        val context = DerivedWithKey() + DerivedWithoutKey()
        assertEquals(1, context.size)
        assertTrue(context[Base] is DerivedWithoutKey)
        assertNull(context[DerivedWithKey])
        assertEquals(EmptyCoroutineContext, context.minusKey(Base))
        assertSame(context, context.minusKey(DerivedWithKey))
    }

    private fun testDerivedWithoutKey(context: CoroutineContext, element: CoroutineContext.Element) {
        val ctx = context + element
        assertEquals(context.size + 1, ctx.size)
        assertSame(element, ctx[Base]!!)
        assertNull(ctx[DerivedWithKey])
        assertEquals(context, ctx.minusKey(Base))
        assertSame(ctx, ctx.minusKey(DerivedWithKey))
    }

    @Test
    fun testDerivedWithKey() {
        testDerivedWithKey(EmptyCoroutineContext, DerivedWithKey()) // Single element
        testDerivedWithKey(IrrelevantElement, DerivedWithKey()) // Combined context
    }

    private fun testDerivedWithKey(context: CoroutineContext, element: CoroutineContext.Element) {
        val ctx = context + element
        assertEquals(context.size + 1, ctx.size)
        assertSame(element, ctx[Base]!!)
        assertSame(element, ctx[DerivedWithKey]!!)
        assertEquals(context, ctx.minusKey(Base))
        assertEquals(context, ctx.minusKey(DerivedWithKey))
    }

    @Test
    fun testSubDerivedWithKey() {
        testSubDerivedWithKey(EmptyCoroutineContext, SubDerivedWithKey())
        testSubDerivedWithKey(IrrelevantElement, SubDerivedWithKey())
    }

    private fun testSubDerivedWithKey(context: CoroutineContext, element: CoroutineContext.Element) {
        val ctx = context + element
        assertEquals(context.size + 1, ctx.size)
        assertSame(element, ctx[Base]!!)
        assertSame(element, ctx[DerivedWithKey]!!)
        assertSame(element, ctx[SubDerivedWithKey]!!)
        assertNull(ctx[SubDerivedWithKeyAndDifferentBase])
        assertEquals(context, ctx.minusKey(Base))
        assertEquals(context, ctx.minusKey(DerivedWithKey))
        assertEquals(context, ctx.minusKey(SubDerivedWithKey))
        assertSame(ctx, ctx.minusKey(SubDerivedWithKeyAndDifferentBase))
    }

    @Test
    fun testSubDerivedWithKeyAndDifferentBase() {
        testSubDerivedWithKeyAndDifferentBase(EmptyCoroutineContext, SubDerivedWithKeyAndDifferentBase())
        testSubDerivedWithKeyAndDifferentBase(IrrelevantElement, SubDerivedWithKeyAndDifferentBase())
    }

    private fun testSubDerivedWithKeyAndDifferentBase(context: CoroutineContext, element: CoroutineContext.Element) {
        val ctx = context + element
        assertEquals(context.size + 1, ctx.size)
        assertSame(element, ctx[Base]!!)
        assertSame(element, ctx[DerivedWithKey]!!)
        assertSame(element, ctx[SubDerivedWithKeyAndDifferentBase]!!)
        assertNull(ctx[SubDerivedWithKey])
        assertEquals(context, ctx.minusKey(Base))
        assertEquals(context, ctx.minusKey(DerivedWithKey))
        assertEquals(context, ctx.minusKey(SubDerivedWithKeyAndDifferentBase))
        assertSame(ctx, ctx.minusKey(SubDerivedWithKey))
    }

    @Test
    fun testDerivedWithKeyOverridesDerived() {
        val context = DerivedWithoutKey() + DerivedWithKey()
        assertEquals(1, context.size)
        assertTrue { context[Base] is DerivedWithKey }
        assertTrue { context[DerivedWithKey] is DerivedWithKey }
        assertEquals(EmptyCoroutineContext, context.minusKey(Base))
        assertEquals(EmptyCoroutineContext, context.minusKey(DerivedWithKey))
    }

    @Test
    fun testSubDerivedOverrides() {
        testSubDerivedOverrides<SubDerivedWithKeyAndDifferentBase>(DerivedWithoutKey() + SubDerivedWithKeyAndDifferentBase())
        testSubDerivedOverrides<SubDerivedWithKeyAndDifferentBase>(DerivedWithKey() + SubDerivedWithKeyAndDifferentBase())
        testSubDerivedOverrides<SubDerivedWithKeyAndDifferentBase>(SubDerivedWithKeyAndDifferentBase() + SubDerivedWithKeyAndDifferentBase())
    }

    @Test
    fun testSubDerivedWithDifferentBaseOverrides() {
        testSubDerivedOverrides<SubDerivedWithKey>(DerivedWithoutKey() + SubDerivedWithKey())
        testSubDerivedOverrides<SubDerivedWithKey>(DerivedWithKey() + SubDerivedWithKey())
        testSubDerivedOverrides<SubDerivedWithKey>(SubDerivedWithKeyAndDifferentBase() + SubDerivedWithKey())
    }

    private inline fun <reified T> testSubDerivedOverrides(context: CoroutineContext) {
        assertEquals(1, context.size)
        assertTrue { context[Base] is DerivedWithKey }
        assertTrue { context[DerivedWithKey] is DerivedWithKey }
        assertTrue { context[DerivedWithKey] is T }
        assertEquals(EmptyCoroutineContext, context.minusKey(Base))
        assertEquals(EmptyCoroutineContext, context.minusKey(DerivedWithKey))

    }
}
