/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlinx.atomicfu.compiler.extensions

import org.jetbrains.kotlin.backend.common.deepCopyWithVariables
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.builtins.PrimitiveType
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.impl.FunctionDescriptorImpl
import org.jetbrains.kotlin.ir.*
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder.buildValueParameter
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder.buildFunction
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder.buildBlockBody
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.declarations.impl.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.*
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.symbols.impl.*
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import java.lang.IllegalStateException

private const val AFU_PKG = "kotlinx/atomicfu"
private const val LOCKS = "locks"
private const val ATOMIC_CONSTRUCTOR = "atomic"
private const val ATOMICFU_VALUE_TYPE = """Atomic(Int|Long|Boolean|Ref)"""
private const val ATOMIC_ARRAY_TYPE = """Atomic(Int|Long|Boolean|)Array"""
private const val ATOMIC_ARRAY_FACTORY_FUNCTION = "atomicArrayOfNulls"
private const val ATOMICFU_RUNTIME_FUNCTION_PREDICATE = "atomicfu_"
private const val REENTRANT_LOCK_TYPE = "ReentrantLock"
private const val GETTER = "atomicfu\$getter"
private const val SETTER = "atomicfu\$setter"
private const val GET = "get"
private const val SET = "set"

private fun String.prettyStr() = replace('/', '.')

class AtomicFUTransformer(override val context: IrPluginContext) : IrElementTransformerVoid(), TransformerHelpers {

    private val irBuiltIns = context.irBuiltIns

    private val AFU_CLASSES: Map<String, IrType> = mapOf(
        "AtomicInt" to irBuiltIns.intType,
        "AtomicLong" to irBuiltIns.longType,
        "AtomicRef" to irBuiltIns.anyType,
        "AtomicBoolean" to irBuiltIns.booleanType
    )

    private val AFU_ARRAY_CLASSES: Map<String, ClassDescriptor> = mapOf(
        "AtomicIntArray" to context.builtIns.getPrimitiveArrayClassDescriptor(PrimitiveType.INT),
        "AtomicLongArray" to context.builtIns.getPrimitiveArrayClassDescriptor(PrimitiveType.LONG),
        "AtomicBooleanArray" to context.builtIns.getPrimitiveArrayClassDescriptor(PrimitiveType.BOOLEAN),
        "AtomicArray" to context.builtIns.array
    )

    override fun visitFile(irFile: IrFile): IrFile {
        irFile.declarations.map { declaration ->
            declaration.transformAtomicInlineDeclaration()
        }
        return super.visitFile(irFile)
    }

    override fun visitClass(irClass: IrClass): IrStatement {
        irClass.declarations.map { declaration ->
            declaration.transformAtomicInlineDeclaration()
        }
        return super.visitClass(irClass)
    }

    override fun visitProperty(property: IrProperty): IrStatement {
        if (property.backingField != null) {
            val backingField = property.backingField!!
            if (backingField.initializer != null) {
                val initializer = backingField.initializer!!.expression.transformAtomicValueInitializer()
                property.backingField!!.initializer = IrExpressionBodyImpl(initializer)
            }
        }
        return super.visitProperty(property)
    }

    override fun visitBlockBody(body: IrBlockBody): IrBody {
        body.statements.forEachIndexed { i, stmt ->
            if (stmt is IrCall) {
                body.statements[i] = stmt.transformAtomicFunctionCall()
            }
        }
        return super.visitBlockBody(body)
    }

    override fun visitBlock(block: IrBlock): IrExpression {
        block.statements.forEachIndexed { i, stmt ->
            if (stmt is IrCall) {
                block.statements[i] = stmt.transformAtomicFunctionCall()
            }
        }
        return super.visitBlock(block)
    }

    override fun visitReturn(returnExpr: IrReturn): IrExpression {
        returnExpr.value = returnExpr.value.transformAtomicFunctionCall()
        return super.visitReturn(returnExpr)
    }

    override fun visitTypeOperator(typeOp: IrTypeOperatorCall): IrExpression {
        typeOp.argument = typeOp.argument.transformAtomicFunctionCall()
        return super.visitTypeOperator(typeOp)
    }

    override fun visitSetVariable(setVar: IrSetVariable): IrExpression {
        setVar.value = setVar.value.transformAtomicFunctionCall()
        return super.visitSetVariable(setVar)
    }

    override fun visitSetField(setField: IrSetField): IrExpression {
        setField.value = setField.value.transformAtomicFunctionCall()
        return super.visitSetField(setField)
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        declaration.initializer = declaration.initializer?.transformAtomicFunctionCall()
        return super.visitVariable(declaration)
    }

    override fun visitBranch(branch: IrBranch): IrBranch {
        branch.apply {
            condition = condition.transformAtomicFunctionCall()
            result = result.transformAtomicFunctionCall()
        }
        return super.visitBranch(branch)
    }

    override fun visitElseBranch(branch: IrElseBranch): IrElseBranch {
        branch.apply {
            condition = condition.transformAtomicFunctionCall()
            result = result.transformAtomicFunctionCall()
        }
        return super.visitElseBranch(branch)
    }

    private fun IrExpression.transformAtomicValueInitializer() =
        when {
            type.isAtomicValueType() -> getPureTypeValue()
            type.isAtomicArrayType() -> buildPureTypeArrayConstructor()
            type.isReentrantLockType() -> buildConstNull()
            else -> this
        }

    private fun IrDeclaration.transformAtomicInlineDeclaration() {
        if (this is IrFunction &&
            isInline &&
            extensionReceiverParameter != null &&
            extensionReceiverParameter!!.type.isAtomicValueType()
        ) {
            val type = extensionReceiverParameter!!.type
            val valueType = type.atomicToValueType()
            val getterType = buildFunctionSimpleType(listOf(irBuiltIns.unitType, valueType))
            val setterType = buildFunctionSimpleType(listOf(valueType, irBuiltIns.unitType))
            val valueParametersCount = valueParameters.size
            val extendedValueParameters = mutableListOf<IrValueParameter>().apply {
                addAll(valueParameters)
                add(buildValueParameter(GETTER, valueParametersCount, getterType))
                add(buildValueParameter(SETTER, valueParametersCount + 1, setterType))
            }
            this as IrSimpleFunction
            (descriptor as FunctionDescriptorImpl).initialize(
                null,
                descriptor.dispatchReceiverParameter,
                descriptor.typeParameters,
                extendedValueParameters.map { it.descriptor as ValueParameterDescriptor },
                descriptor.returnType,
                descriptor.modality,
                descriptor.visibility
            )
            extensionReceiverParameter = null
            valueParameters = extendedValueParameters
        }
    }

    private fun IrExpression.getPureTypeValue(): IrExpression {
        require(this is IrCall && isAtomicFactoryFunction()) { "Illegal initializer for the atomic property $this" }
        return getValueArgument(0)!!.transformAtomicFunctionCall()
    }

    private fun IrExpression.buildPureTypeArrayConstructor() =
        when (this) {
            is IrConstructorCall -> {
                require(isAtomicArrayConstructor())
                val arrayConstructorSymbol = referenceBuiltInConstructor(type.getArrayClassDescriptor()) { it.valueParameters.size == 1 }
                val size = getValueArgument(0)
                IrConstructorCallImpl(
                    UNDEFINED_OFFSET, UNDEFINED_OFFSET,
                    irBuiltIns.unitType, arrayConstructorSymbol,
                    0, 0, 1
                ).apply {
                    putValueArgument(0, size)
                }
            }
            is IrCall -> {
                require(isAtomicArrayFactoryFunction()) { "Unsupported atomic array factory function $this" }
                val arrayFactorySymbol = referencePackageFunction("kotlin", "arrayOfNulls")
                val arrayElementType = getTypeArgument(0)!!
                val size = getValueArgument(0)
                buildCall(
                    target = arrayFactorySymbol,
                    type = type,
                    origin = IrStatementOrigin.INVOKE,
                    typeArguments = listOf(arrayElementType),
                    valueArguments = listOf(size)
                )
            }
            else -> throw IllegalStateException("Illegal type of atomic array initializer")
        }

    private fun IrCall.runtimeInlineAtomicFunctionCall(callReceiver: IrExpression, accessors: List<IrExpression>): IrCall {
        val valueArguments = getValueArguments()
        val functionName = getAtomicFunctionName()
        val receiverType = callReceiver.type.atomicToValueType()
        val runtimeFunction = getRuntimeFunctionSymbol(functionName, receiverType)
        return buildCall(
            target = runtimeFunction,
            type = type,
            origin = IrStatementOrigin.INVOKE,
            typeArguments = if (runtimeFunction.descriptor.typeParametersCount == 1) listOf(receiverType) else null,
            valueArguments = valueArguments + accessors
        )
    }

    private fun IrExpression.transformAtomicFunctionCall(): IrExpression {
        // erase unchecked cast to the Atomic* type
        if (this is IrTypeOperatorCall && operator == IrTypeOperator.CAST && typeOperand.isAtomicValueType()) {
            return argument
        }
        if (isAtomicValueInitializerCall()) return transformAtomicValueInitializer()
        if (this is IrCall) {
            if (dispatchReceiver != null) {
                dispatchReceiver = dispatchReceiver!!.transformAtomicFunctionCall()
            }
            getValueArguments().forEachIndexed { i, arg ->
                putValueArgument(i, arg?.transformAtomicFunctionCall())
            }
            val isInline = symbol.descriptor.isInline
            val callReceiver = extensionReceiver ?: dispatchReceiver ?: return this
            if (symbol.isKotlinxAtomicfuPackage() && callReceiver.type.isAtomicValueType()) {
                // 1. transform function call on the atomic class field
                if (callReceiver is IrCall) {
                    val accessors = callReceiver.getPropertyAccessors()
                    return runtimeInlineAtomicFunctionCall(callReceiver, accessors)
                }
                // 2. transform function call on the atomic extension receiver
                if (callReceiver is IrGetValue) {
                    val containingDeclaration = callReceiver.symbol.owner.parent as IrFunction
                    val accessorParameters = containingDeclaration.valueParameters.takeLast(2).map { it.capture() }
                    return runtimeInlineAtomicFunctionCall(callReceiver, accessorParameters)
                }
            }
            // 3. transform inline Atomic* extension function call
            if (isInline && callReceiver is IrCall && callReceiver.type.isAtomicValueType()) {
                val accessors = callReceiver.getPropertyAccessors()
                val dispatch = dispatchReceiver
                val args = getValueArguments()
                return buildCall(
                    target = symbol,
                    type = type,
                    origin = IrStatementOrigin.INVOKE,
                    valueArguments = args + accessors
                ).apply {
                    dispatchReceiver = dispatch
                }
            }
        }
        return this
    }

    private fun IrExpression.isAtomicValueInitializerCall() =
        (this is IrCall && (this.isAtomicFactoryFunction() || this.isAtomicArrayFactoryFunction())) ||
                (this is IrConstructorCall && this.isAtomicArrayConstructor()) ||
                type.isReentrantLockType()

    private fun IrCall.isArrayElementGetter() =
        dispatchReceiver != null &&
                dispatchReceiver!!.type.isAtomicArrayType() &&
                symbol.descriptor.name.asString() == GET


    private fun IrCall.buildGetterLambda(): IrExpression {
        val isArrayElement = isArrayElementGetter()
        val getterCall = if (isArrayElement) dispatchReceiver as IrCall else this
        val valueType = type.atomicToValueType()
        val getField = buildGetField(getterCall.getBackingField(), getterCall.dispatchReceiver)
        val getterType = buildFunctionSimpleType(listOf(context.irBuiltIns.unitType, valueType))
        val getterBody = if (isArrayElement) {
            val getSymbol = referenceBuiltInFunction(getterCall.type.getArrayClassDescriptor(), GET)
            val elementIndex = getValueArgument(0)!!.deepCopyWithVariables()
            buildCall(
                target = getSymbol,
                type = valueType,
                origin = IrStatementOrigin.LAMBDA,
                valueArguments = listOf(elementIndex)
            ).apply {
                dispatchReceiver = getField.deepCopyWithVariables()
            }
        } else {
            getField.deepCopyWithVariables()
        }
        return buildAccessorLambda(
            name = getterName(getterCall.symbol.descriptor.name.getFieldName()!!),
            receiver = getterCall,
            type = getterType,
            returnType = valueType,
            valueParameters = emptyList(),
            body = getterBody
        )
    }

    private fun IrCall.buildSetterLambda(): IrExpression {
        val isArrayElement = isArrayElementGetter()
        val getterCall = if (isArrayElement) dispatchReceiver as IrCall else this
        val valueType = type.atomicToValueType()
        val valueParameter = buildValueParameter(index = 0, type = valueType)
        val setterType = buildFunctionSimpleType(listOf(valueType, context.irBuiltIns.unitType))
        val setterBody = if (isArrayElement) {
            val setSymbol = referenceBuiltInFunction(getterCall.type.getArrayClassDescriptor(), SET)
            val elementIndex = getValueArgument(0)!!.deepCopyWithVariables()
            buildCall(
                target = setSymbol,
                type = context.irBuiltIns.unitType,
                origin = IrStatementOrigin.LAMBDA,
                valueArguments = listOf(elementIndex, valueParameter.capture())
            ).apply {
                dispatchReceiver = getterCall
            }
        } else {
            buildSetField(getterCall.getBackingField(), getterCall.dispatchReceiver, valueParameter.capture())
        }
        return buildAccessorLambda(
            name = setterName(getterCall.symbol.descriptor.name.getFieldName()!!),
            receiver = getterCall,
            type = setterType,
            returnType = context.irBuiltIns.unitType,
            valueParameters = listOf(valueParameter),
            body = setterBody
        )
    }

    private fun IrCall.getBackingField(): IrField {
        val correspondingPropertySymbol = (symbol.owner as IrFunctionImpl).correspondingPropertySymbol!!
        return correspondingPropertySymbol.owner.backingField!!
    }

    private fun buildAccessorLambda(
        name: String,
        receiver: IrCall,
        type: IrType,
        returnType: IrType,
        valueParameters: List<IrValueParameter>,
        body: IrExpression
    ): IrFunctionExpression {
        val accessorFunction = buildFunction(
            name = name,
            returnType = returnType,
            parent = receiver.symbol.owner.parent,
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR,
            isInline = true
        ).apply {
            this.valueParameters = valueParameters
            this.body = buildBlockBody(listOf(body))
            origin = IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
        }
        return IrFunctionExpressionImpl(
            UNDEFINED_OFFSET, UNDEFINED_OFFSET,
            type,
            accessorFunction,
            IrStatementOrigin.LAMBDA
        )
    }

    private fun buildSetField(backingField: IrField, ownerClass: IrExpression?, value: IrGetValue): IrSetField {
        val receiver = if (ownerClass is IrTypeOperatorCall) ownerClass.argument as IrGetValue else ownerClass
        val fieldSymbol = backingField.symbol
        return buildSetField(
            symbol = fieldSymbol,
            receiver = receiver,
            value = value
        )
    }

    private fun buildGetField(backingField: IrField, ownerClass: IrExpression?): IrGetField {
        val receiver = if (ownerClass is IrTypeOperatorCall) ownerClass.argument as IrGetValue else ownerClass
        return buildGetField(backingField.symbol, receiver)
    }

    private fun IrCall.getPropertyAccessors(): List<IrExpression> = listOf(buildGetterLambda(), buildSetterLambda())

    private fun getRuntimeFunctionSymbol(name: String, type: IrType): IrSimpleFunctionSymbol {
        val functionName = when (name) {
            "value.<get-value>" -> "getValue"
            "value.<set-value>" -> "setValue"
            else -> name
        }
        return referencePackageFunction("kotlin.js", "$ATOMICFU_RUNTIME_FUNCTION_PREDICATE$functionName") {
            val valueType = it.typeParameters.firstOrNull() ?: it.getGetterParameter().type.arguments.first().type
            valueType.toString() == "T" || valueType == type.toKotlinType()
        }
    }

    private fun FunctionDescriptor.getGetterParameter() = valueParameters[valueParameters.lastIndex - 1]

    private fun IrCall.isAtomicFactoryFunction(): Boolean {
        val name = symbol.descriptor.name
        return !name.isSpecial && name.identifier == ATOMIC_CONSTRUCTOR
    }

    private fun IrCall.isAtomicArrayFactoryFunction(): Boolean {
        val name = symbol.descriptor.name
        return !name.isSpecial && name.identifier == ATOMIC_ARRAY_FACTORY_FUNCTION
    }

    private fun IrConstructorCall.isAtomicArrayConstructor(): Boolean {
        val name = (type as IrSimpleType).classifier.descriptor.name
        return !name.isSpecial && name.identifier.matches(Regex(ATOMIC_ARRAY_TYPE))
    }

    private fun IrSymbol.isKotlinxAtomicfuPackage() =
        this is IrPublicSymbolBase<*> && signature.packageFqName().asString() == AFU_PKG.prettyStr()

    private fun IrType.isAtomicValueType() = belongsTo(ATOMICFU_VALUE_TYPE)
    private fun IrType.isAtomicArrayType() = belongsTo(ATOMIC_ARRAY_TYPE)
    private fun IrType.isReentrantLockType() = belongsTo("$AFU_PKG/$LOCKS", REENTRANT_LOCK_TYPE)

    private fun IrType.belongsTo(typeName: String) = belongsTo(AFU_PKG, typeName)

    private fun IrType.belongsTo(packageName: String, typeName: String): Boolean {
        if (this !is IrSimpleType || classifier !is IrClassPublicSymbolImpl) return false
        val signature = classifier.signature as IdSignature.PublicSignature
        val pckg = signature.packageFqName().asString()
        val type = signature.declarationFqn.asString()
        return pckg == packageName.prettyStr() && type.matches(typeName.toRegex())
    }

    private fun IrCall.getAtomicFunctionName(): String {
        val signature = symbol.signature
        val classFqn = if (signature is IdSignature.AccessorSignature) {
            signature.accessorSignature.declarationFqn
        } else (signature as IdSignature.PublicSignature).declarationFqn
        val pattern = "$ATOMICFU_VALUE_TYPE\\.(.*)".toRegex()
        val declarationName = classFqn.asString()
        return pattern.findAll(declarationName).firstOrNull()?.let { it.groupValues[2] } ?: declarationName
    }

    private fun IrType.getArrayClassDescriptor(): ClassDescriptor {
        val classId = ((this as IrSimpleType).classifier.signature as IdSignature.PublicSignature).declarationFqn.asString()
        return AFU_ARRAY_CLASSES[classId]!!
    }

    private fun IrType.atomicToValueType(): IrType {
        require(isAtomicValueType())
        val classId = ((this as IrSimpleType).classifier.signature as IdSignature.PublicSignature).declarationFqn.asString()
        return AFU_CLASSES[classId]!!
    }

    private fun buildConstNull() = IrConstImpl.constNull(UNDEFINED_OFFSET, UNDEFINED_OFFSET, context.irBuiltIns.anyType)

    companion object {
        fun transform(irFile: IrFile, context: IrPluginContext) =
            irFile.transform(AtomicFUTransformer(context), null)
    }
}
