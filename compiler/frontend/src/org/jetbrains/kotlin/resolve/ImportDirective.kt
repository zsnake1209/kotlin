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

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.renderer.render

val Import.hasAlias get() = alias != null
val Import.importedName: Name? get() = if (isAllUnder) null else (alias ?: fqName.shortName())

fun Import.getText(): String {
    val fqNameStr = fqName.toUnsafe().render()
    val pathStr = fqNameStr + if (isAllUnder) ".*" else ""
    val alias = alias
    return pathStr + if (alias != null && !isAllUnder) (" as " + alias.asString()) else ""
}

interface Import {
    val fqName: FqName
    val isAllUnder: Boolean
    val alias: Name?
}

interface ImportDirective : Import {
    val psi: KtImportDirective
}

data class ImportDirectiveImpl(
    override val fqName: FqName,
    override val isAllUnder: Boolean,
    override val alias: Name? = null,
    override val psi: KtImportDirective
) : ImportDirective

data class FakeImportDirective @JvmOverloads constructor(
        override val fqName: FqName,
        override val isAllUnder: Boolean = false,
        override val alias: Name? = null,
        val project: Project
): ImportDirective {
    override val psi by lazy {
        KtPsiFactory(project).createImportDirective(this)
    }

    companion object {
        @JvmStatic
        fun fromString(pathStr: String, reportOn: PsiElement): FakeImportDirective = fromString(pathStr, reportOn.project)

        @JvmStatic
        fun fromString(pathStr: String, project: Project): FakeImportDirective {
            val isAllUnder = pathStr.endsWith(".*")
            val fqName = if (isAllUnder) FqName(pathStr.substring(0, pathStr.length - 2)) else FqName(pathStr)

            return FakeImportDirective(fqName, isAllUnder, project = project)
        }
    }
}

fun ImportDirective.toImportPath() = ImportPath(fqName, isAllUnder, alias)


data class ImportPath @JvmOverloads constructor(
        override val fqName: FqName,
        override val isAllUnder: Boolean = false,
        override val alias: Name? = null
): Import {
    override fun toString(): String = getText()

    companion object {
        @JvmStatic
        fun fromString(pathStr: String): ImportPath {
            val isAllUnder = pathStr.endsWith(".*")
            val fqName = if (isAllUnder) FqName(pathStr.substring(0, pathStr.length - 2)) else FqName(pathStr)

            return ImportPath(fqName, isAllUnder)
        }
    }
}