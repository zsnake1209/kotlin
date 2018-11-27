/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analyzer.common

import org.jetbrains.kotlin.resolve.*
import org.jetbrains.kotlin.storage.StorageManager

object CommonPlatform : TargetPlatform("Default") {
    override val platformConfigurator: PlatformConfigurator = CommonPlatformConfiguratorImpl()
    override val multiTargetPlatform: MultiTargetPlatform
        get() = MultiTargetPlatform.Common
    override val isCommon get() = true
    override fun computePlatformSpecificDefaultImports(storageManager: StorageManager, result: MutableList<ImportPath>) {}
}