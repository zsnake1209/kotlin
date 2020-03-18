/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.diagnostics.checkers.declaration

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.fir.*
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.resolve.diagnostics.onSource
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.psi.KtClassOrObject

object FirModifierChecker : FirDeclarationChecker<FirDeclaration>() {

    private enum class CompatibilityType {
        COMPATIBLE,
        COMPATIBLE_FOR_CLASSES, // for functions and properties: error
        REDUNDANT_2_TO_1,       // second is redundant to first: warning
        REDUNDANT_1_TO_2,       // first is redundant to second: warning
        DEPRECATED,             // pair is deprecated and will soon become incompatible: warning
        REPEATED,               // first and second are the same: error
        INCOMPATIBLE,           // pair is incompatible: error
    }

    private val compatibilityTypeMap = hashMapOf<Pair<KtModifierKeywordToken, KtModifierKeywordToken>, CompatibilityType>()

    private fun recordCompatibilityType(compatibilityType: CompatibilityType, vararg list: KtModifierKeywordToken) {
        for (firstKeyword in list) {
            for (secondKeyword in list) {
                if (firstKeyword != secondKeyword) {
                    compatibilityTypeMap[Pair(firstKeyword, secondKeyword)] = compatibilityType
                }
            }
        }
    }

    private fun recordCompatibilityForClasses(vararg list: KtModifierKeywordToken) {
        recordCompatibilityType(CompatibilityType.COMPATIBLE_FOR_CLASSES, *list)
    }

    private fun recordDeprecation(vararg list: KtModifierKeywordToken) {
        recordCompatibilityType(CompatibilityType.DEPRECATED, *list)
    }

    private fun recordIncompatibility(vararg list: KtModifierKeywordToken) {
        recordCompatibilityType(CompatibilityType.INCOMPATIBLE, *list)
    }

    private fun recordRedundancy(sufficientKeyword: KtModifierKeywordToken, redundantKeyword: KtModifierKeywordToken) {
        compatibilityTypeMap[Pair(sufficientKeyword, redundantKeyword)] = CompatibilityType.REDUNDANT_2_TO_1
        compatibilityTypeMap[Pair(redundantKeyword, sufficientKeyword)] = CompatibilityType.REDUNDANT_1_TO_2
    }

    init {

    }

    private fun deduceCompatibilityType(firstKeyword: KtModifierKeywordToken, secondKeyword: KtModifierKeywordToken): CompatibilityType =
        if (firstKeyword == secondKeyword) {
            CompatibilityType.REPEATED
        } else {
            compatibilityTypeMap[Pair(firstKeyword, secondKeyword)] ?: CompatibilityType.COMPATIBLE
        }

    private fun checkCompatibilityType(
        firstModifier: FirModifier<*>,
        secondModifier: FirModifier<*>,
        modifiersOwner: FirDeclaration,
        reporter: DiagnosticReporter,
        reportedNodes: MutableSet<FirModifier<*>>
    ) {
        val firstToken = firstModifier.token
        val secondToken = secondModifier.token
        when (val compatibilityType = deduceCompatibilityType(firstToken, secondToken)) {
            CompatibilityType.COMPATIBLE -> {}
            CompatibilityType.REPEATED ->
                if (reportedNodes.add(secondModifier)) reporter.reportRepeatedModifier(secondModifier.psi, secondToken)
            CompatibilityType.REDUNDANT_2_TO_1 ->
                reporter.reportRedundantModifier(secondModifier.psi, secondToken, firstToken)
            CompatibilityType.REDUNDANT_1_TO_2 ->
                reporter.reportRedundantModifier(firstModifier.psi, firstToken, secondToken)
            CompatibilityType.DEPRECATED -> {
                reporter.reportDeprecatedModifierPair(firstModifier.psi, firstToken, secondToken)
                reporter.reportDeprecatedModifierPair(secondModifier.psi, secondToken, firstToken)
            }
            CompatibilityType.INCOMPATIBLE, CompatibilityType.COMPATIBLE_FOR_CLASSES -> {
                if (compatibilityType == CompatibilityType.COMPATIBLE_FOR_CLASSES && modifiersOwner is KtClassOrObject) {
                    return
                }
                if (reportedNodes.add(firstModifier)) reporter.reportIncompatibleModifiers(firstModifier.psi, firstToken, secondToken)
                if (reportedNodes.add(secondModifier)) reporter.reportIncompatibleModifiers(secondModifier.psi, secondToken, firstToken)
            }
        }
    }

    private fun checkModifiers(
        list: FirModifierList,
        owner: FirDeclaration,
        reporter: DiagnosticReporter
    ) {
        val reportedNodes = hashSetOf<FirModifier<*>>()

        val modifiers = list.modifiers
        for (secondModifier in modifiers) {
            for (firstModifier in modifiers) {
                if (firstModifier == secondModifier) {
                    break
                }
                checkCompatibilityType(firstModifier, secondModifier, owner, reporter, reportedNodes)
            }
            // TODO
        }
    }

    override fun check(declaration: FirDeclaration, reporter: DiagnosticReporter) {
        val source = declaration.source ?: return
        when {
            declaration !is FirClass<*> && source.elementType == KtNodeTypes.CLASS -> return
        }
        val modifierList = source.getModifierList()
        if (modifierList != null) {
            checkModifiers(modifierList, declaration, reporter)
        }
    }

    private fun DiagnosticReporter.reportRepeatedModifier(
        source: PsiElement?, keyword: KtModifierKeywordToken
    ) {
        source?.let { report(Errors.REPEATED_MODIFIER.onSource(it.toFirSourceElement(), keyword)) }
    }

    private fun DiagnosticReporter.reportRedundantModifier(
        source: PsiElement?, firstKeyword: KtModifierKeywordToken, secondKeyword: KtModifierKeywordToken
    ) {
        source?.let { report(Errors.REDUNDANT_MODIFIER.onSource(it.toFirSourceElement(), firstKeyword, secondKeyword)) }
    }

    private fun DiagnosticReporter.reportDeprecatedModifierPair(
        source: PsiElement?, firstKeyword: KtModifierKeywordToken, secondKeyword: KtModifierKeywordToken
    ) {
        source?.let { report(Errors.DEPRECATED_MODIFIER_PAIR.onSource(it.toFirSourceElement(), firstKeyword, secondKeyword)) }
    }

    private fun DiagnosticReporter.reportIncompatibleModifiers(
        source: PsiElement?, firstKeyword: KtModifierKeywordToken, secondKeyword: KtModifierKeywordToken
    ) {
        source?.let { report(Errors.INCOMPATIBLE_MODIFIERS.onSource(it.toFirSourceElement(), firstKeyword, secondKeyword)) }
    }

}