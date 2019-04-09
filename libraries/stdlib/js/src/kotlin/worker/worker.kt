/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.worker

// TODO: use kotlin-native like workers
class WebWorker(private val worker: Any) {
    fun postMessage(message: Any) {
        js("worker_WebWorker.postMessage(message)")
    }

    fun onmessage(c: (Any) -> Unit) {
        js("worker_WebWorker.onmessage(c)")
    }
}

// TODO: Return Future
fun worker(c: (Any) -> Any): WebWorker {
    throw UnsupportedOperationException("Implemented as intrinsic")
}
