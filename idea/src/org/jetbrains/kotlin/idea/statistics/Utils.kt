/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.statistics

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId

class StatisticsUtils {
    companion object {
        val PLUGIN = PluginManager.getPlugin(PluginId.getId("org.jetbrains.kotlin"))
        val PLUGIN_VERSION = PLUGIN?.version ?: "undefined"
    }
}