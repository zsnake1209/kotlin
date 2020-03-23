/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.fir.FirPsiSourceElement
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.onSource
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.psi.KtConstructor

object FirConstructorChecker : FirDeclarationChecker<FirConstructor>() {
    override fun check(declaration: FirConstructor, context: CheckerContext, reporter: DiagnosticReporter) {
        val containingClass = context.containingDeclarations.lastOrNull() as? FirClass<*> ?: return
        val source = declaration.source
        if (source is FirPsiSourceElement && source.psi !is KtConstructor<*>) {
            return
        }
        when (containingClass.classKind) {
            ClassKind.OBJECT -> reporter.report(source, Errors.CONSTRUCTOR_IN_OBJECT)
            ClassKind.INTERFACE -> reporter.report(source, Errors.CONSTRUCTOR_IN_INTERFACE)
            ClassKind.ENUM_ENTRY -> reporter.report(source, Errors.CONSTRUCTOR_IN_OBJECT)
            ClassKind.ENUM_CLASS -> if (declaration.visibility != Visibilities.PRIVATE) {
                reporter.report(source, Errors.NON_PRIVATE_CONSTRUCTOR_IN_ENUM)
            }
            ClassKind.CLASS -> if (containingClass is FirRegularClass && containingClass.modality == Modality.SEALED &&
                declaration.visibility != Visibilities.PRIVATE
            ) {
                reporter.report(source, Errors.NON_PRIVATE_CONSTRUCTOR_IN_SEALED)
            }
            ClassKind.ANNOTATION_CLASS -> {
                // DO NOTHING
            }
        }
    }

    private inline fun <reified T : PsiElement> DiagnosticReporter.report(source: FirSourceElement?, factory: DiagnosticFactory0<T>) {
        source?.let { report(factory.onSource(it)) }
    }
}