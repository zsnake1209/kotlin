/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.DebuggerBundle
import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.actions.GotoFrameSourceAction
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.ui.impl.tree.TreeBuilder
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode
import com.intellij.debugger.ui.impl.watch.*
import com.intellij.debugger.ui.tree.StackFrameDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.DoubleClickListener
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import javaslang.control.Either
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import java.util.*
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener

/**
 * Tree of coroutines for [CoroutinesPanel]
 */
class CoroutinesDebuggerTree(project: Project) : DebuggerTree(project) {
    private val logger = Logger.getInstance(this::class.java)
    private var lastSuspendContextCache: Cache? = null
    private val settings = CoroutinesViewPopupSettings.getInstance()
    private val cachedChildren = WeakHashMap<CoroutineState, List<DebuggerTreeNodeImpl>>()

    override fun createNodeManager(project: Project): NodeManagerImpl {
        return object : NodeManagerImpl(project, this) {
            override fun getContextKey(frame: StackFrameProxyImpl?): String? {
                return "CoroutinesView"
            }
        }
    }

    /**
     * Prepare specific behavior instead of DebuggerTree constructor
     */
    init {
        val model = object : TreeBuilder(this) {
            override fun buildChildren(node: TreeBuilderNode) {
                if ((node as? DebuggerTreeNodeImpl) != null) buildNode(node)
            }

            override fun isExpandable(builderNode: TreeBuilderNode): Boolean {
                return if (builderNode !is DebuggerTreeNodeImpl)
                    false
                else
                    this@CoroutinesDebuggerTree.isExpandable(builderNode)
            }
        }
        model.setRoot(nodeFactory.defaultNode)
        model.addTreeModelListener(createListener())
        setModel(model)
        emptyText.text = "Coroutines are not available"
    }

    /**
     * Add frames inside coroutine (node)
     */
    private fun buildNode(node: DebuggerTreeNodeImpl) {
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val debugProcess = context.debugProcess
        debugProcess?.managerThread?.schedule(object : SuspendContextCommandImpl(context.suspendContext) {
            override fun contextAction(suspendContext: SuspendContextImpl) {
                val descriptor = node.descriptor as? CoroutineDescriptorImpl ?: return
                val evalContext = debuggerContext.createEvaluationContext() ?: return
                val children = cachedChildren[descriptor.state] ?: try {
                    mutableListOf<DebuggerTreeNodeImpl>().apply {
                        descriptor.computeFrames(this, debugProcess, evalContext, myNodeManager)
                        cachedChildren[descriptor.state] = this
                    }
                } catch (e: EvaluateException) {
                    logger.warn(e)
                    listOf(myNodeManager.createMessageNode(e.message))
                }
                DebuggerInvocationUtil.swingInvokeLater(project) {
                    node.removeAllChildren()
                    showFilteredChildren(node, children)
                    node.childrenChanged(true)
                }
            }
        })
    }

    private fun showFilteredChildren(parent: DebuggerTreeNodeImpl, children: List<DebuggerTreeNodeImpl>) {
        for (node in children) {
            val descriptor = node.descriptor
            if (!settings.showCoroutineCreationStackTrace && descriptor is CreationStackFrameDescriptor) continue
            if (!settings.showIntrinsicFrames && descriptor is StackFrameDescriptorImpl
                && isIntrinsic(descriptor)
            ) continue
            parent.add(node)
        }
    }

    fun installDoubleClickListener(): () -> Unit {
        val listener = object : DoubleClickListener() {
            override fun onDoubleClick(e: MouseEvent): Boolean {
                val location = getPathForLocation(e.x, e.y)?.lastPathComponent as? DebuggerTreeNodeImpl
                return location?.let { selectFrame(location.userObject) } ?: false
            }
        }
        listener.installOn(this)
        return { listener.uninstall(this) }
    }

    fun selectFrame(descriptor: Any): Boolean {
        val dataContext = DataManager.getInstance().getDataContext(this@CoroutinesDebuggerTree)
        return when (descriptor) {
            is CoroutineFrameDescriptor -> {
                descriptor.buildChildren(project, this)
                true
            }
            is StackFrameDescriptor -> {
                GotoFrameSourceAction.doAction(dataContext)
                true
            }
            else -> true
        }
    }

    private fun createListener() = object : TreeModelListener {
        override fun treeNodesChanged(event: TreeModelEvent) {
            hideTooltip()
        }

        override fun treeNodesInserted(event: TreeModelEvent) {
            hideTooltip()
        }

        override fun treeNodesRemoved(event: TreeModelEvent) {
            hideTooltip()
        }

        override fun treeStructureChanged(event: TreeModelEvent) {
            hideTooltip()
        }
    }

    override fun isExpandable(node: DebuggerTreeNodeImpl): Boolean {
        val descriptor = node.descriptor
        return if (descriptor is StackFrameDescriptor) false else descriptor.isExpandable
    }

    override fun build(context: DebuggerContextImpl) {
        val session = context.debuggerSession
        val command = RefreshCoroutinesTreeCommand(context.suspendContext)

        val state = if (session != null) session.state else DebuggerSession.State.DISPOSED
        if (ApplicationManager.getApplication().isUnitTestMode
            || state == DebuggerSession.State.PAUSED
        ) {
            showMessage(MessageDescriptor.EVALUATING)
            context.debugProcess?.managerThread?.schedule(command)
        } else {
            showMessage(if (session != null) session.stateDescription else DebuggerBundle.message("status.debug.stopped"))
        }
    }

    private inner class RefreshCoroutinesTreeCommand(context: SuspendContextImpl?) :
        SuspendContextCommandImpl(context) {

        override fun contextAction() {
            val root = nodeFactory.defaultNode
            val suspendContext = suspendContext
            val frameProxy = suspendContext?.frameProxy
            if (frameProxy == null || suspendContext.isResumed) {
                setRoot(root.apply { add(myNodeManager.createMessageNode("Application is resumed")) })
                return
            }
            val evalContext = EvaluationContextImpl(suspendContext, frameProxy)
            val states = getStates(ExecutionContext(evalContext, frameProxy))
            if (states.isLeft) {
                notifyDumpFailed(states.left)
                return
            }
            for (state in states.get()) {
                root.add(with(nodeFactory) { createNode(getDescriptor(null, CoroutineData(state)), evalContext) })
            }
            setRoot(root)
        }

        private fun getStates(context: ExecutionContext): Either<Throwable, List<CoroutineState>> {
            // if suspend context hasn't changed - use last dump, else compute new
            val cache = lastSuspendContextCache
            return if (cache != null && cache.first === context.suspendContext) {
                Either.right(cache.second)
            } else {
                CoroutinesDebugProbesProxy.dumpCoroutines(context).apply {
                    lastSuspendContextCache = if (isLeft) null else WeakReference(context.suspendContext) to get()
                }
            }
        }

        private fun notifyDumpFailed(e: Throwable) {
            logger.warn(e)
            setRoot(DebuggerTreeNodeImpl(this@CoroutinesDebuggerTree, null).apply {
                add(nodeFactory.createMessageNode("Dump failed"))
            })
            XDebuggerManagerImpl.NOTIFICATION_GROUP.createNotification(
                "Coroutine dump failed. See log",
                MessageType.ERROR
            ).notify(project)
        }

        private fun setRoot(root: DebuggerTreeNodeImpl) {
            DebuggerInvocationUtil.swingInvokeLater(project) {
                mutableModel.setRoot(root)
                treeChanged()
            }
        }

    }

}

private typealias Cache = Pair<WeakReference<SuspendContextImpl>, List<CoroutineState>>
