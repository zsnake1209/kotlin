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

package org.jetbrains.kotlin.resolve.lazy

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analyzer.*
import org.jetbrains.kotlin.analyzer.common.CommonAnalysisParameters
import org.jetbrains.kotlin.analyzer.common.CommonResolverForModuleFactory
import org.jetbrains.kotlin.builtins.DefaultBuiltIns
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.context.ProjectContext
import org.jetbrains.kotlin.context.withModule
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.impl.ModuleDescriptorImpl
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.CommonPlatforms
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.CompilerEnvironment
import org.jetbrains.kotlin.resolve.PlatformDependentAnalyzerServices
import org.jetbrains.kotlin.resolve.jvm.JvmPlatformParameters
import org.jetbrains.kotlin.resolve.jvm.JvmResolverForModuleFactory
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatformAnalyzerServices

fun createResolveSessionForFiles(
    project: Project,
    syntheticFiles: Collection<KtFile>,
    addBuiltIns: Boolean
): ResolveSession {
    val projectContext = ProjectContext(project, "lazy resolve test utils")
    val testModule =
        TestModule(project, addBuiltIns)
    val platformParameters = JvmPlatformParameters(
        packagePartProviderFactory = { PackagePartProvider.Empty },
        moduleByJavaClass = { testModule }
    )

    val moduleDescriptorsFactory = object : AbstractModuleDescriptorsFactory<TestModule>(
        "lazyResolveTestUtils",
        delegateModuleDescriptorsFactory = null,
        storageManager = projectContext.storageManager,
        fallbackTracker = null,
        packageOracleFactory = PackageOracleFactory.OptimisticFactory,
        modules = listOf(testModule)
    ) {
        override fun sdkDependency(module: TestModule): TestModule? = null

        override fun modulesContent(module: TestModule): ModuleContent<TestModule> =
            ModuleContent(module, syntheticFiles, GlobalSearchScope.allScope(project))

        override fun builtInsForModule(module: TestModule): KotlinBuiltIns = DefaultBuiltIns.Instance
    }

    val resolverForProject = object : AbstractResolverForProject<TestModule>(
        moduleDescriptorsFactory,
        projectContext,
        delegateResolver = EmptyResolverForProject(),
        name = "lazyResolveTestUtils"
    ) {
        override fun createResolverForModule(descriptor: ModuleDescriptor, moduleInfo: TestModule): ResolverForModule {
            return JvmResolverForModuleFactory(platformParameters, CompilerEnvironment, JvmPlatforms.defaultJvmPlatform)
                .createResolverForModule(
                    descriptor as ModuleDescriptorImpl,
                    projectContext.withModule(descriptor),
                    ModuleContent(moduleInfo, syntheticFiles, GlobalSearchScope.allScope(project)),
                    this,
                    LanguageVersionSettingsImpl.DEFAULT
                )
        }
    }

    return resolverForProject.resolverForModule(testModule).componentProvider.get<ResolveSession>()
}

private class TestModule(val project: Project, val dependsOnBuiltIns: Boolean) : TrackableModuleInfo {
    override val name: Name = Name.special("<Test module for lazy resolve>")

    override fun dependencies() = listOf(this)
    override fun dependencyOnBuiltIns() =
        if (dependsOnBuiltIns)
            ModuleInfo.DependencyOnBuiltIns.LAST
        else
            ModuleInfo.DependencyOnBuiltIns.NONE

    override val platform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    override fun createModificationTracker(): ModificationTracker {
        return ModificationTracker.NEVER_CHANGED
    }

    override val analyzerServices: PlatformDependentAnalyzerServices
        get() = JvmPlatformAnalyzerServices
}
