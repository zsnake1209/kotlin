/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package kotlin.worker

// TODO: make this interface generic
interface WebWorker {
    fun postMessage(message: Any)
    fun onmessage(c: (Any) -> Unit)
}

fun worker(c: WebWorker.() -> Unit): WebWorker {
    throw UnsupportedOperationException("Implemented as intrinsic")
}
