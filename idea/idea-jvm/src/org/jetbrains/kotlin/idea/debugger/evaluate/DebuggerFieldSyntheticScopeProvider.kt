/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.idea.caches.project.ModuleOrigin
import org.jetbrains.kotlin.idea.caches.project.ModuleProductionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.OriginCapability
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.load.java.components.JavaSourceElementFactoryImpl
import org.jetbrains.kotlin.load.java.components.TypeUsage
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaClassDescriptor
import org.jetbrains.kotlin.load.java.lazy.types.toAttributes
import org.jetbrains.kotlin.load.java.structure.JavaField
import org.jetbrains.kotlin.load.java.structure.classId
import org.jetbrains.kotlin.load.kotlin.internalName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.resolve.MultiTargetPlatform
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.resolve.scopes.SyntheticScope
import org.jetbrains.kotlin.synthetic.SyntheticScopeProviderExtension
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.org.objectweb.asm.Type

class DebuggerFieldSyntheticScopeProvider : SyntheticScopeProviderExtension {
    private val scopes = listOf(DebuggerFieldSyntheticScope())

    override fun getScopes(moduleDescriptor: ModuleDescriptor): List<SyntheticScope> {
        val isOurModuleDescriptor = moduleDescriptor.getCapability(ModuleInfo.Capability) is EvaluatorCodeFragmentModuleInfo
        return if (isOurModuleDescriptor) scopes else emptyList()
    }
}

private class DebuggerFieldSyntheticScope : SyntheticScope.Default() {
    private val javaSourceElementFactory = JavaSourceElementFactoryImpl()

    override fun getSyntheticExtensionProperties(
        receiverTypes: Collection<KotlinType>,
        name: Name,
        location: LookupLocation
    ): Collection<PropertyDescriptor> {
        return getSyntheticExtensionProperties(receiverTypes).filter { it.name == name }
    }

    override fun getSyntheticExtensionProperties(receiverTypes: Collection<KotlinType>): Collection<PropertyDescriptor> {
        val result = mutableListOf<PropertyDescriptor>()
        for (type in receiverTypes) {
            val clazz = type.constructor.declarationDescriptor as? ClassDescriptor ?: continue
            result += getSyntheticPropertiesForClass(clazz)
        }
        return result
    }

    private tailrec fun getSyntheticPropertiesForClass(clazz: ClassDescriptor): Collection<PropertyDescriptor> {
        val superClass = clazz.getSuperClassNotAny()

        if (clazz !is LazyJavaClassDescriptor) {
            return if (superClass != null) getSyntheticPropertiesForClass(superClass) else emptyList()
        }

        val collected = mutableListOf<PropertyDescriptor>()

        val javaClass = clazz.jClass

        for (field in javaClass.fields) {
            if (field.isEnumEntry || field.isStatic) continue

            val typeResolver = clazz.outerContext.typeResolver
            val ownerClassName = javaClass.classId?.internalName ?: continue

            val propertyDescriptor = DebuggerFieldPropertyDescriptor(
                clazz, Name.identifier(field.name.toString() + "_field"), field, Type.getObjectType(ownerClassName))

            propertyDescriptor.setType(
                typeResolver.transformJavaType(field.type, TypeUsage.COMMON.toAttributes())
                    .replaceArgumentsWithStarProjections(),
                emptyList(),
                null,
                clazz.defaultType.replaceArgumentsWithStarProjections()
            )

            val getter = PropertyGetterDescriptorImpl(
                propertyDescriptor,
                Annotations.EMPTY,
                Modality.FINAL,
                Visibilities.PUBLIC,
                false,
                false,
                false,
                CallableMemberDescriptor.Kind.SYNTHESIZED,
                null,
                javaSourceElementFactory.source(field)
            )

            propertyDescriptor.initialize(getter, null)

            collected += propertyDescriptor
        }

        return if (superClass != null) {
            @Suppress("NON_TAIL_RECURSIVE_CALL")
            collected + getSyntheticPropertiesForClass(superClass)
        } else {
            collected
        }
    }
}

internal class DebuggerFieldPropertyDescriptor(
    containingDeclaration: DeclarationDescriptor,
    name: Name,
    val field: JavaField,
    val ownerType: Type
) : PropertyDescriptorImpl(
    containingDeclaration,
    null,
    Annotations.EMPTY,
    Modality.FINAL,
    Visibilities.PUBLIC,
    /*isVar = */true,
    name,
    CallableMemberDescriptor.Kind.SYNTHESIZED,
    SourceElement.NO_SOURCE,
    /*lateInit = */false,
    /*isConst = */false,
    /*isExpect = */false,
    /*isActual = */false,
    /*isExternal = */false,
    /*isDelegated = */false
)