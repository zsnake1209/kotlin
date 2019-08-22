/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analyzer

import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.storage.StorageManager

interface ModuleDescriptorsFactory<M : ModuleInfo> {
    fun descriptorForModule(moduleInfo: M): ModuleDescriptor
    fun tryGetDescriptorForModule(moduleInfo: M): ModuleDescriptor?
    fun moduleForDescriptor(descriptor: ModuleDescriptor): M?

    val allModules: Collection<M>
}

abstract class AbstractModuleDescriptorsFactory<M : ModuleInfo>(
    private val name: String,
    private val delegateModuleDescriptorsFactory: ModuleDescriptorsFactory<M>?,
    private val storageManager: StorageManager,
    private val fallbackTracker: ModificationTracker?,
    private val packageOracleFactory: PackageOracleFactory,
    modules: Collection<M>
) : ModuleDescriptorsFactory<M> {
    lateinit var resolverForProject: ResolverForProject<M>

    // Protected by ("projectContext.storageManager.lock")
    private val descriptorByModule = mutableMapOf<M, ModuleData>()

    // Protected by ("projectContext.storageManager.lock")
    private val moduleInfoByDescriptor = mutableMapOf<ModuleDescriptorImpl, M>()

    @Suppress("UNCHECKED_CAST")
    private val moduleInfoToResolvableInfo: Map<M, M> =
        modules.flatMap { module -> module.flatten().map { modulePart -> modulePart to module } }.toMap() as Map<M, M>

    init {
        assert(moduleInfoToResolvableInfo.values.toSet() == modules.toSet())
    }

    override val allModules: Collection<M> by lazy {
        this.moduleInfoToResolvableInfo.keys + (delegateModuleDescriptorsFactory?.allModules ?: emptyList())
    }

    override fun descriptorForModule(moduleInfo: M): ModuleDescriptor {
        if (!isCorrectModuleInfo(moduleInfo)) {
            DiagnoseUnknownModuleInfoReporter.report(name, listOf(moduleInfo))
        }
        return doGetDescriptorForModule(moduleInfo)
    }

    override fun tryGetDescriptorForModule(moduleInfo: M): ModuleDescriptor? {
        if (!isCorrectModuleInfo(moduleInfo)) {
            return null
        }
        return doGetDescriptorForModule(moduleInfo)
    }

    override fun moduleForDescriptor(descriptor: ModuleDescriptor): M? {
        return moduleInfoByDescriptor[descriptor]
    }

    private fun doGetDescriptorForModule(module: M): ModuleDescriptorImpl {
        val moduleFromThisResolver = moduleInfoToResolvableInfo[module]
            ?: return delegateModuleDescriptorsFactory?.descriptorForModule(module) as ModuleDescriptorImpl?
                ?: DiagnoseUnknownModuleInfoReporter.report(name, listOf(module))

        return storageManager.compute {
            var moduleData = descriptorByModule.getOrPut(moduleFromThisResolver) {
                createModuleDescriptor(moduleFromThisResolver)
            }
            if (moduleData.isOutOfDate()) {
                moduleData = recreateModuleDescriptor(moduleFromThisResolver)
            }
            moduleData.moduleDescriptor
        }
    }

    private fun recreateModuleDescriptor(module: M): ModuleData {
        val oldDescriptor = descriptorByModule[module]?.moduleDescriptor
        if (oldDescriptor != null) {
            oldDescriptor.isValid = false
            moduleInfoByDescriptor.remove(oldDescriptor)
//            resolverByModuleDescriptor.remove(oldDescriptor)
        }

        val moduleData = createModuleDescriptor(module)
        descriptorByModule[module] = moduleData
        return moduleData
    }

    private fun createModuleDescriptor(module: M): ModuleData {
        val moduleDescriptor = ModuleDescriptorImpl(
            module.name,
            storageManager,
            builtInsForModule(module),
            module.platform,
            module.capabilities,
            module.stableName
        )
        moduleInfoByDescriptor[moduleDescriptor] = module
        setupModuleDescriptor(module, moduleDescriptor)
        val modificationTracker = if (module is TrackableModuleInfo) module.createModificationTracker() else fallbackTracker
        return ModuleData(moduleDescriptor, modificationTracker)
    }

    private fun setupModuleDescriptor(module: M, moduleDescriptor: ModuleDescriptorImpl) {
        moduleDescriptor.setDependencies(
            LazyModuleDependencies(
                storageManager,
                module,
                sdkDependency(module),
                resolverForProject
            )
        )

        val content = modulesContent(module)
        moduleDescriptor.initialize(
            DelegatingPackageFragmentProvider(
                resolverForProject, moduleDescriptor, content,
                packageOracleFactory.createOracle(module)
            )
        )
    }

    private fun isCorrectModuleInfo(moduleInfo: M) = moduleInfo in allModules

    abstract fun sdkDependency(module: M): M?
    abstract fun modulesContent(module: M): ModuleContent<M>
    abstract fun builtInsForModule(module: M): KotlinBuiltIns

    private class ModuleData(
        val moduleDescriptor: ModuleDescriptorImpl,
        val modificationTracker: ModificationTracker?
    ) {
        val modificationCount: Long = modificationTracker?.modificationCount ?: Long.MIN_VALUE

        fun isOutOfDate(): Boolean {
            val currentModCount = modificationTracker?.modificationCount
            return currentModCount != null && currentModCount > modificationCount
        }
    }
}

class ModuleDescriptorFactoryImpl<M : ModuleInfo>(
    private val sdkDependency: M?,

    name: String,
    delegateModuleDescriptorsFactory: ModuleDescriptorsFactory<M>?,
    storageManager: StorageManager,
    fallbackTracker: ModificationTracker?,
    packageOracleFactory: PackageOracleFactory,
    modules: Collection<M>
) : AbstractModuleDescriptorsFactory<M>(
    name,
    delegateModuleDescriptorsFactory,
    storageManager,
    fallbackTracker,
    packageOracleFactory,
    modules
) {
    override fun sdkDependency(module: M): M? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun modulesContent(module: M): ModuleContent<M> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun builtInsForModule(module: M): KotlinBuiltIns {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

object DiagnoseUnknownModuleInfoReporter {
    fun report(name: String, infos: List<ModuleInfo>): Nothing {
        val message = "$name does not know how to resolve $infos"
        when {
            name.contains(ResolverForProject.resolverForSdkName) -> errorInSdkResolver(
                message
            )
            name.contains(ResolverForProject.resolverForLibrariesName) -> errorInLibrariesResolver(
                message
            )
            name.contains(ResolverForProject.resolverForModulesName) -> {
                when {
                    infos.isEmpty() -> errorInModulesResolverWithEmptyInfos(
                        message
                    )
                    infos.size == 1 -> {
                        val infoAsString = infos.single().toString()
                        when {
                            infoAsString.contains("ScriptDependencies") -> errorInModulesResolverWithScriptDependencies(
                                message
                            )
                            infoAsString.contains("Library") -> errorInModulesResolverWithLibraryInfo(
                                message
                            )
                            else -> errorInModulesResolver(message)
                        }
                    }
                    else -> throw errorInModulesResolver(message)
                }
            }
            name.contains(ResolverForProject.resolverForScriptDependenciesName) -> errorInScriptDependenciesInfoResolver(
                message
            )
            name.contains(ResolverForProject.resolverForSpecialInfoName) -> {
                when {
                    name.contains("ScriptModuleInfo") -> errorInScriptModuleInfoResolver(
                        message
                    )
                    else -> errorInSpecialModuleInfoResolver(message)
                }
            }
            else -> otherError(message)
        }
    }

    // Do not inline 'error*'-methods, they are needed to avoid Exception Analyzer merging those AssertionErrors

    private fun errorInSdkResolver(message: String): Nothing = throw AssertionError(message)
    private fun errorInLibrariesResolver(message: String): Nothing = throw AssertionError(message)
    private fun errorInModulesResolver(message: String): Nothing = throw AssertionError(message)

    private fun errorInModulesResolverWithEmptyInfos(message: String): Nothing = throw AssertionError(message)
    private fun errorInModulesResolverWithScriptDependencies(message: String): Nothing = throw AssertionError(message)
    private fun errorInModulesResolverWithLibraryInfo(message: String): Nothing = throw AssertionError(message)

    private fun errorInScriptDependenciesInfoResolver(message: String): Nothing = throw AssertionError(message)
    private fun errorInScriptModuleInfoResolver(message: String): Nothing = throw AssertionError(message)
    private fun errorInSpecialModuleInfoResolver(message: String): Nothing = throw AssertionError(message)

    private fun otherError(message: String): Nothing = throw AssertionError(message)
}