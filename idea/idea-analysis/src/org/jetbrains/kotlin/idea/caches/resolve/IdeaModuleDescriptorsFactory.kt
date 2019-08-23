/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.analyzer.AbstractModuleDescriptorsFactory
import org.jetbrains.kotlin.analyzer.ModuleContent
import org.jetbrains.kotlin.analyzer.PackageOracleFactory
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.builtins.jvm.JvmBuiltIns
import org.jetbrains.kotlin.caches.resolve.resolution
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.idea.caches.project.findSdkAcrossDependencies
import org.jetbrains.kotlin.platform.idePlatformKind
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.caches.project.IdeaModuleInfo
import java.util.concurrent.ConcurrentHashMap

class IdeaModuleDescriptorsFactory(
    private val syntheticFilesByModule: Map<IdeaModuleInfo, Collection<KtFile>>,
    projectContext: ProjectContext,
    name: String,
    delegateModuleDescriptorsFactory: IdeaModuleDescriptorsFactory?,
    fallbackTracker: ModificationTracker?,
    packageOracleFactory: PackageOracleFactory,
    modules: Collection<IdeaModuleInfo>
) : AbstractModuleDescriptorsFactory<IdeaModuleInfo>(
    name,
    delegateModuleDescriptorsFactory,
    projectContext.storageManager,
    fallbackTracker,
    packageOracleFactory,
    modules
) {
    /* Caches consistency notice:
    Reference to this cache is held by facadeForSdk.cachedResolverForProject. This guarantees:
        - that cache will be invalidated on root/library modifications (as resolver for SDK has exactly those dependencies)
        - it will never be discarded without discarding whole resolver (and, consequently, all descriptors which might reference
          those built-ins)
     */
    private val builtInsCache: BuiltInsCache =
        delegateModuleDescriptorsFactory?.builtInsCache ?: BuiltInsCache(projectContext)

    override fun sdkDependency(module: IdeaModuleInfo): IdeaModuleInfo? = module.findSdkAcrossDependencies()

    override fun modulesContent(module: IdeaModuleInfo): ModuleContent<IdeaModuleInfo> =
        ModuleContent(module, syntheticFilesByModule[module] ?: emptyList(), module.contentScope())

    // Protected by ("projectContext.storageManager.lock")
    override fun builtInsForModule(module: IdeaModuleInfo): KotlinBuiltIns {
        return builtInsCache.getOrCreateIfNeeded(module)
    }

    inner class BuiltInsCache(private val projectContext: ProjectContext) {
        private val cache = ConcurrentHashMap<BuiltInsCacheKey, KotlinBuiltIns>()

        init {
            cache[BuiltInsCacheKey.DefaultBuiltInsKey] = DefaultBuiltIns.Instance
        }

        // Protected by ("projectContext.storageManager.lock")
        fun getOrCreateIfNeeded(module: IdeaModuleInfo): KotlinBuiltIns {
            val key = module.platform.idePlatformKind.resolution.getKeyForBuiltIns(module)
            val cachedBuiltIns = cache[key]
            if (cachedBuiltIns != null) return cachedBuiltIns

            // Note #1: we can't use .getOrPut, because we have to put builtIns into map *before* initialization
            // Note #2: it's OK to put not-initialized built-ins into public map, because access to [cache] is guarded by storageManager.lock
            val newBuiltIns = module.platform.idePlatformKind.resolution.createBuiltIns(module, projectContext)
            cache[key] = newBuiltIns

            if (newBuiltIns is JvmBuiltIns) {
                // SDK should be present, otherwise we wouldn't have created JvmBuiltIns in createBuiltIns
                val sdk = module.findSdkAcrossDependencies()!!
                val sdkDescriptor = resolverForProject.descriptorForModule(sdk)

                val isAdditionalBuiltInsFeaturesSupported = module.supportsAdditionalBuiltInsMembers(projectContext.project)

                newBuiltIns.initialize(sdkDescriptor, isAdditionalBuiltInsFeaturesSupported)
            }

            return newBuiltIns
        }
    }
}

interface BuiltInsCacheKey {
    object DefaultBuiltInsKey : BuiltInsCacheKey
}
