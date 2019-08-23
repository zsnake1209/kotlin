/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analyzer.AbstractModuleDescriptorsFactory
import org.jetbrains.kotlin.analyzer.ModuleContent
import org.jetbrains.kotlin.analyzer.ModuleDescriptorsFactory
import org.jetbrains.kotlin.analyzer.PackageOracleFactory
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.caches.resolve.resolution
import org.jetbrains.kotlin.idea.caches.project.findSdkAcrossDependencies
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo

class IdeaModuleDescriptorsFactory(
    private val syntheticFilesByModule: Map<IdeaModuleInfo, Collection<KtFile>>,
    private val builtInsCache: BuiltInsCache,
    name: String,
    delegateModuleDescriptorsFactory: ModuleDescriptorsFactory<IdeaModuleInfo>?,
    storageManager: StorageManager,
    fallbackTracker: ModificationTracker?,
    packageOracleFactory: PackageOracleFactory,
    modules: Collection<IdeaModuleInfo>
) : AbstractModuleDescriptorsFactory<IdeaModuleInfo>(
    name,
    delegateModuleDescriptorsFactory,
    storageManager,
    fallbackTracker,
    packageOracleFactory,
    modules
) {
    override fun sdkDependency(module: IdeaModuleInfo): IdeaModuleInfo? = module.findSdkAcrossDependencies()

    override fun modulesContent(module: IdeaModuleInfo): ModuleContent<IdeaModuleInfo> =
        ModuleContent(module, syntheticFilesByModule[module] ?: emptyList(), module.contentScope())

    override fun builtInsForModule(module: IdeaModuleInfo): KotlinBuiltIns {
        val key = module.platform.idePlatformKind.resolution.getKeyForBuiltIns(module)
        return builtInsCache.getOrPut(key) { throw IllegalStateException("Can't find builtIns by key $key for module $module") }
    }
}