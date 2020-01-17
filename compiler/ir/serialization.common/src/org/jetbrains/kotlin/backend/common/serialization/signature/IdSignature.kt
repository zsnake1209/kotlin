/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.name.FqName

sealed class IdSignature {

    abstract val isPublic: Boolean

    abstract fun topLevelSignature(): IdSignature
    abstract fun nearestPublicSig(): IdSignature

    abstract fun render(): String

    override fun toString(): String {
        return "${if (isPublic) "public" else "private"} ${render()}"
    }

    data class PublicSignature(val packageFqn: FqName, val classFqn: FqName, val id: Long?, val mask: Long) : IdSignature() {
        override val isPublic = true

        override fun topLevelSignature(): IdSignature {
            if (classFqn.isRoot) {
                assert(id == null)
                // package signature
                return this
            }

            val topLevelFqn = FqName(classFqn.pathSegments().first().asString())
            if (topLevelFqn == classFqn) {
                if (id != null) return this // Top level functions & properties
            }

            return PublicSignature(packageFqn, topLevelFqn, null, 0)
        }
        override fun nearestPublicSig(): PublicSignature = this

        override fun render(): String = "${packageFqn.asString().replace('.', '/')}/${classFqn.asString()}|$id[${mask.toString(2)}]"

        override fun toString() = super.toString()
    }

    data class FileLocalSignature(val container: IdSignature, val id: Long) : IdSignature() {
        override val isPublic = false

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

        override val isPublic: Boolean = true
        override fun render(): String = "<ß|$mangle>"

        override fun equals(other: Any?): Boolean {
            return this === other || other is BuiltInSignature && id == other.id
        }

        override fun hashCode(): Int = id.toInt()
    }
}