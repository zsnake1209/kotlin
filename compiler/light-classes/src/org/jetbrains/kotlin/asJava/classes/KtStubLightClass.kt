/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.asJava.classes

import com.intellij.openapi.util.Pair
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.javadoc.PsiDocComment
import org.jetbrains.kotlin.asJava.hasInterfaceDefaultImpls
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.load.java.structure.LightClassOriginKind
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtScript
import java.util.*

abstract class KtStubLightClass(manager: PsiManager) : LightElement(manager, KotlinLanguage.INSTANCE), KtLightClass, PsiClass {
    override fun toString(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    abstract override fun getName(): String?

    override fun hasModifierProperty(name: String) = false // TODO:

    override fun getInnerClasses(): Array<PsiClass> = emptyArray() // TODO

    override fun findMethodBySignature(patternMethod: PsiMethod?, checkBases: Boolean) = null

    override fun findInnerClassByName(name: String?, checkBases: Boolean): PsiClass? = null //

    override fun getExtendsListTypes(): Array<PsiClassType> = emptyArray()

    override fun getTypeParameterList(): PsiTypeParameterList? = null

    override fun isAnnotationType() = false // TODO:

    override fun getNameIdentifier(): PsiIdentifier? = null // TODO:

    override fun getFields(): Array<PsiField> = emptyArray()

    override fun getSuperClass(): PsiClass? = null

    override fun getSupers(): Array<PsiClass> = emptyArray()

    override fun findMethodsAndTheirSubstitutorsByName(name: String?, checkBases: Boolean) = emptyList<Pair<PsiMethod, PsiSubstitutor>>()

    override fun getImplementsList() = null

    override fun getSuperTypes(): Array<PsiClassType> = emptyArray()

    override fun getMethods(): Array<PsiMethod> = emptyArray()

    override fun getRBrace(): PsiElement? = null

    override fun getLBrace(): PsiElement? = null

    override fun getInitializers(): Array<PsiClassInitializer> = emptyArray()

    override fun getContainingClass(): PsiClass? = null // TODO:

    override fun isInheritorDeep(baseClass: PsiClass?, classToByPass: PsiClass?): Boolean = false

    override fun getAllMethodsAndTheirSubstitutors(): List<Pair<PsiMethod, PsiSubstitutor>> = emptyList()

    override fun isInterface(): Boolean = false // TODO:

    override fun getTypeParameters(): Array<PsiTypeParameter> = emptyArray() // TODO:

    override fun getInterfaces(): Array<PsiClass> = emptyArray()// TODO:

    override fun isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = false // TODO:

    override fun findFieldByName(name: String?, checkBases: Boolean): PsiField? = null

    override fun getAllFields(): Array<PsiField> = emptyArray()

    override fun hasTypeParameters(): Boolean = false //TODO:

    override fun getAllInnerClasses() = emptyArray<PsiClass>()

    override fun getExtendsList(): PsiReferenceList? = null

    override fun getVisibleSignatures() = emptyList<HierarchicalMethodSignature>()

    override fun isEnum(): Boolean = false

    override fun findMethodsByName(name: String?, checkBases: Boolean) = emptyArray<PsiMethod>()

    override fun getDocComment(): PsiDocComment? = null

    override fun getAllMethods(): Array<PsiMethod> = emptyArray()

    override fun getModifierList(): PsiModifierList? = null

    override fun getScope(): PsiElement = parent

    override fun getImplementsListTypes() = emptyArray<PsiClassType>()

    override fun getConstructors() = emptyArray<PsiMethod>()

    override fun isDeprecated(): Boolean = false

    override fun setName(name: String) = cannotModify()

    override fun findMethodsBySignature(patternMethod: PsiMethod?, checkBases: Boolean) = emptyArray<PsiMethod>()

    override val kotlinOrigin: KtClassOrObject?
        get() = error("Should not be called")
    override val clsDelegate: PsiClass
        get() = error("Should not be called")
    override val originKind: LightClassOriginKind
        get() = error("Should not be called")


    class ForFacade(private val facadeFqName: FqName, manager: PsiManager) : KtStubLightClass(manager) {
        override fun getQualifiedName(): String? = facadeFqName.asString()
        override fun getName(): String? = facadeFqName.shortName().asString()
    }

    class ForClassOrObject(private val classOrObject: KtClassOrObject, manager: PsiManager) : KtStubLightClass(manager) {
        override fun getQualifiedName() = classOrObject.fqName?.asString()
        override fun getName() = classOrObject.fqName?.shortName()?.asString()
        override fun getInnerClasses(): Array<PsiClass> {
            val result = ArrayList<PsiClass>()
            classOrObject.declarations.filterIsInstance<KtClassOrObject>()
                .mapTo(result) { KtStubLightClass.ForClassOrObject(it, manager) }

            if (classOrObject.hasInterfaceDefaultImpls) {
                // TODO:
            }
            return result.toTypedArray()
        }

        override fun findInnerClassByName(name: String?, checkBases: Boolean): PsiClass? {
            return classOrObject.declarations.filterIsInstance<KtClassOrObject>().find { it.name == name }
                ?.let { KtStubLightClass.ForClassOrObject(it, manager) }
        }
    }

    class ForScript(private val script: KtScript, manager: PsiManager) : KtStubLightClass(manager) {
        override fun getName() = script.fqName.shortName().asString()
        override fun getQualifiedName() = script.fqName.asString()
    }
}