/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.analyzer

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.context.ModuleContext
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.descriptors.impl.ModuleDependencies
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.TargetPlatformVersion
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.storage.getValue
import java.util.*

class ResolverForModule(
    val packageFragmentProvider: PackageFragmentProvider,
    val componentProvider: ComponentProvider
)

abstract class ResolverForProject<M : ModuleInfo> {
    abstract fun resolverForModule(moduleInfo: M): ResolverForModule
    abstract fun tryGetResolverForModule(moduleInfo: M): ResolverForModule?
    abstract fun descriptorForModule(moduleInfo: M): ModuleDescriptor
    abstract fun resolverForModuleDescriptor(descriptor: ModuleDescriptor): ResolverForModule
    abstract fun isResolverForModuleDescriptorComputed(descriptor: ModuleDescriptor): Boolean
    abstract fun invalidate(descriptor: ModuleDescriptor)

    abstract val name: String
    abstract val allModules: Collection<M>

    override fun toString() = name

    companion object {
        const val resolverForSdkName = "sdk"
        const val resolverForLibrariesName = "project libraries"
        const val resolverForModulesName = "project source roots and libraries"
        const val resolverForScriptDependenciesName = "dependencies of scripts"

        const val resolverForSpecialInfoName = "completion/highlighting in "
    }
}

class EmptyResolverForProject<M : ModuleInfo> : ResolverForProject<M>() {
    override val name: String
        get() = "Empty resolver"

    override fun resolverForModule(moduleInfo: M): ResolverForModule =
        throw IllegalStateException("$moduleInfo is not contained in empty resolver")

    override fun tryGetResolverForModule(moduleInfo: M): ResolverForModule? = null
    override fun resolverForModuleDescriptor(descriptor: ModuleDescriptor): ResolverForModule =
        throw IllegalStateException("$descriptor is not contained in this resolver")

    override fun descriptorForModule(moduleInfo: M) = throw IllegalStateException("Should not be called for $moduleInfo")
    override val allModules: Collection<M> = listOf()

    override fun isResolverForModuleDescriptorComputed(descriptor: ModuleDescriptor): Boolean =
        throw IllegalStateException("Should not be called for $descriptor")

    override fun invalidate(descriptor: ModuleDescriptor) { }
}

abstract class AbstractResolverForProject<M : ModuleInfo>(
    open val moduleDescriptorsFactory: ModuleDescriptorsFactory<M>,
    protected val projectContext: ProjectContext,
    protected val delegateResolver: ResolverForProject<M>,
    override val name: String
) : ResolverForProject<M>() {
    override val allModules: Collection<M>
        get() = moduleDescriptorsFactory.allModules

    // Protected by ("projectContext.storageManager.lock")
    private val resolverByModuleDescriptor = mutableMapOf<ModuleDescriptor, ResolverForModule>()

    override fun resolverForModule(moduleInfo: M): ResolverForModule =
        resolverForModuleDescriptor(moduleDescriptorsFactory.descriptorForModule(moduleInfo))

    override fun tryGetResolverForModule(moduleInfo: M): ResolverForModule? {
        val descriptor = moduleDescriptorsFactory.tryGetDescriptorForModule(moduleInfo)
        return resolverForModuleDescriptor(descriptor ?: return null)
    }

    override fun invalidate(descriptor: ModuleDescriptor) {
        resolverByModuleDescriptor.remove(descriptor)
    }

    override fun resolverForModuleDescriptor(descriptor: ModuleDescriptor): ResolverForModule {
        return projectContext.storageManager.compute {
            val module = moduleDescriptorsFactory.moduleForDescriptor(descriptor)

            if (module == null) {
                if (delegateResolver is EmptyResolverForProject<*>) {
                    throw IllegalStateException("$descriptor is not contained in resolver $name")
                }
                return@compute delegateResolver.resolverForModuleDescriptor(descriptor)
            }

            resolverByModuleDescriptor.getOrPut(descriptor) {
                ResolverForModuleComputationTracker.getInstance(projectContext.project)?.onResolverComputed(module)
                createResolverForModule(descriptor, module)
            }
        }
    }

    override fun isResolverForModuleDescriptorComputed(descriptor: ModuleDescriptor) =
        projectContext.storageManager.compute {
            descriptor in resolverByModuleDescriptor
        }

    override fun descriptorForModule(moduleInfo: M): ModuleDescriptor = moduleDescriptorsFactory.descriptorForModule(moduleInfo)

    abstract fun createResolverForModule(descriptor: ModuleDescriptor, moduleInfo: M): ResolverForModule
}

// Simplistic implementation of factory when we have only one module (might be useful for tests or other simplified environments)
class ResolverForProjectWithSingleModule<M : ModuleInfo> private constructor(
    name: String,
    private val resolverForModuleFactory: ResolverForModuleFactory,
    moduleDescriptorsFactory: ModuleDescriptorsFactory<M>,
    projectContext: ProjectContext,
    private val searchScope: GlobalSearchScope,
    private val syntheticFiles: Collection<KtFile>,
    private val languageVersionSettings: LanguageVersionSettings
) : AbstractResolverForProject<M>(moduleDescriptorsFactory, projectContext, EmptyResolverForProject(), name) {

    private class ModuleDescriptorsFactoryForSingleModule<M : ModuleInfo>(
        private val module: M,
        name: String,
        storageManager: StorageManager,
        private val searchScope: GlobalSearchScope,
        private val sdkDependency: M? = null,
        private val builtIns: KotlinBuiltIns = DefaultBuiltIns.Instance,
        private val syntheticFiles: Collection<KtFile> = emptyList()
    ) : AbstractModuleDescriptorsFactory<M>(
        name,
        null,
        storageManager,
        null,
        PackageOracleFactory.OptimisticFactory,
        listOf(module)
    ) {
        override fun sdkDependency(module: M): M? = sdkDependency

        override fun modulesContent(module: M): ModuleContent<M> {
            require(module == this.module) {
                "ModuleDescriptorFactoryForSingleModule created for ${this.module} has been passed an unknown module $module"
            }
            return ModuleContent(module, syntheticFiles, searchScope)
        }

        override fun builtInsForModule(module: M): KotlinBuiltIns = builtIns
    }


    override fun createResolverForModule(descriptor: ModuleDescriptor, moduleInfo: M): ResolverForModule =
        resolverForModuleFactory.createResolverForModule(
            descriptor as ModuleDescriptorImpl,
            projectContext.withModule(descriptor),
            ModuleContent(moduleInfo, syntheticFiles, searchScope),
            this,
            languageVersionSettings
        )

    companion object {
        fun <M : ModuleInfo> create(
            name: String,
            moduleInfo: M,
            resolverForModuleFactory: ResolverForModuleFactory,
            projectContext: ProjectContext,
            searchScope: GlobalSearchScope,
            syntheticFiles: Collection<KtFile> = emptyList(),
            sdkDependency: M? = null,
            builtIns: KotlinBuiltIns = DefaultBuiltIns.Instance,
            languageVersionSettings: LanguageVersionSettings = LanguageVersionSettingsImpl.DEFAULT
        ): ResolverForProject<M> {
            val factoryForSingleModule = ModuleDescriptorsFactoryForSingleModule(
                moduleInfo,
                "moduleDescriptorsFactory $name",
                projectContext.storageManager,
                searchScope,
                sdkDependency,
                builtIns,
                syntheticFiles
            )

            val resolverForSingleModule = ResolverForProjectWithSingleModule(
                name,
                resolverForModuleFactory,
                factoryForSingleModule,
                projectContext,
                searchScope,
                syntheticFiles,
                languageVersionSettings
            )

            factoryForSingleModule.initialize(resolverForSingleModule)

            return resolverForSingleModule
        }
    }
}

data class ModuleContent<out M : ModuleInfo>(
    val moduleInfo: M,
    val syntheticFiles: Collection<KtFile>,
    val moduleContentScope: GlobalSearchScope
)

interface PlatformAnalysisParameters {
    object Empty : PlatformAnalysisParameters
}

interface CombinedModuleInfo : ModuleInfo {
    val containedModules: List<ModuleInfo>
    val platformModule: ModuleInfo
}

fun ModuleInfo.flatten(): List<ModuleInfo> = when (this) {
    is CombinedModuleInfo -> listOf(this) + containedModules
    else -> listOf(this)
}

fun ModuleInfo.unwrapPlatform(): ModuleInfo = if (this is CombinedModuleInfo) platformModule else this

interface TrackableModuleInfo : ModuleInfo {
    fun createModificationTracker(): ModificationTracker
}

interface LibraryModuleInfo : ModuleInfo {
    override val platform: TargetPlatform

    fun getLibraryRoots(): Collection<String>
}

abstract class ResolverForModuleFactory {
    abstract fun <M : ModuleInfo> createResolverForModule(
        moduleDescriptor: ModuleDescriptorImpl,
        moduleContext: ModuleContext,
        moduleContent: ModuleContent<M>,
        resolverForProject: ResolverForProject<M>,
        languageVersionSettings: LanguageVersionSettings
    ): ResolverForModule
}

class LazyModuleDependencies<M : ModuleInfo>(
    storageManager: StorageManager,
    private val module: M,
    firstDependency: M? = null,
    private val moduleDescriptorsFactory: ModuleDescriptorsFactory<M>
) : ModuleDependencies {
    private val dependencies = storageManager.createLazyValue {
        val moduleDescriptor = moduleDescriptorsFactory.descriptorForModule(module)
        sequence {
            if (firstDependency != null) {
                yield(moduleDescriptorsFactory.descriptorForModule(firstDependency) as ModuleDescriptorImpl)
            }
            if (module.dependencyOnBuiltIns() == ModuleInfo.DependencyOnBuiltIns.AFTER_SDK) {
                yield(moduleDescriptor.builtIns.builtInsModule)
            }
            for (dependency in module.dependencies()) {
                @Suppress("UNCHECKED_CAST")
                yield(moduleDescriptorsFactory.descriptorForModule(dependency as M) as ModuleDescriptorImpl)
            }
            if (module.dependencyOnBuiltIns() == ModuleInfo.DependencyOnBuiltIns.LAST) {
                yield(moduleDescriptor.builtIns.builtInsModule)
            }
        }.toList()
    }

    override val allDependencies: List<ModuleDescriptorImpl> get() = dependencies()

    override val expectedByDependencies by storageManager.createLazyValue {
        module.expectedBy.map {
            @Suppress("UNCHECKED_CAST")
            moduleDescriptorsFactory.descriptorForModule(it as M) as ModuleDescriptorImpl
        }
    }

    override val modulesWhoseInternalsAreVisible: Set<ModuleDescriptorImpl>
        get() =
            module.modulesWhoseInternalsAreVisible().mapTo(LinkedHashSet()) {
                @Suppress("UNCHECKED_CAST")
                moduleDescriptorsFactory.descriptorForModule(it as M) as ModuleDescriptorImpl
            }

}


class DelegatingPackageFragmentProvider<M : ModuleInfo>(
    private val resolverForProject: ResolverForProject<M>,
    private val module: ModuleDescriptor,
    moduleContent: ModuleContent<M>,
    private val packageOracle: PackageOracle
) : PackageFragmentProvider {
    private val syntheticFilePackages = moduleContent.syntheticFiles.map { it.packageFqName }.toSet()

    override fun getPackageFragments(fqName: FqName): List<PackageFragmentDescriptor> {
        if (certainlyDoesNotExist(fqName)) return emptyList()

        return resolverForProject.resolverForModuleDescriptor(module).packageFragmentProvider.getPackageFragments(fqName)
    }

    override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
        if (certainlyDoesNotExist(fqName)) return emptyList()

        return resolverForProject.resolverForModuleDescriptor(module).packageFragmentProvider.getSubPackagesOf(fqName, nameFilter)
    }

    private fun certainlyDoesNotExist(fqName: FqName): Boolean {
        if (resolverForProject.isResolverForModuleDescriptorComputed(module)) return false // let this request get cached inside delegate

        return !packageOracle.packageExists(fqName) && fqName !in syntheticFilePackages
    }
}

interface PackageOracle {
    fun packageExists(fqName: FqName): Boolean

    object Optimistic : PackageOracle {
        override fun packageExists(fqName: FqName): Boolean = true
    }
}

interface PackageOracleFactory {
    fun createOracle(moduleInfo: ModuleInfo): PackageOracle

    object OptimisticFactory : PackageOracleFactory {
        override fun createOracle(moduleInfo: ModuleInfo) = PackageOracle.Optimistic
    }
}

interface LanguageSettingsProvider {
    fun getLanguageVersionSettings(
        moduleInfo: ModuleInfo,
        project: Project,
        isReleaseCoroutines: Boolean? = null
    ): LanguageVersionSettings

    fun getTargetPlatform(moduleInfo: ModuleInfo, project: Project): TargetPlatformVersion

    object Default : LanguageSettingsProvider {
        override fun getLanguageVersionSettings(
            moduleInfo: ModuleInfo,
            project: Project,
            isReleaseCoroutines: Boolean?
        ) = LanguageVersionSettingsImpl.DEFAULT

        override fun getTargetPlatform(moduleInfo: ModuleInfo, project: Project): TargetPlatformVersion = TargetPlatformVersion.NoVersion
    }
}

interface ResolverForModuleComputationTracker {

    fun onResolverComputed(moduleInfo: ModuleInfo)

    companion object {
        fun getInstance(project: Project): ResolverForModuleComputationTracker? =
            ServiceManager.getService(project, ResolverForModuleComputationTracker::class.java) ?: null
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> ModuleInfo.getCapability(capability: ModuleDescriptor.Capability<T>) = capabilities[capability] as? T
