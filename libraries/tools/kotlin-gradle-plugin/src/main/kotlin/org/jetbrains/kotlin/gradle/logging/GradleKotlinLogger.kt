/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.logging

import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.compilerRunner.KotlinLogger

internal class GradleKotlinLogger constructor(private val log: Logger, private val isVerbose: Boolean) : KotlinLogger {
    override fun debug(msg: String) {
        if (isVerbose) {
            log.lifecycle(msg)
        } else {
            log.debug(msg)
        }
    }

    override fun error(msg: String) {
        log.error(msg)
    }

    override fun info(msg: String) {
        if (isVerbose) {
            log.lifecycle(msg)
        } else {
            log.info(msg)
        }
    }

    override fun warn(msg: String) {
        log.warn(msg)
    }

    override val isDebugEnabled: Boolean
        get() = log.isDebugEnabled || isVerbose
}