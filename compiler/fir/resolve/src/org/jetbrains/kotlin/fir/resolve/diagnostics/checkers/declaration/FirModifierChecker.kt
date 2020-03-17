/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.diagnostics.checkers.declaration

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.psi
import org.jetbrains.kotlin.fir.resolve.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.resolve.diagnostics.onSource
import org.jetbrains.kotlin.fir.toFirSourceElement
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtModifierListOwner

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
        firstNode: ASTNode,
        secondNode: ASTNode,
        modifiersOwner: PsiElement,
        reporter: DiagnosticReporter,
        reportedNodes: MutableSet<ASTNode>
    ) {
        val firstKeyword = firstNode.elementType as KtModifierKeywordToken
        val secondKeyword = secondNode.elementType as KtModifierKeywordToken
        when (val compatibilityType = deduceCompatibilityType(firstKeyword, secondKeyword)) {
            CompatibilityType.COMPATIBLE -> {}
            CompatibilityType.REPEATED ->
                if (reportedNodes.add(secondNode)) reporter.reportRepeatedModifier(secondNode.psi, secondKeyword)
            CompatibilityType.REDUNDANT_2_TO_1 ->
                reporter.reportRedundantModifier(secondNode.psi, secondKeyword, firstKeyword)
            CompatibilityType.REDUNDANT_1_TO_2 ->
                reporter.reportRedundantModifier(firstNode.psi, firstKeyword, secondKeyword)
            CompatibilityType.DEPRECATED -> {
                reporter.reportDeprecatedModifierPair(firstNode.psi, firstKeyword, secondKeyword)
                reporter.reportDeprecatedModifierPair(secondNode.psi, secondKeyword, firstKeyword)
            }
            CompatibilityType.INCOMPATIBLE, CompatibilityType.COMPATIBLE_FOR_CLASSES -> {
                if (compatibilityType == CompatibilityType.COMPATIBLE_FOR_CLASSES && modifiersOwner is KtClassOrObject) {
                    return
                }
                if (reportedNodes.add(firstNode)) reporter.reportIncompatibleModifiers(firstNode.psi, firstKeyword, secondKeyword)
                if (reportedNodes.add(secondNode)) reporter.reportIncompatibleModifiers(secondNode.psi, secondKeyword, firstKeyword)
            }
        }
    }

    private val MODIFIER_KEYWORD_SET = TokenSet.orSet(KtTokens.SOFT_KEYWORDS, TokenSet.create(KtTokens.IN_KEYWORD, KtTokens.FUN_KEYWORD))

    private fun checkModifiers(
        list: KtModifierList,
        reporter: DiagnosticReporter
    ) {
        if (list.stub != null) return

        val reportedNodes = hashSetOf<ASTNode>()

        val modifiers = list.node.getChildren(MODIFIER_KEYWORD_SET)
        for (secondKeyword in modifiers) {
            for (firstKeyword in modifiers) {
                if (firstKeyword == secondKeyword) {
                    break
                }
                checkCompatibilityType(firstKeyword, secondKeyword, list.owner, reporter, reportedNodes)
            }
            // TODO
        }
    }

    private var lastPsiElement: PsiElement? = null

    override fun check(declaration: FirDeclaration, reporter: DiagnosticReporter) {
        val psiElement = declaration.source?.psi ?: return
        if (psiElement !== lastPsiElement && psiElement is KtModifierListOwner) {
            val modifierList = psiElement.modifierList
            modifierList?.let { checkModifiers(modifierList, reporter) }
            lastPsiElement = psiElement
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