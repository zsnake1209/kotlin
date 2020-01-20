/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common.serialization.signature

import org.jetbrains.kotlin.name.FqName

abstract class IdSignatureBuilder<D> {
    protected var packageFqn = FqName.ROOT
    protected val classFanSegments = mutableListOf<String>()
    protected var hash_id: Long? = null
    protected var hash_id_acc: Long? = null
    protected var mask = 0L

    protected abstract fun accept(d: D)

    protected fun reset() {
        this.packageFqn = FqName.ROOT
        this.classFanSegments.clear()
        this.hash_id = null
        this.mask = 0L
    }

    protected fun build(): IdSignature {
        return if (hash_id_acc == null) {
            IdSignature.PublicSignature(packageFqn, FqName.fromSegments(classFanSegments), hash_id, mask)
        } else {
            val accessorSignature = IdSignature.PublicSignature(packageFqn, FqName.fromSegments(classFanSegments), hash_id_acc, mask)
            hash_id_acc = null
            classFanSegments.run { removeAt(lastIndex) }
            val propertySignature = build()
            IdSignature.AccessorSignature(propertySignature, accessorSignature)
        }
    }


    protected fun setExpected(f: Boolean) {
        mask = mask or IdSignatureFlags.IS_EXPECT.encode(f)
    }

    fun buildSignature(declaration: D): IdSignature {
        reset()

        accept(declaration)

        return build()
    }
}