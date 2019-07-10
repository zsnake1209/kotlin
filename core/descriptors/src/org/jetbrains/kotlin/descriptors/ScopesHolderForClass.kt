/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors

import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.StorageManager
import org.jetbrains.kotlin.storage.getValue
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.types.refinement.TypeRefinement

class ScopesHolderForClass<T : MemberScope> private constructor(
    private val classDescriptor: ClassDescriptor,
    storageManager: StorageManager,
    private val scopeFactory: (KotlinTypeRefiner) -> T,
    private val kotlinTypeRefinerForOwnerModule: KotlinTypeRefiner
) {
    private val scopeForOwnerModule by storageManager.createLazyValue {
        scopeFactory(kotlinTypeRefinerForOwnerModule)
    }

    @UseExperimental(TypeRefinement::class)
    fun getScope(kotlinTypeRefiner: KotlinTypeRefiner): T {
        if (!kotlinTypeRefiner.isRefinementNeededForModule(classDescriptor.module)) return scopeForOwnerModule

        if (!kotlinTypeRefiner.isRefinementNeededForTypeConstructor(classDescriptor.typeConstructor)) return scopeForOwnerModule
        return kotlinTypeRefiner.getOrPutScopeForClass(classDescriptor) { scopeFactory(kotlinTypeRefiner) }
    }

    companion object {
        fun <T : MemberScope> create(
            classDescriptor: ClassDescriptor,
            storageManager: StorageManager,
            kotlinTypeRefinerForOwnerModule: KotlinTypeRefiner,
            scopeFactory: (KotlinTypeRefiner) -> T
        ): ScopesHolderForClass<T> {
            return ScopesHolderForClass(classDescriptor, storageManager, scopeFactory, kotlinTypeRefinerForOwnerModule)
        }
    }
}
