/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.kapt3.base.util

import java.net.URL
import java.net.URLClassLoader

internal open class CacheInvalidatingURLClassLoader(urls: Array<URL>, parent: ClassLoader) :
    URLClassLoader(urls, parent) {

    override fun close() {
        try {
            super.close()
        } finally {
            clearJarURLCache()
        }
    }
}
