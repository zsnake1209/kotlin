/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.js.worker

import kotlin.js.Promise

class WebWorker<I, O>(private val worker: dynamic) {
    init {
        aliveWorkers.add(WorkerWrapper(worker))
    }

    fun send(a: I): Promise<O> {
        worker.postMessage(a)
        return Promise { resolve, reject -> worker.on("message") { e -> resolve(e) }}
    }

    companion object {
        private val aliveWorkers = arrayListOf<WorkerWrapper>()
        internal var captured: dynamic = null
        fun terminateWorkers() {
            for (worker in aliveWorkers) {
                worker.worker.terminate()
            }
        }
    }
}

fun <O> WebWorker<Unit, O>.start(): Promise<O> {
    return send(Unit)
}

internal fun terminateWorkers() {
    WebWorker.terminateWorkers()
}

private class WorkerWrapper(val worker: dynamic)

internal fun getCapturedVariable(s: String): dynamic {
    return WebWorker.captured[s]
}

internal fun setCapturedVariables(c: dynamic) {
    WebWorker.captured = c
}

fun <I, O> worker(c: (I) -> O): WebWorker<I, O> {
    throw UnsupportedOperationException("Implemented as intrinsic")
}

internal fun postMessage(message: dynamic) {
    WorkerThreads.parentPort.postMessage(message)
}

internal external object WorkerThreads {
    val parentPort: dynamic
}