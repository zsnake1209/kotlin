/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.engine.JavaStackFrame
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.impl.descriptors.data.DescriptorData
import com.intellij.debugger.impl.descriptors.data.DisplayKey
import com.intellij.debugger.impl.descriptors.data.SimpleDisplayKey
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl
import com.intellij.debugger.memory.utils.StackFrameItem
import com.intellij.debugger.ui.impl.watch.*
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import javaslang.control.Either
import org.jetbrains.kotlin.idea.debugger.KotlinCoroutinesAsyncStackTraceProvider
import org.jetbrains.kotlin.idea.debugger.evaluate.ExecutionContext
import javax.swing.JTree

/**
 * Describes coroutine itself in the tree (name: STATE), has children if stacktrace is not empty (state = CREATED)
 */
class CoroutineData(private val state: CoroutineState) : DescriptorData<CoroutineDescriptorImpl>() {

    override fun createDescriptorImpl(project: Project): CoroutineDescriptorImpl {
        return CoroutineDescriptorImpl(state)
    }

    override fun equals(other: Any?) = if (other !is CoroutineData) false else state.name == other.state.name

    override fun hashCode() = state.name.hashCode()

    override fun getDisplayKey(): DisplayKey<CoroutineDescriptorImpl> = SimpleDisplayKey(state.name)
}

class CoroutineDescriptorImpl(val state: CoroutineState) : NodeDescriptorImpl() {

    override fun getName(): String? {
        return state.name
    }

    @Throws(EvaluateException::class)
    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener): String {
        val name = if (state.thread != null) state.thread.name().substringBefore(" @${state.name}") else ""
        val threadState = if (state.thread != null) DebuggerUtilsEx.getThreadStatusText(state.thread.status()) else ""
        return "${state.name}: ${state.state}${if (name.isNotEmpty()) " on thread \"$name\":$threadState" else ""}"
    }

    override fun isExpandable(): Boolean {
        return state.state != CoroutineState.State.CREATED
    }

    override fun setContext(context: EvaluationContextImpl?) {
    }

    fun computeFrames(
        children: MutableList<DebuggerTreeNodeImpl>,
        debugProcess: DebugProcessImpl,
        evalContext: EvaluationContextImpl,
        nodeManager: NodeManagerImpl
    ) {
        val creationStackTraceSeparator = "\b\b\b" // the "\b\b\b" is used for creation stacktrace separator in kotlinx.coroutines
        val threadProxy = evalContext.suspendContext.thread ?: return
        when (state.state) {
            CoroutineState.State.RUNNING -> {
                if (state.thread == null) {
                    children.add(nodeManager.createMessageNode("Frames are not available"))
                    return
                }
                val proxy = ThreadReferenceProxyImpl(
                    debugProcess.virtualMachineProxy,
                    state.thread
                )
                val frames = proxy.forceFrames()
                val i = frames.indexOfFirst { it.location().method().name() == "invokeSuspend" }

                for (frame in 0 until i) {
                    children.add(
                        nodeManager.createNode(nodeManager.getStackFrameDescriptor(this, frames[frame]), evalContext)
                    )
                }
                if (i > 0) { // add async stack trace if there are frames after invokeSuspend
                    val async = KotlinCoroutinesAsyncStackTraceProvider().getAsyncStackTrace(
                        JavaStackFrame(StackFrameDescriptorImpl(frames[i - 1], MethodsTracker()), true),
                        evalContext.suspendContext
                    )
                    if (async != null) {
                        for (j in 1 until async.size) {
                            children.add(
                                nodeManager.createNode(
                                    nodeManager.getDescriptor(
                                        this, CoroutineStackFrameData(state, async[j], frames[0])
                                    ), evalContext
                                )
                            )
                        }
                    }
                }
                for (frame in i + 1..frames.lastIndex) {
                    children.add(
                        nodeManager.createNode(nodeManager.getStackFrameDescriptor(this, frames[frame]), evalContext)
                    )
                }
            }
            CoroutineState.State.SUSPENDED -> {
                val proxy = threadProxy.frame(0)
                // the thread is paused on breakpoint - it has at least one frame
                for (it in state.stackTrace) {
                    if (it.className.startsWith(creationStackTraceSeparator)) break
                    children.add(
                        nodeManager.createNode(
                            nodeManager.getDescriptor(null, CoroutineStackFrameData(state, it, proxy)), evalContext
                        )
                    )
                }
            }
            else -> {
            }
        }
        val trace = state.stackTrace
        val index = trace.indexOfFirst { it.className.startsWith(creationStackTraceSeparator) }
        val proxy = threadProxy.frame(0)
        for (i in index + 1 until trace.size) {
            children.add(nodeManager.createNode(CreationStackFrameDescriptor(trace[i], proxy), evalContext))
        }
    }
}

class CoroutineStackFrameData private constructor(val state: CoroutineState, private val proxy: StackFrameProxyImpl) :
    DescriptorData<NodeDescriptorImpl>() {
    private lateinit var frame: Either<StackTraceElement, StackFrameItem>

    constructor(state: CoroutineState, frame: StackTraceElement, proxy: StackFrameProxyImpl) : this(state, proxy) {
        this.frame = Either.left(frame)
    }

    constructor(state: CoroutineState, frameItem: StackFrameItem, proxy: StackFrameProxyImpl) : this(state, proxy) {
        this.frame = Either.right(frameItem)
    }

    override fun hashCode(): Int {
        return if (frame.isLeft) frame.left.hashCode() else frame.get().hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is CoroutineStackFrameData && frame == other.frame
    }

    /**
     * Returns [EmptyStackFrameDescriptor], [SuspendStackFrameDescriptor]
     * or [AsyncStackFrameDescriptor] according to current frame
     */
    override fun createDescriptorImpl(project: Project): NodeDescriptorImpl {
        val isLeft = frame.isLeft
        if (!isLeft) return AsyncStackFrameDescriptor(state, frame.get(), proxy)
        // check whether last fun is suspend fun
        val frame = frame.left
        val suspendContext =
            DebuggerManagerEx.getInstanceEx(project).context.suspendContext ?: return EmptyStackFrameDescriptor(
                frame,
                proxy
            )
        val suspendProxy = suspendContext.frameProxy ?: return EmptyStackFrameDescriptor(
            frame,
            proxy
        )
        val evalContext = EvaluationContextImpl(suspendContext, suspendContext.frameProxy)
        val context = ExecutionContext(evalContext, suspendProxy)
        val clazz = context.findClass(frame.className) as ClassType
        val method = clazz.methodsByName(frame.methodName).last {
            val loc = it.location().lineNumber()
            loc < 0 && frame.lineNumber < 0 || loc > 0 && loc <= frame.lineNumber
        } // pick correct method if an overloaded one is given
        return if ("Lkotlin/coroutines/Continuation;)" in method.signature() ||
            method.name() == "invokeSuspend" &&
            method.signature() == "(Ljava/lang/Object;)Ljava/lang/Object;" // suspend fun or invokeSuspend
        ) {
            val continuation = state.getContinuation(frame, context)
            if (continuation == null) EmptyStackFrameDescriptor(
                frame,
                proxy
            ) else
                SuspendStackFrameDescriptor(
                    state,
                    frame,
                    proxy,
                    continuation
                )
        } else EmptyStackFrameDescriptor(frame, proxy)
    }

    override fun getDisplayKey(): DisplayKey<NodeDescriptorImpl> = SimpleDisplayKey(state)
}

interface CoroutineFrameDescriptor {
    fun buildChildren(project: Project, tree: JTree)

    fun calcRepresentation(className: String, methodName: String, lineNumber: Int): String {
        val `package` = className.substringBeforeLast(".", "")
        return "$methodName:$lineNumber, ${className.substringAfterLast(".")} ${if (`package`.isNotEmpty()) "{$`package`}" else ""}"
    }

    fun getPosition(frame: StackTraceElement, project: Project): XSourcePosition? {
        val psiFacade = JavaPsiFacade.getInstance(project)
        val psiClass = psiFacade.findClass(
            frame.className.substringBefore("$"), // find outer class, for which psi exists
            GlobalSearchScope.everythingScope(project)
        )
        val classFile = psiClass?.containingFile?.virtualFile
        return XDebuggerUtil.getInstance().createPosition(classFile, frame.lineNumber)
    }

    fun isIntrinsic(): Boolean
}

/**
 * Descriptor for suspend functions
 */
class SuspendStackFrameDescriptor(
    val state: CoroutineState,
    val frame: StackTraceElement,
    proxy: StackFrameProxyImpl,
    val continuation: ObjectReference
) :
    StackFrameDescriptorImpl(proxy, MethodsTracker()), CoroutineFrameDescriptor {

    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
        return with(frame) {
            val pack = className.substringBeforeLast(".", "")
            "$methodName:$lineNumber, ${className.substringAfterLast(".")} " +
                    if (pack.isNotEmpty()) "{$pack}" else ""
        }
    }

    override fun isExpandable() = false

    override fun isIntrinsic(): Boolean = frame.className.contains(Regex("\\{$KOTLINX_COROUTINES|\\{$KOTLIN_COROUTINES"))

    override fun getName(): String {
        return frame.methodName
    }

    override fun buildChildren(project: Project, tree: JTree) {
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val pos = getPosition(frame, project) ?: return
        context.debugProcess?.managerThread?.schedule(object : SuspendContextCommandImpl(context.suspendContext) {
            override fun contextAction() {
                val (stack, stackFrame) = createSyntheticStackFrame(this@SuspendStackFrameDescriptor, pos, project) ?: return
                val action: () -> Unit = { context.debuggerSession?.xDebugSession?.setCurrentStackFrame(stack, stackFrame) }
                ApplicationManager.getApplication()
                    .invokeLater(action, ModalityState.stateForComponent(tree))
            }
        })
    }

    private fun createSyntheticStackFrame(
        descriptor: SuspendStackFrameDescriptor,
        pos: XSourcePosition,
        project: Project
    ): Pair<XExecutionStack, SyntheticStackFrame>? {
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val suspendContext = context.suspendContext ?: return null
        val proxy = suspendContext.thread ?: return null
        val executionStack = JavaExecutionStack(proxy, suspendContext.debugProcess, false)
        executionStack.initTopFrame()
        val evalContext = context.createEvaluationContext()
        val frameProxy = evalContext?.frameProxy ?: return null
        val execContext = ExecutionContext(evalContext, frameProxy)
        val continuation = descriptor.continuation // guaranteed that it is a BaseContinuationImpl
        val aMethod = (continuation.type() as ClassType).concreteMethodByName(
            "getStackTraceElement",
            "()Ljava/lang/StackTraceElement;"
        )
        val debugMetadataKtType = execContext
            .findClass("kotlin.coroutines.jvm.internal.DebugMetadataKt") as ClassType
        val vars = with(KotlinCoroutinesAsyncStackTraceProvider()) {
            KotlinCoroutinesAsyncStackTraceProvider.AsyncStackTraceContext(
                execContext,
                aMethod,
                debugMetadataKtType
            ).getSpilledVariables(continuation)
        } ?: return null
        return executionStack to SyntheticStackFrame(descriptor, vars, pos)
    }
}

class AsyncStackFrameDescriptor(val state: CoroutineState, val frame: StackFrameItem, proxy: StackFrameProxyImpl) :
    StackFrameDescriptorImpl(proxy, MethodsTracker()), CoroutineFrameDescriptor {
    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
        return with(frame) {
            val pack = path().substringBeforeLast(".", "")
            "${method()}:${line()}, ${path().substringAfterLast(".")} ${if (pack.isNotEmpty()) "{$pack}" else ""}"
        }
    }

    override fun isIntrinsic(): Boolean = frame.path().contains(Regex("\\{$KOTLINX_COROUTINES|\\{$KOTLIN_COROUTINES"))

    override fun getName(): String {
        return frame.method()
    }

    override fun isExpandable(): Boolean = false

    override fun buildChildren(project: Project, tree: JTree) {
        val process = debugProcess as? DebugProcessImpl
        process?.managerThread?.schedule(object : DebuggerCommandImpl() {
            override fun action() {
                val context = DebuggerManagerEx.getInstanceEx(project).context
                val proxy = ThreadReferenceProxyImpl(
                    process.virtualMachineProxy,
                    state.thread // is not null because it's a running coroutine
                )
                val executionStack = JavaExecutionStack(proxy, process, false)
                executionStack.initTopFrame()
                val frame = frame.createFrame(process)
                DebuggerUIUtil.invokeLater {
                    context.debuggerSession?.xDebugSession?.setCurrentStackFrame(
                        executionStack,
                        frame
                    )
                }
            }
        })
    }
}

/**
 * For the case when no data inside frame is available
 */
open class EmptyStackFrameDescriptor(
    val frame: StackTraceElement, proxy: StackFrameProxyImpl,
    tracker: MethodsTracker = MethodsTracker()
) : StackFrameDescriptorImpl(proxy, tracker), CoroutineFrameDescriptor {

    override fun calcRepresentation(context: EvaluationContextImpl?, labelListener: DescriptorLabelListener?): String {
        return calcRepresentation(frame.className, frame.methodName, frame.lineNumber)
    }

    override fun isIntrinsic(): Boolean = frame.className.contains(Regex("\\{$KOTLINX_COROUTINES|\\{$KOTLIN_COROUTINES"))

    override fun buildChildren(project: Project, tree: JTree) {
        val position = getPosition(frame, project) ?: return
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val suspendContext = context.suspendContext ?: return
        val proxy = suspendContext.thread ?: return
        context.debugProcess?.managerThread?.schedule(object : DebuggerCommandImpl() {
            override fun action() {
                val executionStack =
                    JavaExecutionStack(proxy, context.debugProcess!!, false)
                executionStack.initTopFrame()
                val frame = SyntheticStackFrame(this@EmptyStackFrameDescriptor, emptyList(), position)
                val action: () -> Unit =
                    { context.debuggerSession?.xDebugSession?.setCurrentStackFrame(executionStack, frame) }
                ApplicationManager.getApplication()
                    .invokeLater(action, ModalityState.stateForComponent(tree))
            }
        })
    }

    override fun getName(): String = frame.methodName
    override fun isExpandable() = false
}

class CreationStackFrameDescriptor(frame: StackTraceElement, proxy: StackFrameProxyImpl) : EmptyStackFrameDescriptor(frame, proxy) {
    override fun isIntrinsic(): Boolean = false
}

fun isIntrinsic(descriptor: StackFrameDescriptorImpl): Boolean {
    val coroutinesRegex = Regex("\\{$KOTLINX_COROUTINES|\\{$KOTLIN_COROUTINES")
    return when (descriptor) {
        is CoroutineFrameDescriptor -> {
            descriptor.isIntrinsic()
        }
        else -> descriptor.label.contains(coroutinesRegex)
    }
}
