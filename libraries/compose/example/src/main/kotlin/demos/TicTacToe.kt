/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package demos.tictactoe

import androidx.compose.*
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import kotlin.browser.document
import kotlin.browser.window


@Composable
fun Square(value: String?, onClick: (() -> Unit)? = null) {
    console.log("value", value)
    Button(className = "square", onClick = onClick) {
        Text(value ?: "")
    }
}

@Composable
fun Board(value: List<String?>, onClick: ((Int) -> Unit)? = null) {
    Div {
        Div {
            Square(value[0], onClick = { onClick?.let { it(0) } })
            Square(value[1], onClick = { onClick?.let { it(1) } })
            Square(value[2], onClick = { onClick?.let { it(2) } })
        }
        Div {
            Square(value[3], onClick = { onClick?.let { it(3) } })
            Square(value[4], onClick = { onClick?.let { it(4) } })
            Square(value[5], onClick = { onClick?.let { it(5) } })
        }
        Div {
            Square(value[6], onClick = { onClick?.let { it(6) } })
            Square(value[7], onClick = { onClick?.let { it(7) } })
            Square(value[8], onClick = { onClick?.let { it(8) } })
        }
    }
}

@Composable
fun TicTacToeGame() {

    var history: List<HistoryItem> by state { listOf<HistoryItem>(HistoryItem(List<String?>(9) { null })) }
    var xIsNext: Boolean by state { true }
    var stepNumber: Int by state { 0 }

    fun handleClick(i: Int) {
        val history2 = history.subList(0, stepNumber + 1)
        console.log("history ", history2)
        val current = history2.last()
        val squares = current.squares.toMutableList()

        if (calculateWinner(squares) != null || squares[i] != null) {
            return
        }

        squares[i] = if (xIsNext) "X" else "O"
        history = history2 + HistoryItem(squares)
        stepNumber = history2.size
        xIsNext = !xIsNext
    }

    fun jumpTo(index: Int) {
        xIsNext = (index % 2) == 0
        stepNumber = index
    }

    val current = history[stepNumber]
    val winner = calculateWinner(current.squares)

    val moves = history.mapIndexed { move, step ->
        if (move == 0) {
            "Go to game start"
        } else {
            "Go to move #$move"
        }
    }

    val status = if (winner != null) {
        "Winner: $winner"
    } else {
        "Next player: ${if (xIsNext) "X" else "O"}"
    }

    Div(className = "game") {
        Div {
            Board(value = current.squares, onClick = { handleClick(it) })
        }

        Div {
            Text(status)
        }

        Div {
            moves.forEachIndexed { index, it ->
                Div {
                    Button(onClick = { jumpTo(index) }) {
                        Text(it)
                    }
                }
            }
        }
    }
}

fun calculateWinner(squares: List<String?>): String? {
    val lines = listOf(
        listOf(0, 1, 2),
        listOf(3, 4, 5),
        listOf(6, 7, 8),
        listOf(0, 3, 6),
        listOf(1, 4, 7),
        listOf(2, 5, 8),
        listOf(0, 4, 8),
        listOf(2, 4, 6)
    )
    lines.forEachIndexed { index, item ->
        val (a, b, c) = lines[index]
        if (squares[a] != null && squares[a] == squares[b] && squares[a] == squares[c]) {
            return squares[a]
        }
    }

    return null
}

class HistoryItem(val squares: List<String?>)

val div = SourceLocation("div")
@Composable
fun Div(className: String? = null, onClick: (() -> Unit)? = null, block: @Composable () -> Unit) {
    val composer = (currentComposer as DomComposer)

    composer.emit(
        div,
        {
            composer.document.createElement("div").also {
                if (className != null) it.className = className
                it.addClickListener(onClick)
            }
        },
        {
            updateClickListener(onClick)
        },
        { block() }
    )
}

val button = SourceLocation("button")

@Composable
fun Button(className: String? = null, onClick: (() -> Unit)? = null,  block: @Composable () -> Unit) {
    val composer = (currentComposer as DomComposer)

    composer.emit(
        button,
        {
            composer.document.createElement("button").also {
                if (className != null) it.className = className
                it.addClickListener(onClick)
            }
        },
        {
            updateClickListener(onClick)
        },
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

val listeners: MutableMap<Node, MutableList<(Event) -> Unit>> = mutableMapOf()

fun Node.addClickListener(onClick: (() -> Unit)? = null) {
    if (onClick != null) {
        listeners[this]?.apply {
            forEach {
                removeEventListener("click", it)
            }
            clear()
        }


        val callback: (Event) -> Unit = { onClick() }
        if (listeners.containsKey(this)) {
            listeners[this]!!.add(callback)
        } else {
            listeners[this] = mutableListOf(callback)
        }

        addEventListener("click", callback)
    }
}

fun DomUpdater<Node>.updateClickListener(onClick: (() -> Unit)? = null) {
    update(onClick) {
        addClickListener(onClick)
    }
}