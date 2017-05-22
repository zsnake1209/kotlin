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

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.renderer.render

val ImportDirective.hasAlias get() = alias != null
val ImportDirective.importedName: Name? get() = if (isAllUnder) null else (alias ?: fqName.shortName())

fun ImportDirective.getText(): String {
    val fqNameStr = fqName.toUnsafe().render()
    val pathStr = fqNameStr + if (isAllUnder) ".*" else ""
    return pathStr + if (alias != null && !isAllUnder) (" as " + alias.asString()) else ""
}

class ImportDirective @JvmOverloads constructor(
        val fqName: FqName,
        val isAllUnder: Boolean,
        val alias: Name? = null,
        val psi: KtImportDirective? = null) {
    override fun toString(): String = getText()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as ImportDirective

        if (fqName != other.fqName) return false
        if (isAllUnder != other.isAllUnder) return false
        if (alias != other.alias) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fqName.hashCode()
        result = 31 * result + isAllUnder.hashCode()
        result = 31 * result + (alias?.hashCode() ?: 0)
        return result
    }

    companion object {
        @JvmStatic fun fromString(pathStr: String): ImportDirective {
            if (pathStr.endsWith(".*")) {
                return ImportDirective(FqName(pathStr.substring(0, pathStr.length - 2)), isAllUnder = true)
            }
            else {
                return ImportDirective(FqName(pathStr), isAllUnder = false)
            }
        }
    }
}
