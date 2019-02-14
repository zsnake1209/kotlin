/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.psi.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo
import com.intellij.refactoring.util.MoveRenameUsageInfo
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewUtil
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.idea.analysis.analyzeInContext
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.unsafeResolveToDescriptor
import org.jetbrains.kotlin.idea.codeInsight.DescriptorToSourceUtilsIde
import org.jetbrains.kotlin.idea.core.NewDeclarationNameValidator
import org.jetbrains.kotlin.idea.core.copied
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.project.TargetPlatformDetector
import org.jetbrains.kotlin.idea.refactoring.explicateAsText
import org.jetbrains.kotlin.idea.refactoring.getThisLabelName
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.and
import org.jetbrains.kotlin.idea.search.restrictToKotlinSources
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.idea.util.getAllAccessibleFunctions
import org.jetbrains.kotlin.idea.util.getAllAccessibleVariables
import org.jetbrains.kotlin.idea.util.getResolutionScope
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.js.resolve.JsPlatform
import org.jetbrains.kotlin.js.resolve.JsTypeSpecificityComparator
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace
import org.jetbrains.kotlin.resolve.OverloadChecker
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.calls.resolvedCallUtil.getExplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.resolvedCallUtil.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.calls.results.TypeSpecificityComparator
import org.jetbrains.kotlin.resolve.descriptorUtil.getImportableDescriptor
import org.jetbrains.kotlin.resolve.jvm.JvmTypeSpecificityComparator
import org.jetbrains.kotlin.resolve.jvm.platform.JvmPlatform
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.scopes.utils.findClassifier
import org.jetbrains.kotlin.resolve.scopes.utils.getImplicitReceiversHierarchy
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.ErrorUtils
import org.jetbrains.kotlin.utils.SmartList
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

internal fun ResolvedCall<*>.noReceivers() = dispatchReceiver == null && extensionReceiver == null

internal fun PsiNamedElement.renderDescription(): String {
    val type = UsageViewUtil.getType(this)
    if (name == null || name!!.startsWith("<")) return type
    return "$type '$name'".trim()
}

internal fun PsiElement.representativeContainer(): PsiNamedElement? =
        when (this) {
            is KtDeclaration -> containingClassOrObject
                                ?: getStrictParentOfType<KtNamedDeclaration>()
                                ?: JavaPsiFacade.getInstance(project).findPackage(containingKtFile.packageFqName.asString())
            is PsiMember -> containingClass
            else -> null
        }

internal fun DeclarationDescriptor.canonicalRender(): String = DescriptorRenderer.FQ_NAMES_IN_TYPES.render(this)

internal fun checkRedeclarations(
        descriptor: DeclarationDescriptor,
        newName: String,
        result: MutableList<UsageInfo>
) {
    fun DeclarationDescriptor.isTopLevelPrivate(): Boolean {
        return this is DeclarationDescriptorWithVisibility
               && visibility == Visibilities.PRIVATE
               && containingDeclaration is PackageFragmentDescriptor
    }

    fun isInSameFile(d1: DeclarationDescriptor, d2: DeclarationDescriptor): Boolean {
        return (d1 as? DeclarationDescriptorWithSource)?.source?.getPsi()?.containingFile == (d2 as? DeclarationDescriptorWithSource)?.source?.getPsi()?.containingFile
    }

    fun MemberScope.findSiblingsByName(): List<DeclarationDescriptor> {
        val descriptorKindFilter = when (descriptor) {
            is ClassifierDescriptor -> DescriptorKindFilter.CLASSIFIERS
            is VariableDescriptor -> DescriptorKindFilter.VARIABLES
            is FunctionDescriptor -> DescriptorKindFilter.FUNCTIONS
            else -> return emptyList()
        }
        return getDescriptorsFiltered(descriptorKindFilter) { it.asString() == newName }.filter { it != descriptor }
    }

    fun getSiblingsWithNewName(): List<DeclarationDescriptor> {
        val containingDescriptor = descriptor.containingDeclaration

        if (descriptor is ValueParameterDescriptor) {
            return (containingDescriptor as CallableDescriptor).valueParameters.filter { it.name.asString() == newName }
        }

        if (descriptor is TypeParameterDescriptor) {
            val typeParameters = when (containingDescriptor) {
                is ClassDescriptor -> containingDescriptor.declaredTypeParameters
                is CallableDescriptor -> containingDescriptor.typeParameters
                else -> emptyList()
            }

            return SmartList<DeclarationDescriptor>().apply {
                typeParameters.filterTo(this) { it.name.asString() == newName }
                val containingDeclaration = (containingDescriptor as? DeclarationDescriptorWithSource)?.source?.getPsi() as? KtDeclaration
                        ?: return emptyList()
                val dummyVar = KtPsiFactory(containingDeclaration).createProperty("val foo: $newName")
                val outerScope = containingDeclaration.getResolutionScope()
                val context = dummyVar.analyzeInContext(outerScope, containingDeclaration)
                addIfNotNull(context[BindingContext.VARIABLE, dummyVar]?.type?.constructor?.declarationDescriptor)
            }
        }

        return when (containingDescriptor) {
            is ClassDescriptor -> containingDescriptor.unsubstitutedMemberScope.findSiblingsByName()
            is PackageFragmentDescriptor -> containingDescriptor.getMemberScope().findSiblingsByName().filter {
                it != descriptor
                && (!(descriptor.isTopLevelPrivate() || it.isTopLevelPrivate()) || isInSameFile(descriptor, it))
            }
            else -> {
                val block = (descriptor as? DeclarationDescriptorWithSource)?.source?.getPsi()?.parent as? KtBlockExpression
                            ?: return emptyList()
                block.statements.mapNotNull {
                    if (it.name != newName) return@mapNotNull null
                    val isAccepted = when (descriptor) {
                        is ClassDescriptor -> it is KtClassOrObject
                        is VariableDescriptor -> it is KtProperty
                        is FunctionDescriptor -> it is KtNamedFunction
                        else -> false
                    }
                    if (!isAccepted) return@mapNotNull null
                    (it as? KtDeclaration)?.unsafeResolveToDescriptor()
                }
            }
        }
    }

    val overloadChecker = when (descriptor) {
        is PropertyDescriptor,
        is FunctionDescriptor,
        is ClassifierDescriptor -> {
            val psi = (descriptor as? DeclarationDescriptorWithSource)?.source?.getPsi() as? KtElement ?: return
            val typeSpecificityComparator = when (TargetPlatformDetector.getPlatform(psi.containingKtFile)) {
                is JvmPlatform -> JvmTypeSpecificityComparator
                is JsPlatform -> JsTypeSpecificityComparator
                else -> TypeSpecificityComparator.NONE
            }
            OverloadChecker(typeSpecificityComparator)
        }
        else -> null
    }
    for (candidateDescriptor in getSiblingsWithNewName()) {
        val candidate = (candidateDescriptor as? DeclarationDescriptorWithSource)?.source?.getPsi() as? KtNamedDeclaration ?: continue
        if (overloadChecker != null && overloadChecker.isOverloadable(descriptor, candidateDescriptor)) continue
        val what = candidate.renderDescription().capitalize()
        val where = candidate.representativeContainer()?.renderDescription() ?: continue
        val message = "$what is already declared in $where"
        result += BasicUnresolvableCollisionUsageInfo(candidate, candidate, message)
    }
}

private fun LexicalScope.getRelevantDescriptors(
        declaration: PsiNamedElement,
        name: String
): Collection<DeclarationDescriptor> {
    val nameAsName = Name.identifier(name)
    return when (declaration) {
        is KtProperty, is KtParameter, is PsiField -> getAllAccessibleVariables(nameAsName)
        is KtNamedFunction -> getAllAccessibleFunctions(nameAsName)
        is KtClassOrObject, is PsiClass -> listOfNotNull(findClassifier(nameAsName, NoLookupLocation.FROM_IDE))
        else -> emptyList()
    }
}

private fun checkUsagesRetargeting(
        elementToBindUsageInfosTo: PsiElement,
        declaration: PsiNamedElement,
        name: String,
        isNewName: Boolean,
        accessibleDescriptors: Collection<DeclarationDescriptor>,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
) {
    val usageIterator = originalUsages.listIterator()
    while (usageIterator.hasNext()) {
        val usageInfo = usageIterator.next()
        val newUsage = UsageRetargetingProcessor.checkRetargeting(
            declaration, name, isNewName, accessibleDescriptors, elementToBindUsageInfosTo, usageInfo
        )
        when (newUsage) {
            is KtResolvableCollisionUsageInfo -> usageIterator.set(newUsage) // replace usage
            is UnresolvableCollisionUsageInfo -> newUsages.add(newUsage) // add conflict
            null -> {// do nothing
            }
            else -> error("Unexpected usage type")
        }
    }
}

internal fun checkOriginalUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        originalUsages: MutableList<UsageInfo>,
        newUsages: MutableList<UsageInfo>
) {
    val accessibleDescriptors = declaration.getResolutionScope().getRelevantDescriptors(declaration, newName)
    checkUsagesRetargeting(declaration, declaration, newName, true, accessibleDescriptors, originalUsages, newUsages)
}

internal fun checkNewNameUsagesRetargeting(
        declaration: KtNamedDeclaration,
        newName: String,
        newUsages: MutableList<UsageInfo>
) {
    val currentName = declaration.name ?: return
    val descriptor = declaration.unsafeResolveToDescriptor()

    if (declaration is KtParameter && !declaration.hasValOrVar()) {
        val ownerFunction = declaration.ownerFunction
        val searchScope = (if (ownerFunction is KtPrimaryConstructor) ownerFunction.containingClassOrObject else ownerFunction) ?: return

        val usagesByCandidate = LinkedHashMap<PsiElement, MutableList<UsageInfo>>()

        searchScope.accept(
                object: KtTreeVisitorVoid() {
                    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) {
                        if (expression.getReferencedName() != newName) return
                        val ref = expression.mainReference
                        val candidate = ref.resolve() as? PsiNamedElement ?: return
                        usagesByCandidate.getOrPut(candidate) { SmartList() }.add(MoveRenameUsageInfo(ref, candidate))
                    }
                }
        )

        for ((candidate, usages) in usagesByCandidate) {
            checkUsagesRetargeting(candidate, declaration, currentName, false, listOf(descriptor), usages, newUsages)
            usages.filterIsInstanceTo<KtResolvableCollisionUsageInfo, MutableList<UsageInfo>>(newUsages)
        }

        return
    }

    for (candidateDescriptor in declaration.getResolutionScope().getRelevantDescriptors(declaration, newName)) {
        val candidate = DescriptorToSourceUtilsIde.getAnyDeclaration(declaration.project, candidateDescriptor) as? PsiNamedElement ?: continue
        val usages = ReferencesSearch
                .search(candidate, candidate.useScope.restrictToKotlinSources() and declaration.useScope)
                .mapTo(SmartList<UsageInfo>()) { MoveRenameUsageInfo(it, candidate) }
        checkUsagesRetargeting(candidate, declaration, currentName, false, listOf(descriptor), usages, newUsages)
        usages.filterIsInstanceTo<KtResolvableCollisionUsageInfo, MutableList<UsageInfo>>(newUsages)
    }
}

private class UsageRetargetingProcessor(
    private val declaration: PsiNamedElement,
    private val name: String,
    private val isNewName: Boolean,
    private val accessibleDescriptors: Collection<DeclarationDescriptor>,
    private val elementToBindUsageInfosTo: PsiElement,
    private val scope: LexicalScope,
    private val refElement: KtSimpleNameExpression,
    private val context: BindingContext
) {

    private val psiFactory = KtPsiFactory(declaration)

    private fun checkCallUsageRetargeting(resolvedCall: ResolvedCall<out CallableDescriptor>): UsageInfo? {
        val callExpression = resolvedCall.call.callElement as? KtExpression ?: return null
        val fullCallExpression = callExpression.getQualifiedExpressionForSelectorOrThis()

        val qualifiedExpression = expandedCallExpression(resolvedCall) ?: return null

        val newCallee = qualifiedExpression.getQualifiedElementSelector() as? KtSimpleNameExpression ?: return null
        if (isNewName) {
            newCallee.getReferencedNameElement().replace(psiFactory.createNameIdentifier(name))
        }

        qualifiedExpression.parentSubstitute = fullCallExpression.parent
        val newContext = qualifiedExpression.analyzeInContext(scope, refElement, DelegatingBindingTrace(context, ""))

        val newResolvedCall = newCallee.getResolvedCall(newContext)
        val candidateText = newResolvedCall?.candidateDescriptor?.getImportableDescriptor()?.canonicalRender()

        if (newResolvedCall != null
            && !accessibleDescriptors.any { it.canonicalRender() == candidateText }
            && resolvedCall.candidateDescriptor.canonicalRender() != candidateText
        ) {
            return reportShadowing(newResolvedCall.candidateDescriptor)
        }

        if (fullCallExpression !is KtQualifiedExpression) {
            return UsageInfoWithReplacement(fullCallExpression, declaration, qualifiedExpression)
        }
        return null
    }

    private fun expandedCallExpression(resolvedCall: ResolvedCall<out CallableDescriptor>): KtExpression? {
        val callExpression = resolvedCall.call.callElement as? KtExpression ?: return null
        val fullCallExpression = callExpression.getQualifiedExpressionForSelectorOrThis()
        if (resolvedCall.noReceivers()) {
            val resultingDescriptor = resolvedCall.resultingDescriptor
            val fqName = resultingDescriptor.importableFqName
                ?: (resultingDescriptor as? ClassifierDescriptor)?.let {
                    FqName(IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(it))
                }
                ?: return null
            return if (fqName.parent().isRoot) {
                callExpression.copied()
            } else {
                psiFactory.createExpressionByPattern("${fqName.parent().asString()}.$0", callExpression)
            }
        }
        val receiverValue = resolvedCall.getExplicitReceiverValue()
        if (receiverValue != null) {
            return fullCallExpression.copied()
        }

        val implicitReceiver = resolvedCall.getImplicitReceiverValue() ?: return null
        val expectedLabelName = implicitReceiver.declarationDescriptor.getThisLabelName()
        val implicitReceivers = scope.getImplicitReceiversHierarchy()
        val receiversWithExpectedName = implicitReceivers.filter {
            it.value.type.constructor.declarationDescriptor?.getThisLabelName() == expectedLabelName
        }

        val canQualifyThis = receiversWithExpectedName.isEmpty()
                || receiversWithExpectedName.size == 1 && (declaration !is KtClassOrObject || expectedLabelName != name)
        return if (canQualifyThis) {
            psiFactory.createExpressionByPattern("${implicitReceiver.explicateAsText()}.$0", callExpression)
        } else {
            val defaultReceiverClassText =
                implicitReceivers.firstOrNull()?.value?.type?.constructor?.declarationDescriptor?.canonicalRender()
            val canInsertUnqualifiedThis = accessibleDescriptors.any { it.canonicalRender() == defaultReceiverClassText }
            if (canInsertUnqualifiedThis) {
                psiFactory.createExpressionByPattern("this.$0", callExpression)
            } else {
                callExpression.copied()
            }
        }
    }

    private fun checkTypeNameUsageRetargeting(): UsageInfo? {
        val typeReference = refElement.getStrictParentOfType<KtTypeReference>() ?: return null
        val referencedClass = context[BindingContext.TYPE, typeReference]?.constructor?.declarationDescriptor ?: return null
        val referencedClassFqName = FqName(IdeDescriptorRenderers.SOURCE_CODE.renderClassifierName(referencedClass))
        val newFqName = if (isNewName) referencedClassFqName.parent().child(Name.identifier(name)) else referencedClassFqName
        val fakeVar = psiFactory.createDeclaration<KtProperty>("val __foo__: ${newFqName.asString()}")
        val newContext = fakeVar.analyzeInContext(scope, refElement)
        val referencedClassInNewContext = newContext[BindingContext.TYPE, fakeVar.typeReference!!]?.constructor?.declarationDescriptor
        val candidateText = referencedClassInNewContext?.canonicalRender()
        return if (referencedClassInNewContext == null
            || ErrorUtils.isError(referencedClassInNewContext)
            || referencedClass.canonicalRender() == candidateText
            || accessibleDescriptors.any { it.canonicalRender() == candidateText }
        ) {
            UsageInfoWithFqNameReplacement(refElement, declaration, newFqName)
        } else {
            reportShadowing(referencedClassInNewContext)
        }
    }

    private fun reportShadowing(candidateDescriptor: DeclarationDescriptor): UsageInfo? {
        val candidate = DescriptorToSourceUtilsIde.getAnyDeclaration(declaration.project, candidateDescriptor) as? PsiNamedElement
            ?: return null
        if (declaration.parent == candidate.parent)
            return null
        val message = "${declaration.renderDescription().capitalize()} will be shadowed by ${candidate.renderDescription()}"
        return BasicUnresolvableCollisionUsageInfo(refElement, elementToBindUsageInfosTo, message)
    }

    companion object {
        fun checkRetargeting(
            declaration: PsiNamedElement,
            name: String,
            isNewName: Boolean,
            accessibleDescriptors: Collection<DeclarationDescriptor>,
            elementToBindUsageInfosTo: PsiElement,
            usage: UsageInfo
        ): UsageInfo? {
            val refElement = usage.element as? KtSimpleNameExpression ?: return null
            val context = refElement.analyze(BodyResolveMode.PARTIAL)
            val scope = refElement
                .parentsWithSelf
                .filterIsInstance<KtElement>()
                .mapNotNull { context[BindingContext.LEXICAL_SCOPE, it] }
                .firstOrNull()
                ?: return null

            if (scope.getRelevantDescriptors(declaration, name).isEmpty()) {
                if (declaration !is KtProperty && declaration !is KtParameter) return null
                if (NewDeclarationNameValidator(refElement.parent, refElement, NewDeclarationNameValidator.Target.VARIABLES)(name)) return null
            }

            val resolvedCall = refElement.getResolvedCall(context)
            val processor =
                UsageRetargetingProcessor(
                    declaration, name, isNewName, accessibleDescriptors, elementToBindUsageInfosTo, scope, refElement, context
                )
            return if (resolvedCall != null) {
                processor.checkCallUsageRetargeting(resolvedCall)
            } else {
                processor.checkTypeNameUsageRetargeting()
            }

        }
    }
}