/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.checkers.declaration.leak

import org.jetbrains.kotlin.fir.analysis.cfa.TraverseDirection
import org.jetbrains.kotlin.fir.analysis.cfa.traverse
import org.jetbrains.kotlin.fir.declarations.FirAnonymousInitializer
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.*
import org.jetbrains.kotlin.fir.resolve.dfa.controlFlowGraph
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.name.ClassId

internal class BaseClassMembersContext(private val classDeclaration: FirRegularClass) {
    val isClassNotRelevantForChecker: Boolean
        get() = propsDeclList.isEmpty()
    val isCfgAvailable: Boolean
        get() = classDeclaration.controlFlowGraphReference.controlFlowGraph != null
    val classId: ClassId
        get() = classDeclaration.symbol.classId

    val classInitContextNodes = mutableListOf<InitializeContextNode>()

    private val classCfg: ControlFlowGraph
        get() = classDeclaration.controlFlowGraphReference.controlFlowGraph!!

    private val propsDeclList = mutableListOf<FirProperty>()
    private val anonymousInitializer = mutableListOf<FirAnonymousInitializer>()

    init {
        if (isCfgAvailable)
            for (subGraph in classCfg.subGraphs) {
                when (val declaration = subGraph.declaration) {
                    is FirAnonymousInitializer -> anonymousInitializer.add(declaration)
                    is FirProperty -> propsDeclList.add(declaration)
                }
            }
        val visitor = BackwardCfgVisitor(classDeclaration.symbol.classId)
        classCfg.traverse(TraverseDirection.Backward, visitor)
        classInitContextNodes.addAll(visitor.initContextNodes)
    }
}

internal class BackwardCfgVisitor(
    private val classId: ClassId,
) : ControlFlowGraphVisitorVoid() {

    val initContextNodes = mutableListOf<InitializeContextNode>()


    private var lastAssignmentContextNode: InitializeContextNode? = null
    private var lastAssignRValue: FirExpression? = null
    private var isInRvalueOfAssignment: Boolean = false
    private var lastAssignmentAffectingNodes = mutableListOf<InitializeContextNode>()

    private var isInPropertyInitializer = false
    private var lastPropertyInitializerAffectingNodes = mutableListOf<InitializeContextNode>()
    private var lastPropertyInitializerContextNode: InitializeContextNode? = null

    override fun visitNode(node: CFGNode<*>) {
        initContextNodes.add(checkAndBuildNodeContext(cfgNode = node))
    }

    override fun visitFunctionCallNode(node: FunctionCallNode) {
        val accessedMembers = mutableListOf<FirCallableSymbol<*>>()
        val accessedProperties = mutableListOf<FirVariableSymbol<*>>()
        var nodeType = ContextNodeType.UNRESOLVABLE_FUN_CALL
        if (node.fir.calleeReference.isMemberOfTheClass(classId)) {
            accessedMembers.add(node.fir.calleeReference.resolvedSymbolAsNamedFunction!!)
            for (argument in node.fir.argumentList.arguments) {
                if ((argument is FirQualifiedAccessExpression)
                    && argument.calleeReference.isMemberOfTheClass(classId)
                ) {
//                    TODO: clean up with casting
                    accessedMembers.add(argument.calleeReference.resolvedSymbolAsCallable!!)
                    if (argument.calleeReference.resolvedSymbolAsProperty != null)
                        accessedProperties.add(argument.calleeReference.resolvedSymbolAsProperty!!)
                }
            }
            nodeType = ContextNodeType.RESOLVABLE_MEMBER_CALL
        }
        initContextNodes.add(
            checkAndBuildNodeContext(
                cfgNode = node,
                accessedMembers = accessedMembers,
                accessedProperties = accessedProperties,
                nodeType = nodeType
            )
        )
    }

    override fun visitQualifiedAccessNode(node: QualifiedAccessNode) {
        val accessedProperties = mutableListOf<FirVariableSymbol<*>>()
        var nodeType = ContextNodeType.NOT_MEMBER_QUALIFIED_ACCESS
        if (node.fir.calleeReference.isMemberOfTheClass(classId)) {
            // if it is a member and it is qualifiedAccess then it is property :)
            val member = node.fir.calleeReference.resolvedSymbolAsProperty!!
            nodeType = ContextNodeType.PROPERTY_QUALIFIED_ACCESS
            accessedProperties.add(member)
        }
        initContextNodes.add(
            checkAndBuildNodeContext(
                cfgNode = node,
                accessedMembers = accessedProperties,
                accessedProperties = accessedProperties,
                nodeType = nodeType
            )
        )
    }

    override fun visitVariableAssignmentNode(node: VariableAssignmentNode) {
        if (node.fir.lValue.resolvedSymbolAsProperty?.callableId?.classId == classId) {
            val symbol = node.fir.lValue.resolvedSymbolAsProperty
            val accessedProperties = mutableListOf<FirVariableSymbol<*>>()
            accessedProperties.add(symbol!!)
            lastAssignmentContextNode =
                checkAndBuildNodeContext(
                    cfgNode = node,
                    accessedMembers = accessedProperties,
                    accessedProperties = accessedProperties,
                    initCandidates = accessedProperties,
                    nodeType = ContextNodeType.ASSINGMENT_OR_INITIALIZER
                )
            isInRvalueOfAssignment = true
            lastAssignRValue = node.fir.rValue
            initContextNodes.add(lastAssignmentContextNode!!)
        } else {
//                TODO:  properties assignment local vals?
            visitNode(node)
        }
    }

    override fun visitPropertyInitializerEnterNode(node: PropertyInitializerEnterNode) {
        if (node.fir.isClassPropertyWithInitializer) {
            isInPropertyInitializer = false
            val accessedProperties = mutableListOf<FirVariableSymbol<*>>(node.fir.symbol)
            initContextNodes.add(
                checkAndBuildNodeContext(
                    cfgNode = node,
                    accessedMembers = accessedProperties,
                    accessedProperties = accessedProperties,
                    initCandidates = accessedProperties,
                    affectingNodes = lastPropertyInitializerAffectingNodes,
                    nodeType = ContextNodeType.ASSINGMENT_OR_INITIALIZER
                )
            )
            lastPropertyInitializerAffectingNodes = mutableListOf()

        } else {
            isInPropertyInitializer = false
            visitNode(node)
        }
    }

    override fun visitPropertyInitializerExitNode(node: PropertyInitializerExitNode) {
        if (node.fir.isClassPropertyWithInitializer) {
            isInPropertyInitializer = true
            val accessedProperties = mutableListOf<FirVariableSymbol<*>>(node.fir.symbol)
            // TODO affected by itself also :)))
            lastPropertyInitializerContextNode = checkAndBuildNodeContext(
                cfgNode = node,
                accessedMembers = accessedProperties,
                accessedProperties = accessedProperties,
                initCandidates = accessedProperties,
                nodeType = ContextNodeType.ASSINGMENT_OR_INITIALIZER
            )
            initContextNodes.add(lastPropertyInitializerContextNode!!)
        } else {
            visitNode(node)
        }
    }
//        TODO: lambda

    private fun checkAndBuildNodeContext(
        cfgNode: CFGNode<*>,
        accessedMembers: List<FirCallableSymbol<*>> = emptyList(),
        accessedProperties: List<FirVariableSymbol<*>> = emptyList(),
        initCandidates: MutableList<FirVariableSymbol<*>> = mutableListOf(),
        affectedNodes: MutableList<InitializeContextNode> = mutableListOf(),
        affectingNodes: MutableList<InitializeContextNode> = mutableListOf(),
        nodeType: ContextNodeType = ContextNodeType.NOT_AFFECTED,
        isInitialized: Boolean = false
    ): InitializeContextNode {

        val context = InitializeContextNode(
            cfgNode,
            accessedMembers,
            accessedProperties,
            initCandidates,
            affectedNodes,
            affectingNodes,
            nodeType,
            isInitialized
        )
// TODO: if variable
        if (checkIfInRvalueOfAssignment(context))
            context.affectedNodes.add(lastAssignmentContextNode!!)

        if (checkIfInPropertyInitializer(context)) {
            context.affectedNodes.add(lastPropertyInitializerContextNode!!)
        }

        return context
    }

    private fun checkIfInRvalueOfAssignment(contextNode: InitializeContextNode): Boolean {
        if (isInRvalueOfAssignment) {
            lastAssignmentAffectingNodes.add(contextNode)
            if (contextNode.cfgNode.fir == lastAssignRValue) {
                isInRvalueOfAssignment = false
                lastAssignmentContextNode?.affectingNodes = lastAssignmentAffectingNodes
                lastAssignmentAffectingNodes = mutableListOf()
            }
            return true
        }
        return false
    }

    private fun checkIfInPropertyInitializer(contextNode: InitializeContextNode) =
        if (contextNode.cfgNode !is PropertyInitializerExitNode && isInPropertyInitializer) {
            lastPropertyInitializerAffectingNodes.add(contextNode)
            true
        } else false
}
