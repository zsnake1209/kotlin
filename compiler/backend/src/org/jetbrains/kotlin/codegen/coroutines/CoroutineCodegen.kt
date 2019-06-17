/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.coroutines

import com.intellij.util.ArrayUtil
import org.jetbrains.kotlin.builtins.isSuspendFunctionTypeOrSubtype
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.binding.CalculatedClosure
import org.jetbrains.kotlin.codegen.binding.CodegenBinding
import org.jetbrains.kotlin.codegen.binding.CodegenBinding.CAPTURES_CROSSINLINE_LAMBDA
import org.jetbrains.kotlin.codegen.binding.CodegenBinding.CLOSURE
import org.jetbrains.kotlin.codegen.binding.MutableClosure
import org.jetbrains.kotlin.codegen.context.ClosureContext
import org.jetbrains.kotlin.codegen.context.MethodContext
import org.jetbrains.kotlin.codegen.inline.coroutines.SurroundSuspendLambdaCallsWithSuspendMarkersMethodVisitor
import org.jetbrains.kotlin.codegen.optimization.common.asSequence
import org.jetbrains.kotlin.codegen.optimization.fixStack.peek
import org.jetbrains.kotlin.codegen.optimization.fixStack.top
import org.jetbrains.kotlin.codegen.optimization.transformer.MethodTransformer
import org.jetbrains.kotlin.codegen.serialization.JvmSerializationBindings.METHOD_FOR_FUNCTION
import org.jetbrains.kotlin.codegen.serialization.JvmSerializerExtension
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.config.isReleaseCoroutines
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.SimpleFunctionDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.builtIns
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.kotlin.resolve.jvm.diagnostics.OtherOrigin
import org.jetbrains.kotlin.resolve.jvm.jvmSignature.JvmMethodSignature
import org.jetbrains.kotlin.serialization.DescriptorSerializer
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.makeNullable
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.kotlin.utils.sure
import org.jetbrains.org.objectweb.asm.Label
import org.jetbrains.org.objectweb.asm.MethodVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import org.jetbrains.org.objectweb.asm.commons.Method
import org.jetbrains.org.objectweb.asm.tree.*
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame
import org.jetbrains.org.objectweb.asm.tree.analysis.SourceInterpreter
import org.jetbrains.org.objectweb.asm.tree.analysis.SourceValue

abstract class AbstractCoroutineCodegen(
    outerExpressionCodegen: ExpressionCodegen,
    element: KtElement,
    closureContext: ClosureContext,
    classBuilder: ClassBuilder,
    private val userDataForDoResume: Map<out CallableDescriptor.UserDataKey<*>, *>? = null
) : ClosureCodegen(
    outerExpressionCodegen.state,
    element, null, closureContext, null,
    FailingFunctionGenerationStrategy,
    outerExpressionCodegen.parentCodegen, classBuilder
) {
    protected val classDescriptor = closureContext.contextDescriptor
    protected val languageVersionSettings = outerExpressionCodegen.state.languageVersionSettings

    protected val methodToImplement =
        if (languageVersionSettings.isReleaseCoroutines())
            createImplMethod(
                INVOKE_SUSPEND_METHOD_NAME,
                SUSPEND_CALL_RESULT_NAME to classDescriptor.module.getResult(classDescriptor.builtIns.anyType)
            )
        else
            createImplMethod(
                DO_RESUME_METHOD_NAME,
                "data" to classDescriptor.builtIns.nullableAnyType,
                "throwable" to classDescriptor.builtIns.throwable.defaultType.makeNullable()
            )

    private fun createImplMethod(name: String, vararg parameters: Pair<String, KotlinType>) =
        SimpleFunctionDescriptorImpl.create(
            classDescriptor, Annotations.EMPTY, Name.identifier(name), CallableMemberDescriptor.Kind.DECLARATION,
            funDescriptor.source
        ).apply {
            initialize(
                null,
                classDescriptor.thisAsReceiverParameter,
                emptyList(),
                parameters.withIndex().map { (index, nameAndType) ->
                    createValueParameterForDoResume(Name.identifier(nameAndType.first), nameAndType.second, index)
                },
                builtIns.nullableAnyType,
                Modality.FINAL,
                Visibilities.PUBLIC,
                userDataForDoResume
            )
        }

    private fun FunctionDescriptor.createValueParameterForDoResume(name: Name, type: KotlinType, index: Int) =
        ValueParameterDescriptorImpl(
            this, null, index, Annotations.EMPTY, name,
            type,
            false, false,
            false,
            null, SourceElement.NO_SOURCE
        )

    override fun generateConstructor(): Method {
        val args = calculateConstructorParameters(typeMapper, languageVersionSettings, closure, asmType)
        val argTypes = args.map { it.fieldType }.plus(languageVersionSettings.continuationAsmType()).toTypedArray()

        val constructor = Method("<init>", Type.VOID_TYPE, argTypes)
        val mv = v.newMethod(
            OtherOrigin(element, funDescriptor), visibilityFlag, "<init>", constructor.descriptor, null,
            ArrayUtil.EMPTY_STRING_ARRAY
        )

        if (state.classBuilderMode.generateBodies) {
            mv.visitCode()
            val iv = InstructionAdapter(mv)

            iv.generateClosureFieldsInitializationFromParameters(closure, args)

            iv.load(0, AsmTypes.OBJECT_TYPE)
            val hasArityParameter = !languageVersionSettings.isReleaseCoroutines() || passArityToSuperClass
            if (hasArityParameter) {
                iv.iconst(if (passArityToSuperClass) calculateArity() else 0)
            }

            iv.load(argTypes.map { it.size }.sum(), AsmTypes.OBJECT_TYPE)

            val parameters =
                if (hasArityParameter)
                    listOf(Type.INT_TYPE, languageVersionSettings.continuationAsmType())
                else
                    listOf(languageVersionSettings.continuationAsmType())

            val superClassConstructorDescriptor = Type.getMethodDescriptor(
                Type.VOID_TYPE,
                *parameters.toTypedArray()
            )
            iv.invokespecial(superClassAsmType.internalName, "<init>", superClassConstructorDescriptor, false)

            iv.visitInsn(Opcodes.RETURN)

            FunctionCodegen.endVisit(iv, "constructor", element)
        }

        if (languageVersionSettings.isReleaseCoroutines()) {
            v.newField(JvmDeclarationOrigin.NO_ORIGIN, AsmUtil.NO_FLAG_PACKAGE_PRIVATE, "label", "I", null, null)
        }

        return constructor
    }

    abstract protected val passArityToSuperClass: Boolean
}

class CoroutineCodegenForLambda private constructor(
    outerExpressionCodegen: ExpressionCodegen,
    element: KtElement,
    private val closureContext: ClosureContext,
    private val classBuilder: SuspendLambdaClassBuilder,
    private val originalSuspendFunctionDescriptor: FunctionDescriptor,
    private val forInline: Boolean
) : AbstractCoroutineCodegen(
    outerExpressionCodegen, element, closureContext, classBuilder,
    userDataForDoResume = mapOf(INITIAL_SUSPEND_DESCRIPTOR_FOR_DO_RESUME to originalSuspendFunctionDescriptor)
) {
    private val builtIns = funDescriptor.builtIns

    private val constructorCallNormalizationMode = outerExpressionCodegen.state.constructorCallNormalizationMode

    private val erasedInvokeFunction by lazy {
        getErasedInvokeFunction(funDescriptor).createCustomCopy { setModality(Modality.FINAL) }
    }

    private lateinit var constructorToUseFromInvoke: Method

    private val createCoroutineDescriptor by lazy {
        if (generateErasedCreate) getErasedCreateFunction() else getCreateFunction()
    }

    private val endLabel = Label()

    private fun getCreateFunction(): SimpleFunctionDescriptor = SimpleFunctionDescriptorImpl.create(
        funDescriptor.containingDeclaration,
        Annotations.EMPTY,
        Name.identifier(SUSPEND_FUNCTION_CREATE_METHOD_NAME),
        funDescriptor.kind,
        funDescriptor.source
    ).also {
        it.initialize(
            funDescriptor.extensionReceiverParameter?.copy(it),
            funDescriptor.dispatchReceiverParameter,
            funDescriptor.typeParameters,
            funDescriptor.valueParameters,
            funDescriptor.module.getContinuationOfTypeOrAny(
                builtIns.unitType,
                state.languageVersionSettings.supportsFeature(LanguageFeature.ReleaseCoroutines)
            ),
            funDescriptor.modality,
            Visibilities.PUBLIC
        )
    }

    private fun getErasedCreateFunction(): SimpleFunctionDescriptor {
        val typedCreate = getCreateFunction()
        assert(generateErasedCreate) { "cannot create erased create function: $typedCreate" }
        val argumentsNum = typeMapper.mapSignatureSkipGeneric(typedCreate).asmMethod.argumentTypes.size
        assert(argumentsNum == 1 || argumentsNum == 2) {
            "too many arguments of create to have an erased signature: $argumentsNum: $typedCreate"
        }
        return typedCreate.module.resolveClassByFqName(
            languageVersionSettings.coroutinesJvmInternalPackageFqName().child(
                if (languageVersionSettings.isReleaseCoroutines())
                    Name.identifier("BaseContinuationImpl")
                else
                    Name.identifier("CoroutineImpl")
            ),
            NoLookupLocation.FROM_BACKEND
        ).sure { "BaseContinuationImpl or CoroutineImpl is not found" }.defaultType.memberScope
            .getContributedFunctions(typedCreate.name, NoLookupLocation.FROM_BACKEND)
            .find { it.valueParameters.size == argumentsNum }
            .sure { "erased parent of $typedCreate is not found" }
            .createCustomCopy { setModality(Modality.FINAL) }
    }

    override fun generateClosureBody() {
        for (parameter in allFunctionParameters()) {
            val fieldInfo = parameter.getFieldInfoForCoroutineLambdaParameter()
            v.newField(
                OtherOrigin(parameter),
                Opcodes.ACC_PRIVATE,
                fieldInfo.fieldName,
                fieldInfo.fieldType.descriptor, null, null
            )
        }

        generateResumeImpl()
    }

    private val generateErasedCreate: Boolean = allFunctionParameters().size <= 1

    private val doNotGenerateInvokeBridge: Boolean = !originalSuspendFunctionDescriptor.isLocalSuspendFunctionNotSuspendLambda()

    override fun generateBody() {
        super.generateBody()

        if (doNotGenerateInvokeBridge) {
            v.serializationBindings.put<FunctionDescriptor, Method>(
                METHOD_FOR_FUNCTION,
                originalSuspendFunctionDescriptor,
                typeMapper.mapAsmMethod(erasedInvokeFunction)
            )
        }

        // create() = ...
        functionCodegen.generateMethod(JvmDeclarationOrigin.NO_ORIGIN, createCoroutineDescriptor,
                                       object : FunctionGenerationStrategy.CodegenBased(state) {
                                           override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
                                               generateCreateCoroutineMethod(codegen)
                                           }
                                       })

        if (doNotGenerateInvokeBridge) {
            generateUntypedInvokeMethod()
        } else {
            // invoke(..) = create(..).resume(Unit)
            functionCodegen.generateMethod(JvmDeclarationOrigin.NO_ORIGIN, funDescriptor,
                                           object : FunctionGenerationStrategy.CodegenBased(state) {
                                               override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
                                                   codegen.v.generateInvokeMethod(signature)
                                               }
                                           })
        }
    }

    override fun generateBridges() {
        if (!doNotGenerateInvokeBridge) {
            super.generateBridges()
        }
    }

    private fun generateUntypedInvokeMethod() {
        val untypedDescriptor = getErasedInvokeFunction(funDescriptor)
        val untypedAsmMethod = typeMapper.mapAsmMethod(untypedDescriptor)
        val jvmMethodSignature = typeMapper.mapSignatureSkipGeneric(untypedDescriptor)
        val mv = v.newMethod(
            OtherOrigin(element, funDescriptor), AsmUtil.getVisibilityAccessFlag(untypedDescriptor) or Opcodes.ACC_FINAL,
            untypedAsmMethod.name, untypedAsmMethod.descriptor, null, ArrayUtil.EMPTY_STRING_ARRAY
        )
        mv.visitCode()
        with(InstructionAdapter(mv)) {
            generateInvokeMethod(jvmMethodSignature)
        }

        FunctionCodegen.endVisit(mv, "invoke", element)
    }

    private fun InstructionAdapter.generateInvokeMethod(signature: JvmMethodSignature) {
        // this
        load(0, AsmTypes.OBJECT_TYPE)
        val parameterTypes = signature.valueParameters.map { it.asmType }
        val createArgumentTypes =
            if (generateErasedCreate || doNotGenerateInvokeBridge) typeMapper.mapAsmMethod(createCoroutineDescriptor).argumentTypes.asList()
            else parameterTypes
        var index = 0
        parameterTypes.withVariableIndices().forEach { (varIndex, type) ->
            load(varIndex + 1, type)
            StackValue.coerce(type, createArgumentTypes[index++], this)
        }

        // this.create(..)
        invokevirtual(
            v.thisName,
            createCoroutineDescriptor.name.identifier,
            Type.getMethodDescriptor(
                languageVersionSettings.continuationAsmType(),
                *createArgumentTypes.toTypedArray()
            ),
            false
        )
        checkcast(Type.getObjectType(v.thisName))

        // .doResume(Unit)
        if (languageVersionSettings.isReleaseCoroutines()) {
            invokeInvokeSuspendWithUnit(v.thisName)
        } else {
            invokeDoResumeWithUnit(v.thisName)
        }
        areturn(AsmTypes.OBJECT_TYPE)
    }

    override val passArityToSuperClass get() = true

    override fun generateConstructor(): Method {
        constructorToUseFromInvoke = super.generateConstructor()
        return constructorToUseFromInvoke
    }

    private fun generateCreateCoroutineMethod(codegen: ExpressionCodegen) {
        val classDescriptor = closureContext.contextDescriptor
        val owner = typeMapper.mapClass(classDescriptor)

        val thisInstance = StackValue.thisOrOuter(codegen, classDescriptor, false, false)
        val isBigArity = JvmCodegenUtil.isDeclarationOfBigArityCreateCoroutineMethod(createCoroutineDescriptor)

        with(codegen.v) {
            anew(owner)
            dup()

            // pass captured closure to constructor
            val constructorParameters = calculateConstructorParameters(typeMapper, languageVersionSettings, closure, owner)
            for (parameter in constructorParameters) {
                StackValue.field(parameter, thisInstance).put(parameter.fieldType, parameter.fieldKotlinType, this)
            }

            // load resultContinuation
            if (isBigArity) {
                load(1, AsmTypes.OBJECT_TYPE)
                iconst(allFunctionParameters().size)
                aload(AsmTypes.OBJECT_TYPE)
            } else {
                if (generateErasedCreate) {
                    load(allFunctionParameters().size + 1, AsmTypes.OBJECT_TYPE)
                } else {
                    load(allFunctionParameters().map { typeMapper.mapType(it.type).size }.sum() + 1, AsmTypes.OBJECT_TYPE)
                }
            }

            invokespecial(owner.internalName, constructorToUseFromInvoke.name, constructorToUseFromInvoke.descriptor, false)

            val cloneIndex = codegen.frameMap.enterTemp(AsmTypes.OBJECT_TYPE)
            store(cloneIndex, AsmTypes.OBJECT_TYPE)

            // Pass lambda parameters to 'invoke' call on newly constructed object
            storeParametersInFields(cloneIndex, generateErasedCreate)

            load(cloneIndex, AsmTypes.OBJECT_TYPE)
            areturn(AsmTypes.OBJECT_TYPE)
        }
    }

    private fun InstructionAdapter.storeParametersInFields(receiver: Int, erasedTypes: Boolean) {
        val isBigArity = JvmCodegenUtil.isDeclarationOfBigArityCreateCoroutineMethod(createCoroutineDescriptor)
        var index = 1
        for (parameter in allFunctionParameters()) {
            val fieldInfoForCoroutineLambdaParameter = parameter.getFieldInfoForCoroutineLambdaParameter()
            if (isBigArity) {
                load(receiver, fieldInfoForCoroutineLambdaParameter.ownerType)
                load(1, AsmTypes.OBJECT_TYPE)
                iconst(index - 1)
                aload(AsmTypes.OBJECT_TYPE)
                StackValue.coerce(
                    AsmTypes.OBJECT_TYPE, builtIns.nullableAnyType,
                    fieldInfoForCoroutineLambdaParameter.fieldType, fieldInfoForCoroutineLambdaParameter.fieldKotlinType,
                    this
                )
                putfield(
                    fieldInfoForCoroutineLambdaParameter.ownerInternalName,
                    fieldInfoForCoroutineLambdaParameter.fieldName,
                    fieldInfoForCoroutineLambdaParameter.fieldType.descriptor
                )
            } else {
                if (erasedTypes) {
                    load(index, AsmTypes.OBJECT_TYPE)
                    StackValue.coerce(
                        AsmTypes.OBJECT_TYPE, builtIns.nullableAnyType,
                        fieldInfoForCoroutineLambdaParameter.fieldType, fieldInfoForCoroutineLambdaParameter.fieldKotlinType,
                        this
                    )
                } else {
                    load(index, fieldInfoForCoroutineLambdaParameter.fieldType)
                }
                AsmUtil.genAssignInstanceFieldFromParam(
                    fieldInfoForCoroutineLambdaParameter,
                    index,
                    this,
                    receiver,
                    erasedTypes
                )
            }
            index += if (isBigArity || erasedTypes) 1 else fieldInfoForCoroutineLambdaParameter.fieldType.size
        }
    }

    private fun ExpressionCodegen.initializeCoroutineParameters() {
        for (parameter in allFunctionParameters()) {
            val fieldStackValue =
                StackValue.field(
                    parameter.getFieldInfoForCoroutineLambdaParameter(), generateThisOrOuter(context.thisDescriptor, false)
                )

            val mappedType = typeMapper.mapType(parameter.type)
            fieldStackValue.put(mappedType, v)

            val newIndex = myFrameMap.enter(parameter, mappedType)
            v.store(newIndex, mappedType)

            val name =
                if (parameter is ReceiverParameterDescriptor)
                    AsmUtil.getNameForReceiverParameter(originalSuspendFunctionDescriptor, bindingContext, languageVersionSettings)
                else
                    (getNameForDestructuredParameterOrNull(parameter as ValueParameterDescriptor) ?: parameter.name.asString())
            val label = Label()
            v.mark(label)
            v.visitLocalVariable(name, mappedType.descriptor, null, label, endLabel, newIndex)
        }

        initializeVariablesForDestructuredLambdaParameters(this, originalSuspendFunctionDescriptor.valueParameters, endLabel)
    }

    private fun allFunctionParameters(): List<ParameterDescriptor> =
        originalSuspendFunctionDescriptor.extensionReceiverParameter.let(::listOfNotNull) +
                originalSuspendFunctionDescriptor.valueParameters

    private fun ParameterDescriptor.getFieldInfoForCoroutineLambdaParameter() =
        createHiddenFieldInfo(type, COROUTINE_LAMBDA_PARAMETER_PREFIX + (this.safeAs<ValueParameterDescriptor>()?.index ?: ""))

    private fun createHiddenFieldInfo(type: KotlinType, name: String) =
        FieldInfo.createForHiddenField(
            typeMapper.mapClass(closureContext.thisDescriptor),
            typeMapper.mapType(type),
            type,
            name
        )

    private fun generateResumeImpl() {
        functionCodegen.generateMethod(
            OtherOrigin(element),
            methodToImplement,
            object : FunctionGenerationStrategy.FunctionDefault(state, element as KtDeclarationWithBody) {

                override fun wrapMethodVisitor(mv: MethodVisitor, access: Int, name: String, desc: String): MethodVisitor {
                    val stateMachineBuilder = CoroutineTransformerMethodVisitor(
                        mv, access, name, desc, null, null,
                        obtainClassBuilderForCoroutineState = { v },
                        element = element,
                        diagnostics = state.diagnostics,
                        shouldPreserveClassInitialization = constructorCallNormalizationMode.shouldPreserveClassInitialization,
                        containingClassInternalName = v.thisName,
                        isForNamedFunction = false,
                        languageVersionSettings = languageVersionSettings,
                        onTailCall = {
                            // TODO: Support tail-call crossinline suspend lambdas
                            if (!forInline) {
                                (closure as MutableClosure).setTailCall()
                                classBuilder.isTailCall = true
                            }
                        }
                    )
                    return if (forInline) AddEndLabelMethodVisitor(
                        MethodNodeCopyingMethodVisitor(
                            SurroundSuspendLambdaCallsWithSuspendMarkersMethodVisitor(
                                stateMachineBuilder, access, name, desc, v.thisName,
                                isCapturedSuspendLambda = { isCapturedSuspendLambda(closure, it.name, state.bindingContext) }
                            ), access, name, desc,
                            newMethod = { origin, newAccess, newName, newDesc ->
                                functionCodegen.newMethod(origin, newAccess, newName, newDesc, null, null)
                            }
                        ), access, name, desc, endLabel
                    ) else AddEndLabelMethodVisitor(stateMachineBuilder, access, name, desc, endLabel)
                }

                override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
                    if (element is KtFunctionLiteral) {
                        recordCallLabelForLambdaArgument(element, state.bindingTrace)
                    }
                    codegen.initializeCoroutineParameters()
                    super.doGenerateBody(codegen, signature)
                }
            }
        )
    }

    private class SuspendLambdaClassBuilder(
        val delegate: ClassBuilder,
        private val classNode: ClassNode = ClassNode()
    ) : AbstractClassBuilder.Concrete(classNode) {
        var isTailCall = false
        lateinit var codegen: CoroutineCodegenForLambda
        private val lambdaInternalName = "kotlin/jvm/internal/Lambda"

        override fun done() {
            super.done()
            if (isTailCall && !codegen.forInline) {
                classNode.superName = lambdaInternalName
                if (codegen.languageVersionSettings.isReleaseCoroutines()) {
                    classNode.interfaces.add("kotlin/coroutines/jvm/internal/SuspendFunction")
                }
                classNode.methods.removeIf { it.name == "create" }

                // Generate invoke, than just calls invokeSuspend with continuation parameter
                val invokeMethod =
                    classNode.methods.single { it.name == "invoke" && it.desc == codegen.invokeSignature().asmMethod.descriptor }
                        .also { classNode.methods.remove(it) }
                generateTailCallInvoke(invokeMethod)

                // Replace ALOAD 0 with ALOAD 1 in invokeSuspend
                val invokeSuspendMethod = classNode.methods.single {
                    codegen.languageVersionSettings.isResumeImplMethodName(it.name)
                }
                fixContinuationAccesses(invokeSuspendMethod)

                // Generate correct init
                fixConstructor(classNode)
            }
            classNode.accept(delegate.visitor)
        }

        private fun fixConstructor(classNode: ClassNode) {
            val constructor = classNode.methods.single { it.name == "<init>" }.also { classNode.methods.remove(it) }
            val mv = classNode.visitMethod(
                constructor.access,
                constructor.name,
                constructor.desc.removeContinuationParameter(codegen.languageVersionSettings.isReleaseCoroutines()),
                constructor.signature,
                constructor.exceptions.toTypedArray()
            )
            // suspend lambda's constructor accepts arity and continuation, but lambda's constructor accepts only arity
            val supercall = constructor.instructions.last.previous
            assert(supercall?.opcode == Opcodes.INVOKESPECIAL) {
                "Supercall must be second to last instruction in <init>"
            }
            supercall as MethodInsnNode
            supercall.owner = lambdaInternalName
            supercall.desc = "(I)V"
            assert(supercall.previous?.opcode == Opcodes.ALOAD) {
                "Last parameter of supercall should be continuation"
            }
            constructor.instructions.remove(supercall.previous)
            constructor.instructions.resetLabels()
            mv.visitCode()
            constructor.instructions.accept(mv)
            mv.visitEnd()
        }

        private fun fixContinuationAccesses(invokeSuspendMethod: MethodNode) {
            fun AbstractInsnNode.index() = invokeSuspendMethod.instructions.indexOf(this)

            val suspensionPoints = CoroutineTransformerMethodVisitor.collectSuspensionPoints(invokeSuspendMethod)
            val frames = MethodTransformer.analyze("fake", invokeSuspendMethod, SourceInterpreter())

            val continuationAccesses = mutableSetOf<AbstractInsnNode>()

            // Pass continuation argument to suspension points
            suspensionPoints.flatMapTo(continuationAccesses) { suspensionPoint ->
                frames[suspensionPoint.suspensionCallBegin.index()]?.top()?.insns // If suspension point is intrinsic, stack is empty
                    ?: emptySet()
            }

            // Pass continuation argument as receiver
            invokeSuspendMethod.instructions.asSequence()
                .filterIsInstance<MethodInsnNode>()
                .filter { it.owner == codegen.languageVersionSettings.continuationAsmType().internalName }
                .asIterable()
                .flatMapTo(continuationAccesses) { call ->
                    val parametersSize = Type.getArgumentTypes(call.desc).size
                    frames[call.index()]?.let { it.peek(parametersSize)?.insns ?: error("$call's receiver is not found") } ?: emptySet()
                }

            // Replace all CHECKCAST continuation's targets
            invokeSuspendMethod.instructions.asSequence()
                .filterIsInstance<TypeInsnNode>()
                .filter { it.opcode == Opcodes.CHECKCAST && it.desc == codegen.languageVersionSettings.continuationAsmType().internalName }
                .asIterable()
                .flatMapTo(continuationAccesses) { checkcast ->
                    frames[checkcast.index()]?.let {
                        it.top()?.insns ?: error("$checkcast expects continuation on top of the stack")
                    } ?: emptySet()
                }

            val aload0s = mutableSetOf<VarInsnNode>()
            continuationAccesses.flatMapTo(aload0s) {
                when (it.opcode) {
                    Opcodes.ACONST_NULL -> emptySet() // Passing null as completion. See KT-26658
                    Opcodes.ALOAD -> findAllAload0s(invokeSuspendMethod, frames, it as VarInsnNode).asIterable()
                    Opcodes.GETSTATIC -> emptySet() // There can be singleton as completion
                    else -> error("Expected ACONST_NULL, ALOAD or GETSTATIC as continuation access")
                }
            }
            for (aload0 in aload0s) {
                assert(aload0.isAload0()) { "Cannot replace continuation access $aload0, ALOAD 0 expected" }
                aload0.`var` = 1
            }

            CoroutineTransformerMethodVisitor.dropSuspensionMarkers(invokeSuspendMethod, suspensionPoints)
        }

        // Unroll ALOAD N, ASTORE M sequences generated by the inliner, until we find ALOAD 0
        // TODO: Why RedundantLocalsEliminationMethodTransformer does not remove them?
        private fun findAllAload0s(method: MethodNode, frames: Array<Frame<SourceValue>?>, aload: VarInsnNode): Sequence<VarInsnNode> =
            sequence {
                fun AbstractInsnNode.index() = method.instructions.indexOf(this)

                suspend fun SequenceScope<VarInsnNode>.checkAload(aload: AbstractInsnNode) {
                    assert(aload.opcode == Opcodes.ALOAD) {
                        "Expected ALOAD, but got $aload"
                    }
                    aload as VarInsnNode
                    if (aload.isAload0()) yield(aload)
                    else (yieldAll(findAllAload0s(method, frames, aload)))
                }

                assert(aload.opcode == Opcodes.ALOAD) { "$aload is not ALOAD" }
                if (aload.isAload0()) yield(aload)
                else {
                    val astores = frames[aload.index()]?.getLocal(aload.`var`)?.insns
                        ?: error("No sources of $aload found")
                    for (astore in astores) {
                        assert(astore.opcode == Opcodes.ASTORE) { "Source of $aload shall be ASTORE, but got $astore" }
                        val sources = frames[astore.index()]?.top()?.insns
                            ?: error("No sources of $astore found")
                        for (source in sources) {
                            if (source.opcode == Opcodes.CHECKCAST) {
                                // Instead of ALOAD N, ASTORE M we got ALOAD N, CHECKCAST Continuation, ASTORE M. Handle it
                                source as TypeInsnNode
                                assert(source.desc == codegen.languageVersionSettings.continuationAsmType().internalName) {
                                    "Expected CHECKCAST Continuation, but got $source"
                                }
                                val newAloads = frames[source.index()]?.top()?.insns
                                    ?: error("No sources of $source found")
                                for (newAload in newAloads) {
                                    checkAload(newAload)
                                }
                            } else {
                                checkAload(source)
                            }
                        }
                    }
                }
            }

        private fun AbstractInsnNode.isAload0() = opcode == Opcodes.ALOAD && (this as VarInsnNode).`var` == 0

        private fun generateTailCallInvoke(invokeMethod: MethodNode) {
            val mv = classNode.visitMethod(
                invokeMethod.access, invokeMethod.name, invokeMethod.desc, null, ArrayUtil.EMPTY_STRING_ARRAY
            )
            // copy invoke's annotations
            if (invokeMethod.invisibleAnnotations != null) {
                for (annotation in invokeMethod.invisibleAnnotations) {
                    mv.visitAnnotation(annotation.desc, false)
                }
            }
            if (invokeMethod.visibleAnnotations != null) {
                for (annotation in invokeMethod.visibleAnnotations) {
                    mv.visitAnnotation(annotation.desc, true)
                }
            }
            if (invokeMethod.invisibleParameterAnnotations != null) {
                for ((index, annotations) in invokeMethod.invisibleParameterAnnotations.withIndex()) {
                    if (annotations == null) continue
                    for (annotation in annotations) {
                        mv.visitParameterAnnotation(index, annotation.desc, false)
                    }
                }
            }
            if (invokeMethod.visibleParameterAnnotations != null) {
                for ((index, annotations) in invokeMethod.visibleParameterAnnotations.withIndex()) {
                    if (annotations == null) continue
                    for (annotation in annotations) {
                        mv.visitParameterAnnotation(index, annotation.desc, true)
                    }
                }
            }
            mv.visitCode()
            with(codegen) {
                with(InstructionAdapter(mv)) {
                    // TODO: inline invokeSuspend
                    storeParametersInFields(0, doNotGenerateInvokeBridge)
                    load(0, AsmTypes.OBJECT_TYPE)
                    val continuationIndex = invokeSignature().valueParameters.map { it.asmType.size }.reduce(Int::plus)
                    if (languageVersionSettings.isReleaseCoroutines()) {
                        load(continuationIndex, RELEASE_CONTINUATION_ASM_TYPE)
                        invokeInvokeSuspend(v.thisName)
                    } else {
                        load(continuationIndex, EXPERIMENTAL_CONTINUATION_ASM_TYPE)
                        invokeDoResumeWithNullException(v.thisName)
                    }
                    areturn(AsmTypes.OBJECT_TYPE)
                }
            }
            mv.visitEnd()
        }

        private fun CoroutineCodegenForLambda.invokeSignature(): JvmMethodSignature = typeMapper.mapSignatureSkipGeneric(
            if (doNotGenerateInvokeBridge) getErasedInvokeFunction(funDescriptor) else funDescriptor
        )
    }

    companion object {
        @JvmStatic
        fun create(
            expressionCodegen: ExpressionCodegen,
            originalSuspendLambdaDescriptor: FunctionDescriptor,
            declaration: KtElement,
            classBuilder: ClassBuilder
        ): ClosureCodegen? {
            if (!originalSuspendLambdaDescriptor.isSuspendLambdaOrLocalFunction() || declaration is KtCallableReferenceExpression) return null

            val suspendLambdaClassBuilder = SuspendLambdaClassBuilder(classBuilder)
            return CoroutineCodegenForLambda(
                expressionCodegen,
                declaration,
                expressionCodegen.context.intoCoroutineClosure(
                    getOrCreateJvmSuspendFunctionView(
                        originalSuspendLambdaDescriptor,
                        expressionCodegen.state
                    ),
                    originalSuspendLambdaDescriptor, expressionCodegen, expressionCodegen.state.typeMapper
                ),
                suspendLambdaClassBuilder,
                originalSuspendLambdaDescriptor,
                // Local suspend lambdas, which call crossinline suspend parameters of containing functions must be generated after inlining
                expressionCodegen.bindingContext[CAPTURES_CROSSINLINE_LAMBDA, originalSuspendLambdaDescriptor] == true
            ).also { suspendLambdaClassBuilder.codegen = it }
        }
    }
}

fun String.removeContinuationParameter(isReleaseCoroutines: Boolean): String =
    if (isReleaseCoroutines)
        replace(RELEASE_CONTINUATION_ASM_TYPE.descriptor, "")
    else
        replace(EXPERIMENTAL_CONTINUATION_ASM_TYPE.descriptor, "")

fun isCapturedSuspendLambda(closure: CalculatedClosure, name: String, bindingContext: BindingContext): Boolean {
    for ((param, value) in closure.captureVariables) {
        if (param !is ValueParameterDescriptor) continue
        if (value.fieldName != name) continue
        return param.type.isSuspendFunctionTypeOrSubtype
    }
    val classDescriptor = closure.capturedOuterClassDescriptor ?: return false
    return isCapturedSuspendLambda(classDescriptor, name, bindingContext)
}

fun isCapturedSuspendLambda(classDescriptor: ClassDescriptor, name: String, bindingContext: BindingContext): Boolean {
    val closure = bindingContext[CLOSURE, classDescriptor] ?: return false
    return isCapturedSuspendLambda(closure, name, bindingContext)
}

private class AddEndLabelMethodVisitor(
    delegate: MethodVisitor,
    access: Int,
    name: String,
    desc: String,
    private val endLabel: Label
) : TransformationMethodVisitor(delegate, access, name, desc, null, null) {
    override fun performTransformations(methodNode: MethodNode) {
        methodNode.instructions.add(
            withInstructionAdapter {
                mark(endLabel)
            }
        )
    }
}

class CoroutineCodegenForNamedFunction private constructor(
    outerExpressionCodegen: ExpressionCodegen,
    element: KtElement,
    closureContext: ClosureContext,
    classBuilder: ClassBuilder,
    originalSuspendFunctionDescriptor: FunctionDescriptor
) : AbstractCoroutineCodegen(outerExpressionCodegen, element, closureContext, classBuilder) {
    private val labelFieldStackValue by lazy {
        StackValue.field(
            FieldInfo.createForHiddenField(
                computeLabelOwner(languageVersionSettings, v.thisName),
                Type.INT_TYPE,
                COROUTINE_LABEL_FIELD_NAME
            ),
            StackValue.LOCAL_0
        )
    }


    private val suspendFunctionJvmView =
        bindingContext[CodegenBinding.SUSPEND_FUNCTION_TO_JVM_VIEW, originalSuspendFunctionDescriptor]!!

    override val passArityToSuperClass get() = false

    override fun generateBridges() {
        // Do not generate any closure bridges
    }

    override fun generateClosureBody() {
        generateResumeImpl()

        if (!languageVersionSettings.isReleaseCoroutines()) {
            generateGetLabelMethod()
            generateSetLabelMethod()
        }

        v.newField(
            JvmDeclarationOrigin.NO_ORIGIN, Opcodes.ACC_SYNTHETIC or AsmUtil.NO_FLAG_PACKAGE_PRIVATE,
            languageVersionSettings.dataFieldName(), AsmTypes.OBJECT_TYPE.descriptor, null, null
        )

        if (!languageVersionSettings.isReleaseCoroutines()) {
            v.newField(
                JvmDeclarationOrigin.NO_ORIGIN, Opcodes.ACC_SYNTHETIC or AsmUtil.NO_FLAG_PACKAGE_PRIVATE,
                EXCEPTION_FIELD_NAME, AsmTypes.JAVA_THROWABLE_TYPE.descriptor, null, null
            )
        }
    }

    private fun generateResumeImpl() {
        functionCodegen.generateMethod(
            OtherOrigin(element),
            methodToImplement,
            object : FunctionGenerationStrategy.CodegenBased(state) {
                override fun doGenerateBody(codegen: ExpressionCodegen, signature: JvmMethodSignature) {
                    StackValue.field(
                        AsmTypes.OBJECT_TYPE, Type.getObjectType(v.thisName), languageVersionSettings.dataFieldName(), false,
                        StackValue.LOCAL_0
                    ).store(StackValue.local(1, AsmTypes.OBJECT_TYPE), codegen.v)

                    if (!languageVersionSettings.isReleaseCoroutines()) {
                        StackValue.field(
                            AsmTypes.JAVA_THROWABLE_TYPE, Type.getObjectType(v.thisName), EXCEPTION_FIELD_NAME, false,
                            StackValue.LOCAL_0
                        ).store(StackValue.local(2, AsmTypes.JAVA_THROWABLE_TYPE), codegen.v)
                    }

                    labelFieldStackValue.store(
                        StackValue.operation(Type.INT_TYPE) {
                            labelFieldStackValue.put(Type.INT_TYPE, it)
                            it.iconst(1 shl 31)
                            it.or(Type.INT_TYPE)
                        },
                        codegen.v
                    )

                    val captureThis = closure.capturedOuterClassDescriptor
                    val captureThisType = captureThis?.let(typeMapper::mapType)
                    if (captureThisType != null) {
                        StackValue.field(
                            captureThisType, Type.getObjectType(v.thisName), AsmUtil.CAPTURED_THIS_FIELD,
                            false, StackValue.LOCAL_0
                        ).put(captureThisType, codegen.v)
                    }

                    val isInterfaceMethod = DescriptorUtils.isInterface(suspendFunctionJvmView.containingDeclaration)
                    val callableMethod =
                        typeMapper.mapToCallableMethod(
                            suspendFunctionJvmView,
                            // Obtain default impls method for interfaces
                            isInterfaceMethod
                        )

                    for (argumentType in callableMethod.getAsmMethod().argumentTypes.dropLast(1)) {
                        AsmUtil.pushDefaultValueOnStack(argumentType, codegen.v)
                    }

                    codegen.v.load(0, AsmTypes.OBJECT_TYPE)

                    if (suspendFunctionJvmView.isOverridable && !isInterfaceMethod && captureThisType != null) {
                        val owner = captureThisType.internalName
                        val impl = callableMethod.getAsmMethod().getImplForOpenMethod(owner)
                        codegen.v.invokestatic(owner, impl.name, impl.descriptor, false)
                    } else {
                        callableMethod.genInvokeInstruction(codegen.v)
                    }

                    codegen.v.visitInsn(Opcodes.ARETURN)
                }
            }
        )
    }

    private fun generateGetLabelMethod() {
        val mv = v.newMethod(
            JvmDeclarationOrigin.NO_ORIGIN,
            Opcodes.ACC_SYNTHETIC or Opcodes.ACC_FINAL or AsmUtil.NO_FLAG_PACKAGE_PRIVATE,
            "getLabel",
            Type.getMethodDescriptor(Type.INT_TYPE),
            null,
            null
        )

        mv.visitCode()
        labelFieldStackValue.put(Type.INT_TYPE, InstructionAdapter(mv))
        mv.visitInsn(Opcodes.IRETURN)
        mv.visitEnd()
    }

    private fun generateSetLabelMethod() {
        val mv = v.newMethod(
            JvmDeclarationOrigin.NO_ORIGIN,
            Opcodes.ACC_SYNTHETIC or Opcodes.ACC_FINAL or AsmUtil.NO_FLAG_PACKAGE_PRIVATE,
            "setLabel",
            Type.getMethodDescriptor(Type.VOID_TYPE, Type.INT_TYPE),
            null,
            null
        )

        mv.visitCode()
        labelFieldStackValue.store(StackValue.local(1, Type.INT_TYPE), InstructionAdapter(mv))
        mv.visitInsn(Opcodes.RETURN)
        mv.visitEnd()
    }

    override fun generateKotlinMetadataAnnotation() {
        writeKotlinMetadata(v, state, KotlinClassHeader.Kind.SYNTHETIC_CLASS, 0) { av ->
            val serializer = DescriptorSerializer.createForLambda(JvmSerializerExtension(v.serializationBindings, state))
            val functionProto =
                serializer.functionProto(createFreeFakeLambdaDescriptor(suspendFunctionJvmView))?.build() ?: return@writeKotlinMetadata
            AsmUtil.writeAnnotationData(av, serializer, functionProto)
        }
    }

    companion object {
        fun create(
            cv: ClassBuilder,
            expressionCodegen: ExpressionCodegen,
            originalSuspendDescriptor: FunctionDescriptor,
            declaration: KtFunction
        ): CoroutineCodegenForNamedFunction {
            val bindingContext = expressionCodegen.state.bindingContext
            val closure =
                bindingContext[
                        CodegenBinding.CLOSURE,
                        bindingContext[CodegenBinding.CLASS_FOR_CALLABLE, originalSuspendDescriptor]
                ].sure { "There must be a closure defined for $originalSuspendDescriptor" }

            val suspendFunctionView =
                bindingContext[
                        CodegenBinding.SUSPEND_FUNCTION_TO_JVM_VIEW, originalSuspendDescriptor
                ].sure { "There must be a jvm view defined for $originalSuspendDescriptor" }

            if (suspendFunctionView.dispatchReceiverParameter != null) {
                closure.setNeedsCaptureOuterClass()
            }

            return CoroutineCodegenForNamedFunction(
                expressionCodegen, declaration,
                expressionCodegen.context.intoClosure(
                    originalSuspendDescriptor, expressionCodegen, expressionCodegen.state.typeMapper
                ),
                cv,
                originalSuspendDescriptor
            )
        }
    }
}

private const val COROUTINE_LAMBDA_PARAMETER_PREFIX = "p$"

private object FailingFunctionGenerationStrategy : FunctionGenerationStrategy() {
    override fun skipNotNullAssertionsForParameters(): kotlin.Boolean {
        error("This functions must not be called")
    }

    override fun generateBody(
        mv: MethodVisitor,
        frameMap: FrameMap,
        signature: JvmMethodSignature,
        context: MethodContext,
        parentCodegen: MemberCodegen<*>
    ) {
        error("This functions must not be called")
    }
}
