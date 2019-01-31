/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.backend.jvm.lower.*
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.name.NameUtils

private fun makeJvmPhase(
    lowering: (JvmBackendContext) -> FileLoweringPass,
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase> = emptySet()
) = makeIrFilePhase(lowering, name, description, prerequisite)

private val JvmCoercionToUnitPhase = makeJvmPhase(
    ::JvmCoercionToUnitPatcher,
    name = "JvmCoercionToUnit",
    description = "Insert conversions to unit after IrCalls where needed"
)

private val FileClassPhase = makeJvmPhase(
    ::FileClassLowering,
    name = "FileClass",
    description = "Put file level function and property declaration into a class"
)

private val KCallableNamePropertyPhase = makeJvmPhase(
    ::KCallableNamePropertyLowering,
    name = "KCallableNameProperty",
    description = "Replace name references for callables with constants"
)

private val LateinitPhase = makeJvmPhase(
    { context -> LateinitLowering(context, true) },
    name = "Lateinit",
    description = "Insert checks for lateinit field references"
)

private val MoveCompanionObjectFieldsPhase = makeJvmPhase(
    ::MoveCompanionObjectFieldsLowering,
    name = "MoveCompanionObjectFields",
    description = "Move companion object fields to static fields of companion's owner"
)


private val ConstAndJvmFieldPropertiesPhase = makeJvmPhase(
    ::ConstAndJvmFieldPropertiesLowering,
    name = "ConstAndJvmFieldProperties",
    description = "Substitute calls to const and Jvm>Field properties with const/field access"
)


private val PropertiesPhase = makeJvmPhase(
    ::PropertiesLowering,
    name = "Properties",
    description = "move fields and accessors for properties to their classes"
)


private val AnnotationPhase = makeJvmPhase(
    ::AnnotationLowering,
    name = "Annotation",
    description = "Remove constructors from annotation classes"
)

private val DefaultArgumentStubPhase = makeJvmPhase(
    { context -> DefaultArgumentStubGenerator(context, false) },
    name = "DefaultArgumentsStubGenerator",
    description = "Generate synthetic stubs for functions with default parameter values"
)

private val InterfacePhase = makeJvmPhase(
    ::InterfaceLowering,
    name = "Interface",
    description = "Move default implementations of interface members to DefaultImpls class"
)

private val InterfaceDelegationPhase = makeJvmPhase(
    ::InterfaceDelegationLowering,
    name = "InterfaceDelegation",
    description = "Delegate calls to interface members with default implementations to DefaultImpls"
)

private val SharedVariablesPhase = makeJvmPhase(
    ::SharedVariablesLowering,
    name = "SharedVariables",
    description = "Transform shared variables"
)

private val LocalDeclarationsPhase = makeJvmPhase(
    { context ->
        LocalDeclarationsLowering(context, object : LocalNameProvider {
            override fun localName(descriptor: DeclarationDescriptor): String =
                NameUtils.sanitizeAsJavaIdentifier(super.localName(descriptor))
        }, Visibilities.PUBLIC, true)
    },
    name = "JvmLocalDeclarations",
    description = "Move local declarations to classes",
    prerequisite = setOf(SharedVariablesPhase)
)

private val CallableReferencePhase = makeJvmPhase(
    ::CallableReferenceLowering,
    name = "CallableReference",
    description = "Handle callable references"
)

private val FunctionNVarargInvokePhase = makeJvmPhase(
    ::FunctionNVarargInvokeLowering,
    name = "FunctionNVarargInvoke",
    description = "Handle invoke functions with large number of arguments"
)

private val InnerClassesPhase = makeJvmPhase(
    ::InnerClassesLowering,
    name = "InnerClasses",
    description = "Move inner classes to toplevel"
)

private val InnerClassConstructorCallsPhase = makeJvmPhase(
    ::InnerClassConstructorCallsLowering,
    name = "InnerClassConstructorCalls",
    description = "Handle constructor calls for inner classes"
)

private val EnumClassPhase = makeJvmPhase(
    ::EnumClassLowering,
    name = "EnumClass",
    description = "Handle enum classes"
)

private val ObjectClassPhase = makeJvmPhase(
    ::ObjectClassLowering,
    name = "ObjectClass",
    description = "Handle object classes"
)

private val InitializersPhase = makeJvmPhase(
    { context -> InitializersLowering(context, JvmLoweredDeclarationOrigin.CLASS_STATIC_INITIALIZER, true) },
    name = "Initializers",
    description = "Handle initializer statements"
)

private val SingletonReferencesPhase = makeJvmPhase(
    ::SingletonReferencesLowering,
    name = "SingletonReferences",
    description = "Handle singleton references"
)

private val SyntheticAccessorPhase = makeJvmPhase(
    ::SyntheticAccessorLowering,
    name = "SyntheticAccessor",
    description = "Introduce synthetic accessors",
    prerequisite = setOf(ObjectClassPhase)
)

private val BridgePhase = makeJvmPhase(
    ::BridgeLowering,
    name = "Bridge",
    description = "Generate bridges"
)

private val JvmOverloadsAnnotationPhase = makeJvmPhase(
    ::JvmOverloadsAnnotationLowering,
    name = "JvmOverloadsAnnotation",
    description = "Handle JvmOverloads annotations"
)

private val JvmStaticAnnotationPhase = makeJvmPhase(
    ::JvmStaticAnnotationLowering,
    name = "JvmStaticAnnotation",
    description = "Handle JvmStatic annotations"
)

private val StaticDefaultFunctionPhase = makeJvmPhase(
    ::StaticDefaultFunctionLowering,
    name = "StaticDefaultFunction",
    description = "Generate static functions for default parameters"
)

private val TailrecPhase = makeJvmPhase(
    ::TailrecLowering,
    name = "Tailrec",
    description = "Handle tailrec calls"
)

private val ToArrayPhase = makeJvmPhase(
    ::ToArrayLowering,
    name = "ToArray",
    description = "Handle toArray functions"
)

val jvmPhases = namedIrFilePhase(
    name = "IrLowering",
    description = "IR lowering",
    lower = JvmCoercionToUnitPhase then
            FileClassPhase then
            KCallableNamePropertyPhase then

            LateinitPhase then

            MoveCompanionObjectFieldsPhase then
            ConstAndJvmFieldPropertiesPhase then
            PropertiesPhase then
            AnnotationPhase then

            DefaultArgumentStubPhase then

            InterfacePhase then
            InterfaceDelegationPhase then
            SharedVariablesPhase then

            makePatchParentsPhase(1) then

            LocalDeclarationsPhase then
            CallableReferencePhase then
            FunctionNVarargInvokePhase then

            InnerClassesPhase then
            InnerClassConstructorCallsPhase then

            makePatchParentsPhase(2) then

            EnumClassPhase then
            ObjectClassPhase then
            InitializersPhase then
            SingletonReferencesPhase then
            SyntheticAccessorPhase then
            BridgePhase then
            JvmOverloadsAnnotationPhase then
            JvmStaticAnnotationPhase then
            StaticDefaultFunctionPhase then

            TailrecPhase then
            ToArrayPhase then

            makePatchParentsPhase(3)
)
