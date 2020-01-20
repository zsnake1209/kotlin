/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.name.FqName

sealed class IdSignature {

    abstract val isPublic: Boolean

    open fun isPackageSignature() = false

    abstract fun topLevelSignature(): IdSignature
    abstract fun nearestPublicSig(): IdSignature

    abstract fun packageFqName(): FqName

    abstract fun render(): String

    val isLocal: Boolean get() = !isPublic

    override fun toString(): String {
        return "${if (isPublic) "public" else "private"} ${render()}"
    }

    data class PublicSignature(val packageFqn: FqName, val classFqn: FqName, val id: Long?, val mask: Long) : IdSignature() {
        override val isPublic = true

        override fun packageFqName() = packageFqn

        override fun topLevelSignature(): IdSignature {
            if (classFqn.isRoot) {
                assert(id == null)
                // package signature
                return this
            }

            val pathSegments = classFqn.pathSegments()

            if (pathSegments.size == 1) return this

            return PublicSignature(packageFqn, FqName(pathSegments.first().asString()), null, mask)
        }

        override fun isPackageSignature(): Boolean = id == null && classFqn.isRoot

        override fun nearestPublicSig(): PublicSignature = this

        override fun render(): String = "${packageFqn.asString()}/${classFqn.asString()}|$id[${mask.toString(2)}]"

        override fun toString() = super.toString()
    }

    class AccessorSignature(val propertySignature: IdSignature, val accessorSignature: PublicSignature) : IdSignature() {
        override val isPublic: Boolean = true

        override fun topLevelSignature() = propertySignature.topLevelSignature()

        override fun nearestPublicSig() = this

        override fun packageFqName() = propertySignature.packageFqName()

        override fun render(): String = accessorSignature.render()

        override fun equals(other: Any?): Boolean {
            if (other is AccessorSignature) return accessorSignature == other.accessorSignature
            return accessorSignature == other
        }

        override fun hashCode(): Int = accessorSignature.hashCode()
    }

    data class FileLocalSignature(val container: IdSignature, val id: Long) : IdSignature() {
        override val isPublic = false

        override fun packageFqName(): FqName = container.packageFqName()

        override fun topLevelSignature(): IdSignature {
            val topLevelContainer = container.topLevelSignature()
            if (topLevelContainer === container) {
                if (topLevelContainer is PublicSignature && topLevelContainer.classFqn.isRoot) {
                    // private top level
                    return this
                }
            }
            return topLevelContainer
        }

        override fun nearestPublicSig(): IdSignature = container.nearestPublicSig()

        override fun render(): String = "${container.render()}:$id"

        override fun toString() = super.toString()
    }

    class BuiltInSignature(val mangle: String, val id: Long) : IdSignature() {
        constructor(id: Long) : this("", id)

        override fun topLevelSignature(): IdSignature = this // built ins are always top level
        override fun nearestPublicSig(): IdSignature = this

        override fun packageFqName(): FqName = IrBuiltIns.KOTLIN_INTERNAL_IR_FQN

        override val isPublic: Boolean = true
        override fun render(): String = "<ß|$mangle>"

        override fun equals(other: Any?): Boolean {
            return this === other || other is BuiltInSignature && id == other.id
        }

        override fun hashCode(): Int = id.toInt()
    }
}