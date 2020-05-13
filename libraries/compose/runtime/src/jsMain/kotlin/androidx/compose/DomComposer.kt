package androidx.compose

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.get

typealias DomUpdater<T> = ComposerUpdater<Node, T>

class DomComposer(
    val document: Document,
    val root: Node,
    recomposer: Recomposer
) : Composer<Node>(
    SlotTable(),
    Applier(root, DomApplierAdapter),
    recomposer
) {
    fun compose(composable: DomComposer.() -> Unit) {
        composeRoot {
            composable()
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
        for (i in index..index + count)
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
fun DomComposer.span(onClick: (() -> Unit)? = null, block: DomComposer.() -> Unit) {
    emit(
        linear,
        {
            document.createElement("span").also {
                if (onClick != null) it.addEventListener("click", { onClick() })
            }
        },
        {},
        { block() }
    )
}

val text = SourceLocation("text")
fun DomComposer.text(value: String) {
    emit(
        text,
        { document.createTextNode(value) },
        { update(value) { textContent = it } }
    )
}