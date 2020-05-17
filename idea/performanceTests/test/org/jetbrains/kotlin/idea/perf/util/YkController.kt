/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.perf.util

class YkController {
    private val controllerClass = ignoreExceptions("No yjp-controller-api-redist.jar found in classpath") { Class.forName("com.yourkit.api.Controller") }
    private val controller = ignoreExceptions("Agent not started") { controllerClass?.newInstance() }
    private val startTracing = controllerClass?.getMethod("startTracing", "".javaClass)
    private val capturePerformanceSnapshot = controllerClass?.getMethod("capturePerformanceSnapshot")
    private val stopCpuProfiling = controllerClass?.getMethod("stopCpuProfiling")

    fun startTracing() =
        startTracing?.invoke(controller, null)

    fun capturePerformanceSnapshot() =
        capturePerformanceSnapshot?.invoke(controller)

    fun stopCpuProfiling() =
        stopCpuProfiling?.invoke(controller)

    inline fun <T : Any> ignoreExceptions(error: String, block: () -> T?): T? {
        return try {
            block()
        } catch (e: Exception) {
            println(error)
            null
        }
    }

}
