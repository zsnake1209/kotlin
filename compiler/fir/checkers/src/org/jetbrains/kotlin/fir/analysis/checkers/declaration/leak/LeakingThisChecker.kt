/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration.leak

import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.fir.FirSourceElement
import org.jetbrains.kotlin.fir.analysis.cfa.TraverseDirection
import org.jetbrains.kotlin.fir.analysis.cfa.traverse
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.modality
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ConstExpressionNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.FunctionCallNode
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.ClassId

object LeakingThisChecker : FirDeclarationChecker<FirRegularClass>() {

    override fun check(declaration: FirRegularClass, context: CheckerContext, reporter: DiagnosticReporter) {
        when (declaration.modality) {
            Modality.FINAL -> {
                if (!declaration.hasClassSomeParents())
                    try {
                        runCheck(
                            collectDataForSimpleClassAnalysis(declaration),
                            reporter
                        )
                    } catch (e: Exception) {

                    }
            }
            else -> {

            }
        }
    }

    private fun collectDataForSimpleClassAnalysis(classDeclaration: FirRegularClass): BaseClassMembersContext =
        BaseClassMembersContext(
            classDeclaration
        )

    private fun runCheck(classMembersContext: BaseClassMembersContext, reporter: DiagnosticReporter) {
        if (classMembersContext.isClassNotRelevantForChecker)
            return
        val initializedProperties = mutableSetOf<FirVariableSymbol<*>>()
        val reportedProperties = mutableSetOf<FirVariableSymbol<*>>()
        checkContextNodes(
            classMembersContext.classInitContextNodes.asReversed(),
            initializedProperties,
            reportedProperties,
            0,
            2,
            reporter,
            classMembersContext.classId
        )
    }

    private fun checkContextNodes(
        contextNodes: MutableList<InitializeContextNode>,
        initializedProperties: MutableSet<FirVariableSymbol<*>>,
        reportedProperties: MutableSet<FirVariableSymbol<*>>,
        memberCallLevel: Int,
        maxMemberCallLevel: Int,
        reporter: DiagnosticReporter, classId: ClassId
    ) {
        for (initContextNode in contextNodes) {
            when (initContextNode.nodeType) {
                ContextNodeType.ASSINGMENT_OR_INITIALIZER -> {
                    if (initContextNode.affectingNodes.all {
                            it.cfgNode is ConstExpressionNode
                                    || (it.nodeType == ContextNodeType.UNRESOLVABLE_FUN_CALL)
                                    || (it.nodeType == ContextNodeType.PROPERTY_QUALIFIED_ACCESS
                                    && it.firstAccessedProperty.callableId.classId == classId
                                    && initializedProperties.contains(it.firstAccessedProperty))
                        }) {
                        initContextNode.confirmInitForCandidate()
                        initializedProperties.add(initContextNode.initCandidate)
                    }
                }
                ContextNodeType.PROPERTY_QUALIFIED_ACCESS -> {
                    if (initContextNode.accessedProperties.any {
                            it !in initializedProperties && it !in reportedProperties
                        }
                    ) {
                        reporter.report(initContextNode.cfgNode.fir.source)
                        reportedProperties.add(initContextNode.firstAccessedProperty)
                    }
                }
                ContextNodeType.RESOLVABLE_MEMBER_CALL -> {
                    if (memberCallLevel < maxMemberCallLevel) {
                        val memberBodyVisitor = BackwardCfgVisitor(classId)
                        val memberCfg =
                            (initContextNode.cfgNode as FunctionCallNode).fir.calleeReference.resolvedSymbolAsNamedFunction?.fir?.controlFlowGraphReference?.controlFlowGraph
                        try {
                            memberCfg?.traverse(TraverseDirection.Backward, memberBodyVisitor)
                            memberCfg?.enterNode
                            checkContextNodes(
                                memberBodyVisitor.initContextNodes,
                                initializedProperties,
                                reportedProperties,
                                memberCallLevel + 1,
                                maxMemberCallLevel,
                                reporter,
                                classId
                            )
                            memberCfg?.enterNode
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }
                else -> {

                }
            }
        }
    }

    private fun DiagnosticReporter.report(source: FirSourceElement?) {
        source?.let { report(FirErrors.LEAKING_THIS_IN_CONSTRUCTOR.on(it, "Possible leaking this in constructor")) }
    }

}

