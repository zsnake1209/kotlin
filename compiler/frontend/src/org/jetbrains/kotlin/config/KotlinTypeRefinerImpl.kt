/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.config

import org.jetbrains.kotlin.builtins.isFunctionOrSuspendFunctionType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.types.checker.NewCapturedTypeConstructor
import org.jetbrains.kotlin.types.checker.REFINER_CAPABILITY
import org.jetbrains.kotlin.types.refinement.TypeRefinement
import org.jetbrains.kotlin.utils.DFS
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@UseExperimental(TypeRefinement::class)
class KotlinTypeRefinerImpl(
    private val moduleDescriptor: ModuleDescriptor,
    storageManager: StorageManager,
    languageVersionSettings: LanguageVersionSettings
) : KotlinTypeRefiner() {
    init {
        moduleDescriptor.getCapability(REFINER_CAPABILITY)?.value = this
    }

    private val isRefinementDisabled = !languageVersionSettings.isTypeRefinementEnabled
    /*
     * TODO: Dangerous place, because actually we refine only SimpleTypes, but here we cache all kotlin types
     *   that may greatly increase memory consumption
     */
    private val refinedTypeCache = storageManager.createCacheWithNotNullValues<KotlinType, KotlinType>()
    private val _isRefinementNeededForTypeConstructor =
        storageManager.createMemoizedFunction<TypeConstructor, Boolean> { it.areThereExpectSupertypesOrTypeArguments() }
    private val scopes = storageManager.createCacheWithNotNullValues<ClassDescriptor, MemberScope>()

    @TypeRefinement
    override fun refineType(type: KotlinType): KotlinType {
        if (isRefinementDisabled) return type
        val cached = refinedTypeCache.computeIfAbsent(type) {
            type.refine(this)
        }
        return updateArgumentsAnnotationsIfNeeded(type, cached)
    }

    private fun updateArgumentsAnnotationsIfNeeded(originalType: KotlinType, cachedType: KotlinType): KotlinType {
        if (!originalType.isArgumentsAnnotationsUpdateNeeded()) return cachedType

        fun doReplace(original: KotlinType, cached: KotlinType): KotlinType {
            val newArguments = mutableListOf<TypeProjection>()
            for ((originalArg, cachedArg) in original.arguments zip cached.arguments) {
                if (cachedArg.type.isError || TypeUtils.noExpectedType(cachedArg.type)) {
                    newArguments += cachedArg
                } else {
                    newArguments += cachedArg.replaceType(doReplace(originalArg.type, cachedArg.type))
                }
            }
            return cached.replace(newArguments, original.annotations)
        }

        return doReplace(originalType, cachedType)
    }

    private fun KotlinType.isArgumentsAnnotationsUpdateNeeded(): Boolean = isFunctionOrSuspendFunctionType

    @TypeRefinement
    override fun refineSupertypes(classDescriptor: ClassDescriptor): Collection<KotlinType> {
        // Note that we can't omit refinement even if classDescriptor.module == moduleDescriptor,
        // because such class may have supertypes which need refinement
        if (isRefinementDisabled) return classDescriptor.typeConstructor.supertypes
        return classDescriptor.typeConstructor.supertypes.map { refineType(it) }
    }

    @TypeRefinement
    override fun refineDescriptor(descriptor: DeclarationDescriptor): ClassifierDescriptor? {
        if (descriptor !is ClassifierDescriptorWithTypeParameters) return null
        val classId = descriptor.classId ?: return null
        return moduleDescriptor.findClassifierAcrossModuleDependencies(classId)
    }

    @TypeRefinement
    override fun refineTypeAliasTypeConstructor(typeAliasDescriptor: TypeAliasDescriptor): TypeConstructor? {
        return typeAliasDescriptor.classId?.let { moduleDescriptor.findClassifierAcrossModuleDependencies(it) }?.typeConstructor
    }

    @TypeRefinement
    override fun findClassAcrossModuleDependencies(classId: ClassId): ClassDescriptor? {
        return moduleDescriptor.findClassAcrossModuleDependencies(classId)
    }

    @TypeRefinement
    override fun isRefinementNeededForModule(moduleDescriptor: ModuleDescriptor): Boolean {
        return this.moduleDescriptor !== moduleDescriptor
    }

    @TypeRefinement
    override fun isRefinementNeededForTypeConstructor(typeConstructor: TypeConstructor): Boolean {
        return _isRefinementNeededForTypeConstructor.invoke(typeConstructor)
    }

    @TypeRefinement
    override fun <S : MemberScope> getOrPutScopeForClass(classDescriptor: ClassDescriptor, compute: () -> S): S {
        @Suppress("UNCHECKED_CAST")
        return scopes.computeIfAbsent(classDescriptor, compute) as S
    }

    private fun TypeConstructor.areThereExpectSupertypesOrTypeArguments(): Boolean {
        var result = false
        DFS.dfs(
            listOf(this),
            DFS.Neighbors(TypeConstructor::allDependentTypeConstructors),
            DFS.VisitedWithSet(),
            object : DFS.AbstractNodeHandler<TypeConstructor, Unit>() {
                override fun beforeChildren(current: TypeConstructor): Boolean {
                    if (current.isExpectClass() && current.declarationDescriptor?.module != moduleDescriptor) {
                        result = true
                        return false
                    }
                    return true
                }

                override fun result() = Unit
            }
        )

        return result
    }
}

val LanguageVersionSettings.isTypeRefinementEnabled: Boolean
    get() = getFlag(AnalysisFlags.useTypeRefinement) && supportsFeature(LanguageFeature.MultiPlatformProjects)

private val TypeConstructor.allDependentTypeConstructors: Collection<TypeConstructor>
    get() = when (this) {
        is NewCapturedTypeConstructor -> {
            supertypes.map { it.constructor } + projection.type.constructor
        }
        else -> supertypes.map { it.constructor } + parameters.map { it.typeConstructor }
    }

private fun TypeConstructor.isExpectClass() =
    declarationDescriptor?.safeAs<ClassDescriptor>()?.isExpect == true