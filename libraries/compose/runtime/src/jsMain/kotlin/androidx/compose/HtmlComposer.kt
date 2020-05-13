package androidx.compose

import org.w3c.dom.Document
import org.w3c.dom.Node
import org.w3c.dom.get

class HtmlComposer(
    val document: Document,
    val root: Node,
    recomposer: Recomposer
) : Composer<Node>(
    SlotTable(),
    Applier(root, HtmlElementApplierAdapter),
    recomposer
) {
    val rootComposition: HtmlComposition = HtmlComposition(this)

    fun compose(composition: HtmlComposition.() -> Unit) {
        composeRoot {
            rootComposition.composition()
        }
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
object HtmlElementApplierAdapter : ApplyAdapter<Node> {
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

val composer get() = HtmlComposition(currentComposerNonNull as HtmlComposer)

class HtmlComposition(val cc: HtmlComposer) {
    inline fun <V : Node> emit(
        key: Any,
        noinline factory: () -> V,
        block: HtmlComposition.() -> Unit
    ) {
        cc.startNode(key)
        cc.emitNode(factory)
        block()
        cc.endNode()
    }

    inline fun <V : Node, reified A1> emit(
        key: Any,
        noinline factory: () -> V,
        a1: A1,
        noinline set1: V.(A1) -> Unit
    ) {
        cc.startNode(key)
        cc.emitNode(factory)
        if (cc.changed(a1)) {
            cc.apply(a1, set1)
        }
        cc.endNode()
    }

    inline fun call(
        key: Any,
        invalid: Composer<Node>.() -> Boolean,
        block: () -> Unit
    ) = with(cc) {
        startGroup(key)
        if (invalid() || inserting) {
            startGroup(invocation)
            block()
            endGroup()
        } else {
            (cc as Composer<*>).skipCurrentGroup()
        }
        endGroup()
    }
}

class SourceLocation(val name: String) {
    override fun toString(): String = "SL $name"
}

val linear = SourceLocation("linear")
inline fun HtmlComposition.span(noinline onClick: (() -> Unit)? = null, block: HtmlComposition.() -> Unit) {
    emit(linear, {
        cc.document.createElement("span").also {
            if (onClick != null) it.addEventListener("click", { onClick() })
        }
    }, block)
}

val text = SourceLocation("text")
inline fun HtmlComposition.text(value: String) {
    emit(text, { cc.document.createTextNode(value) }, value, { textContent = it })
}