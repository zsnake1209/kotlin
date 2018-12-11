/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve.util

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

fun Project.isInDumbMode(): Boolean {
    val dumbService = DumbService.getInstance(this)
    // ideally we should do something if dumbService.isAlternativeResolveEnabled, because platform expects resolve to work in such cases
    return dumbService.isDumb
}

