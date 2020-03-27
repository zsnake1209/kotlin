/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.util.diff.FlyweightCapableTreeStructure
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

interface FirSourceElement {
    val elementType: IElementType
    val startOffset: Int
    val endOffset: Int
}

abstract class FirAbstractSourceElement : FirSourceElement

interface FirClassSourceElement : FirSourceElement

interface FirNamedFunctionSourceElement : FirSourceElement

interface FirPropertySourceElement : FirSourceElement

interface FirConstructorSourceElement : FirSourceElement

interface FirModifierListSourceElement : FirSourceElement {
    val modifiers: List<FirModifier<*>>
}

open class FirPsiSourceElement<P : PsiElement>(val psi: P) : FirAbstractSourceElement() {
    override val elementType: IElementType
        get() = psi.node.elementType

    override val startOffset: Int
        get() = psi.startOffset

    override val endOffset: Int
        get() = psi.endOffset
}

class FirPsiClassElement<P : KtClassOrObject>(psi: P) : FirPsiSourceElement<P>(psi), FirClassSourceElement

class FirPsiNamedFunctionElement<P : KtNamedFunction>(psi: P) : FirPsiSourceElement<P>(psi), FirNamedFunctionSourceElement

class FirPsiPropertyElement<P : KtProperty>(psi: P) : FirPsiSourceElement<P>(psi), FirPropertySourceElement

class FirPsiConstructorElement<P : KtConstructor<*>>(psi: P) : FirPsiSourceElement<P>(psi), FirConstructorSourceElement

private val MODIFIER_KEYWORD_SET = TokenSet.orSet(KtTokens.SOFT_KEYWORDS, TokenSet.create(KtTokens.IN_KEYWORD, KtTokens.FUN_KEYWORD))

class FirPsiModifierListElement<P : KtModifierList>(psi: P) : FirPsiSourceElement<P>(psi), FirModifierListSourceElement {
    override val modifiers: List<FirPsiModifier>
        get() = psi.node.getChildren(MODIFIER_KEYWORD_SET).map { node ->
            FirPsiModifier(node, node.elementType as KtModifierKeywordToken)
        }
}

open class FirLightSourceElement(
    val element: LighterASTNode,
    override val startOffset: Int,
    override val endOffset: Int,
    val tree: FlyweightCapableTreeStructure<LighterASTNode>
) : FirAbstractSourceElement() {
    override val elementType: IElementType
        get() = element.tokenType
}

class FirLightClassElement(
    element: LighterASTNode,
    startOffset: Int, endOffset: Int,
    tree: FlyweightCapableTreeStructure<LighterASTNode>
) : FirLightSourceElement(element, startOffset, endOffset, tree), FirClassSourceElement

class FirLightNamedFunctionElement(
    element: LighterASTNode,
    startOffset: Int, endOffset: Int,
    tree: FlyweightCapableTreeStructure<LighterASTNode>
) : FirLightSourceElement(element, startOffset, endOffset, tree), FirNamedFunctionSourceElement

class FirLightPropertyElement(
    element: LighterASTNode,
    startOffset: Int, endOffset: Int,
    tree: FlyweightCapableTreeStructure<LighterASTNode>
) : FirLightSourceElement(element, startOffset, endOffset, tree), FirPropertySourceElement

class FirLightConstructorElement(
    element: LighterASTNode,
    startOffset: Int, endOffset: Int,
    tree: FlyweightCapableTreeStructure<LighterASTNode>
) : FirLightSourceElement(element, startOffset, endOffset, tree), FirConstructorSourceElement

class FirLightModifierListElement(
    element: LighterASTNode,
    startOffset: Int, endOffset: Int,
    tree: FlyweightCapableTreeStructure<LighterASTNode>
) : FirLightSourceElement(element, startOffset, endOffset, tree), FirModifierListSourceElement {
    override val modifiers: List<FirLightModifier>
        get() {
            val kidsRef = Ref<Array<LighterASTNode?>>()
            tree.getChildren(element, kidsRef)
            val modifierNodes = kidsRef.get()
            return modifierNodes.filterNotNull()
                .filter { it.tokenType is KtModifierKeywordToken }
                .map { FirLightModifier(it, it.tokenType as KtModifierKeywordToken, tree) }
        }
}

val FirSourceElement?.psi: PsiElement? get() = (this as? FirPsiSourceElement<*>)?.psi

val FirElement.psi: PsiElement? get() = (source as? FirPsiSourceElement<*>)?.psi

fun PsiElement.toFirPsiSourceElement(): FirPsiSourceElement<*> = when (this) {
    is KtClassOrObject -> FirPsiClassElement(this)
    is KtNamedFunction -> FirPsiNamedFunctionElement(this)
    is KtProperty -> FirPsiPropertyElement(this)
    is KtConstructor<*> -> FirPsiConstructorElement(this)
    is KtModifierList -> FirPsiModifierListElement(this)
    else -> FirPsiSourceElement(this)
}

fun LighterASTNode.toFirLightSourceElement(
    startOffset: Int, endOffset: Int,
    tree: FlyweightCapableTreeStructure<LighterASTNode>
): FirLightSourceElement = when (this.tokenType) {
    KtNodeTypes.CLASS, KtNodeTypes.OBJECT_DECLARATION ->
        FirLightClassElement(this, startOffset, endOffset, tree)
    KtNodeTypes.PRIMARY_CONSTRUCTOR, KtNodeTypes.SECONDARY_CONSTRUCTOR ->
        FirLightConstructorElement(this, startOffset, endOffset, tree)
    KtNodeTypes.FUN ->
        FirLightNamedFunctionElement(this, startOffset, endOffset, tree)
    KtNodeTypes.PROPERTY ->
        FirLightPropertyElement(this, startOffset, endOffset, tree)
    KtNodeTypes.MODIFIER_LIST ->
        FirLightModifierListElement(this, startOffset, endOffset, tree)
    else ->
        FirLightSourceElement(this, startOffset, endOffset, tree)
}

val FirSourceElement?.lightNode: LighterASTNode? get() = (this as? FirLightSourceElement)?.element

fun FirSourceElement?.getModifierList(): FirModifierListSourceElement? {
    return when (this) {
        is FirPsiSourceElement<*> -> (psi as? KtModifierListOwner)?.modifierList?.let { FirPsiModifierListElement(it) }
        is FirLightSourceElement -> {
            val kidsRef = Ref<Array<LighterASTNode?>>()
            tree.getChildren(element, kidsRef)
            val modifierListNode = kidsRef.get().find { it?.tokenType == KtNodeTypes.MODIFIER_LIST } ?: return null
            FirLightModifierListElement(modifierListNode, tree.getStartOffset(modifierListNode), tree.getEndOffset(modifierListNode), tree)
        }
        else -> null
    }
}
