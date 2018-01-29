/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.highlighter.dsl

import kotlinx.colorScheme.Call
import kotlinx.colorScheme.Class
import kotlinx.colorScheme.MemberDeclaration
import kotlinx.colorScheme.Receiver
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class CallImpl(private val resolvedCall: ResolvedCall<*>): Call {
    override val declaration: MemberDeclaration
        get() = MemberDeclarationImpl(resolvedCall.resultingDescriptor)
    override val receiver: Receiver
        get() = ReceiverImpl(resolvedCall)
}

class MemberDeclarationImpl(private val declaration: DeclarationDescriptor) : MemberDeclaration {
    override val containingClass: Class?
        get() = declaration.parents.firstIsInstanceOrNull<ClassDescriptor>()?.let(::ClassImpl)
    override val name: String
        get() = declaration.name.asString()
    override val packageName: String
        get() = declaration.parents.firstIsInstance<PackageFragmentDescriptor>().fqName.asString()
}

class ClassImpl(private val descriptor: ClassDescriptor) : Class {
    override val annotations: List<Class>
        get() = descriptor.annotations.mapNotNull { it.annotationClass?.let(::ClassImpl) }
    override val fqName: String
        get() = descriptor.fqNameSafe.asString()
}

class ReceiverImpl(private val resolvedCall: ResolvedCall<*>): Receiver {
    override val dispatchReceiver: Class?
        get() = classFromReceiver(resolvedCall.dispatchReceiver)

    override val extensionReceiver: Class?
        get() = classFromReceiver(resolvedCall.extensionReceiver)

    private fun classFromReceiver(receiverValue: ReceiverValue?) =
        (receiverValue?.type?.constructor?.declarationDescriptor as? ClassDescriptor)?.let(::ClassImpl)
}