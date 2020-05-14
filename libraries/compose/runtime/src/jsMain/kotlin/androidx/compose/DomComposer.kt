/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package androidx.compose

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.get

typealias DomUpdater<T> = ComposerUpdater<Node, T>

class DomComposer(
    val document: Document,
    val root: Node,
    slotTabe: SlotTable,
    recomposer: Recomposer
) : Composer<Node>(slotTabe, Applier(root, DomApplierAdapter), recomposer) {
    fun compose(composable: @Composable () -> Unit) {
        composeRoot {
            invokeComposable(this, composable)
        }
    }

    inline fun <T : Node> emit(
        key: Any,
        /*crossinline*/
        ctor: () -> T,
        update: DomUpdater<T>.() -> Unit
    ) {
        startNode(key)
        val node = if (inserting) ctor().also { emitNode(it) }
        else useNode() as T
        DomUpdater(this, node).update()
        endNode()
    }

    inline fun emit(
        key: Any,
        /*crossinline*/
        ctor: () -> Node,
        update: DomUpdater<Node>.() -> Unit,
        children: () -> Unit
    ) {
        startNode(key)
        val node = if (inserting) ctor().also { emitNode(it) }
        else useNode()
        DomUpdater(this, node).update()
        children()
        endNode()
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
object DomApplierAdapter : ApplyAdapter<Node> {
    override fun Node.start(instance: Node) {}

    override fun Node.insertAt(index: Int, instance: Node) {
        insertBefore(instance, childNodes[index])
    }

    override fun Node.removeAt(index: Int, count: Int) {
        for (i in index until index + count)
            removeChild(childNodes[index]!!)
    }

    override fun Node.move(from: Int, to: Int, count: Int) {
        if (from == to) return

        if (from > to) {
            val target = childNodes[to]
            val toMove = mutableListOf<Node>()
            repeat(count) {
                val child = childNodes[from]!!
                toMove.add(child)
                removeChild(child)
            }
            toMove.forEach { insertBefore(it, target) }
        } else {
            // Number of elements between to and from is smaller than count, can't move.
            if (count > to - from) return
            repeat(count) {
                val node = childNodes[from]!!
                removeChild(node)
                insertBefore(node, childNodes[to - 1])
            }
        }
    }

    override fun Node.end(instance: Node, parent: Node) {}
}

class SourceLocation(val name: String) {
    override fun toString(): String = "SL $name"
}

val linear = SourceLocation("linear")
@Composable
fun Span(onClick: (() -> Unit)? = null,  block: @Composable () -> Unit) {
    val composer = (currentComposer as DomComposer)
    composer.emit(
        linear,
        {
            composer.document.createElement("span").also {
                if (onClick != null) it.addEventListener("click", { onClick() })
            }
        },
        {},
        { block() }
    )
}

val text = SourceLocation("text")
@Composable
fun Text(value: String) {
    val composer = (currentComposer as DomComposer)
    composer.emit(
        text,
        { composer.document.createTextNode(value) },
        { update(value) { textContent = it } }
    )
}