/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ConflictingJvmDeclarationsData
import org.jetbrains.kotlin.resolve.jvm.diagnostics.ErrorsJvm
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOriginKind

class FilteredJvmDiagnostics(val jvmDiagnostics: Diagnostics, val otherDiagnostics: Diagnostics) : Diagnostics by jvmDiagnostics {

    private fun alreadyReported(psiElement: PsiElement): Boolean {
        val higherPriority = setOf<DiagnosticFactory<*>>(
            Errors.CONFLICTING_OVERLOADS, Errors.REDECLARATION, Errors.NOTHING_TO_OVERRIDE, Errors.MANY_IMPL_MEMBER_NOT_IMPLEMENTED
        )
        return otherDiagnostics.forElement(psiElement).any { it.factory in higherPriority }
                || psiElement is KtPropertyAccessor && alreadyReported(psiElement.parent)
    }

    override fun forElement(psiElement: PsiElement): Collection<Diagnostic> {
        val jvmDiagnosticFactories = setOf(
            ErrorsJvm.CONFLICTING_JVM_DECLARATIONS,
            ErrorsJvm.ACCIDENTAL_OVERRIDE,
            ErrorsJvm.CONFLICTING_INHERITED_JVM_DECLARATIONS
        )
        fun Diagnostic.data() = DiagnosticFactory.cast(this, jvmDiagnosticFactories).a
        val (conflicting, other) = jvmDiagnostics.forElement(psiElement).partition { it.factory in jvmDiagnosticFactories }
        if (alreadyReported(psiElement)) {
            // CONFLICTING_OVERLOADS already reported, no need to duplicate it
            return other
        }

        val filtered = arrayListOf<Diagnostic>()
        conflicting.groupBy {
            it.data().signature.name
        }.forEach {
                val diagnostics = it.value
                if (diagnostics.size <= 1) {
                    filtered.addAll(diagnostics)
                }
                else {
                    filtered.addAll(
                        diagnostics.filter {
                                me ->
                            diagnostics.none {
                                    other ->
                                me != other && (
                                        // in case of implementation copied from a super trait there will be both diagnostics on the same signature
                                        other.factory == ErrorsJvm.CONFLICTING_JVM_DECLARATIONS && (me.factory == ErrorsJvm.ACCIDENTAL_OVERRIDE ||
                                                me.factory == ErrorsJvm.CONFLICTING_INHERITED_JVM_DECLARATIONS)
                                                // there are paris of corresponding signatures that frequently clash simultaneously: multifile class & part, trait and trait-impl
                                                || other.data().higherThan(me.data())
                                        )
                            }
                        }
                    )
                }
            }

        return filtered + other
    }

    override fun all(): Collection<Diagnostic> {
        return jvmDiagnostics.all()
            .map { it.psiElement }
            .toSet()
            .flatMap { forElement(it) }
    }
}

private infix fun ConflictingJvmDeclarationsData.higherThan(other: ConflictingJvmDeclarationsData): Boolean {
    return when (other.classOrigin.originKind) {
        JvmDeclarationOriginKind.INTERFACE_DEFAULT_IMPL -> this.classOrigin.originKind != JvmDeclarationOriginKind.INTERFACE_DEFAULT_IMPL
        JvmDeclarationOriginKind.MULTIFILE_CLASS_PART -> this.classOrigin.originKind == JvmDeclarationOriginKind.MULTIFILE_CLASS
        else -> false
    }
}