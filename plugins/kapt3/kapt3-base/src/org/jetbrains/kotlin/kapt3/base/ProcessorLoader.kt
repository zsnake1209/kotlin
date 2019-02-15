/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.base

import org.jetbrains.kotlin.base.kapt3.KaptFlag
import org.jetbrains.kotlin.base.kapt3.KaptOptions
import org.jetbrains.kotlin.kapt3.base.util.*
import java.io.Closeable
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.processing.Processor

class LoadedProcessors(val processors: List<Processor>, val classLoader: ClassLoader)

open class ProcessorLoader(private val options: KaptOptions, private val logger: KaptLogger) : Closeable {
    companion object {
        private val classLoaderCache: MutableMap<ClassPath, SpeculativeClassloader> = ConcurrentHashMap()
    }

    private var annotationProcessingClassLoader: URLClassLoader? = null

    fun loadProcessors(parentClassLoader: ClassLoader = ClassLoader.getSystemClassLoader()): LoadedProcessors {
        val classPath = classPathOf {
            for (file in options.processingClasspath) {
                yield(file.toURI())
            }
            if (options[KaptFlag.INCLUDE_COMPILE_CLASSPATH]) {
                for (file in options.compileClasspath) {
                    yield(file.toURI())
                }
            }
        }

        val classLoader = if (options[KaptFlag.USE_SPECULATIVE_CLASS_LOADING]) {
            logger.info("Use speculative class loading to improve processors latency")

            val speculativeClassloader = classLoaderCache[classPath]
                ?.flip()
                ?: SpeculativeClassloader(classPath.toArray(), parentClassLoader)

            classLoaderCache[classPath] = speculativeClassloader

            speculativeClassloader
        } else {
            clearJarURLCache() // TODO: aren't we clearing the jar URL cache too much?
            CacheInvalidatingURLClassLoader(classPath.toArray(), parentClassLoader)
        }

        this.annotationProcessingClassLoader = classLoader

        val processors = if (options.processors.isNotEmpty()) {
            logger.info("Annotation processor class names are set, skip AP discovery")
            options.processors.mapNotNull { tryLoadProcessor(it, classLoader) }
        } else {
            logger.info("Need to discovery annotation processors in the AP classpath")
            doLoadProcessors(classLoader)
        }

        if (processors.isEmpty()) {
            logger.info("No annotation processors available, aborting")
        } else {
            logger.info { "Annotation processors: " + processors.joinToString { it::class.java.canonicalName } }
        }

        return LoadedProcessors(processors, classLoader)
    }

    open fun doLoadProcessors(classLoader: URLClassLoader): List<Processor> {
        return ServiceLoader.load(Processor::class.java, classLoader).toList()
    }

    private fun tryLoadProcessor(fqName: String, classLoader: ClassLoader): Processor? {
        val annotationProcessorClass = try {
            Class.forName(fqName, true, classLoader)
        } catch (e: Throwable) {
            logger.warn("Can't find annotation processor class $fqName: ${e.message}")
            return null
        }

        try {
            val annotationProcessorInstance = annotationProcessorClass.newInstance()
            if (annotationProcessorInstance !is Processor) {
                logger.warn("$fqName is not an instance of 'Processor'")
                return null
            }

            return annotationProcessorInstance
        } catch (e: Throwable) {
            logger.warn("Can't load annotation processor class $fqName: ${e.message}")
            return null
        }
    }

    override fun close() {
        annotationProcessingClassLoader?.close()
    }
}
