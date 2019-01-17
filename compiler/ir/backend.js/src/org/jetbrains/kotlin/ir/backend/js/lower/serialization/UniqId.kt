/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower.serialization

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.metadata.js.JsProtoBuf
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassConstructorDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedPropertyDescriptor
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedSimpleFunctionDescriptor

//import org.jetbrains.kotlin.metadata.KonanIr
//import org.jetbrains.kotlin.metadata.konan.KonanProtoBuf

// This is an abstract uniqIdIndex any serialized IR declarations gets.
// It is either isLocal and then just gets and ordinary number within its module.
// Or is visible across modules and then gets a hash of mangled name as its index.
data class UniqId (
    val index: Long,
    val isLocal: Boolean
)

// isLocal=true in UniqId is good while we dealing with a single current module.
// To disambiguate module local declarations of different modules we use UniqIdKey.
// It has moduleDescriptor specified for isLocal=true uniqIds.
data class UniqIdKey private constructor(val uniqId: UniqId, val moduleDescriptor: ModuleDescriptor?) {
    constructor(moduleDescriptor: ModuleDescriptor?, uniqId: UniqId)
            : this(uniqId, if (uniqId.isLocal) moduleDescriptor else null)
}

internal val IrDeclaration.uniqIdIndex: Long
    get() = this.uniqSymbolName().hashCode().toLong() //localHash.value

fun protoUniqId(uniqId: UniqId): KonanIr.UniqId =
   KonanIr.UniqId.newBuilder()
       .setIndex(uniqId.index)
       .setIsLocal(uniqId.isLocal)
       .build()

fun KonanIr.UniqId.uniqId(): UniqId = UniqId(this.index, this.isLocal)
fun KonanIr.UniqId.uniqIdKey(moduleDescriptor: ModuleDescriptor) =
    UniqIdKey(moduleDescriptor, this.uniqId())

fun DeclarationDescriptor.getUniqId(): JsProtoBuf.DescriptorUniqId? = when (this) {
    is DeserializedClassDescriptor -> if (this.classProto.hasExtension(JsProtoBuf.classUniqId)) this.classProto.getExtension(JsProtoBuf.classUniqId) else null
    is DeserializedSimpleFunctionDescriptor -> if (this.proto.hasExtension(JsProtoBuf.functionUniqId)) this.proto.getExtension(JsProtoBuf.functionUniqId) else null
    is DeserializedPropertyDescriptor -> if (this.proto.hasExtension(JsProtoBuf.propertyUniqId)) this.proto.getExtension(JsProtoBuf.propertyUniqId) else null
    is DeserializedClassConstructorDescriptor -> if (this.proto.hasExtension(JsProtoBuf.constructorUniqId)) this.proto.getExtension(JsProtoBuf.constructorUniqId) else null
    else -> null
}

fun newDescriptorUniqId(index: Long): JsProtoBuf.DescriptorUniqId =
    JsProtoBuf.DescriptorUniqId.newBuilder().setIndex(index).build()
