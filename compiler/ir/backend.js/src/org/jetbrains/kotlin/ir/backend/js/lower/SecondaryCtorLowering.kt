/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.ClassLoweringPass
import org.jetbrains.kotlin.backend.common.DeclarationContainerLoweringPass
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.ir.copyTo
import org.jetbrains.kotlin.backend.common.ir.copyTypeParametersFrom
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.ir.JsIrBuilder
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.transformDeclarationsFlat
import org.jetbrains.kotlin.ir.util.transformFlat
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

data class ConstructorPair(val delegate: IrSimpleFunction, val stub: IrSimpleFunction)

class SecondaryConstructorLowering(val context: JsIrBackendContext) : ClassLoweringPass {

    private val oldCtorToNewMap = context.secondaryConstructorToFactoryCache

    override fun lower(irClass: IrClass) {
        if (irClass.isInline) return

        irClass.declarations.transformFlat {
            if (it is IrConstructor) {
                if (it.isPrimary) null else transformConstructor(it, irClass)
            } else null
        }
    }

    private fun transformConstructor(constructor: IrConstructor, irClass: IrClass): List<IrSimpleFunction> {
        val stubs = oldCtorToNewMap.getOrPut(constructor) {
            buildConstructorStubDeclarations(constructor, irClass)
        }

        generateStubsBody(constructor, irClass, stubs)

        return listOf(stubs.delegate, stubs.stub)
    }

    private fun generateStubsBody(constructor: IrConstructor, irClass: IrClass, stubs: ConstructorPair) {
        generateInitBody(constructor, irClass, stubs.delegate)
        generateFactoryBody(constructor, irClass, stubs.stub, stubs.delegate)
    }

    private fun generateFactoryBody(constructor: IrConstructor, irClass: IrClass, stub: IrSimpleFunction, delegate: IrSimpleFunction) {
        val type = irClass.defaultType
        val createFunctionIntrinsic = context.intrinsics.jsObjectCreate
        val irCreateCall = JsIrBuilder.buildCall(createFunctionIntrinsic.symbol, type, listOf(type))
        val irDelegateCall = JsIrBuilder.buildCall(delegate.symbol, type).also { call ->
            for (i in 0 until stub.valueParameters.size) {
                call.putValueArgument(i, JsIrBuilder.buildGetValue(stub.valueParameters[i].symbol))
            }
//                    valueParameters.forEachIndexed { i, p -> it.putValueArgument(i, JsIrBuilder.buildGetValue(p.symbol)) }
            call.putValueArgument(constructor.valueParameters.size, irCreateCall)

//                typeParameters.mapIndexed { i, t -> ctorImpl.typeParameters[i].descriptor ->  }
        }
        val irReturn = JsIrBuilder.buildReturn(stub.symbol, irDelegateCall, context.irBuiltIns.nothingType)


        stub.body = JsIrBuilder.buildBlockBody(listOf(irReturn))
    }

    private fun generateInitBody(constructor: IrConstructor, irClass: IrClass, delegate: IrSimpleFunction) {
        val thisParam = delegate.valueParameters.last()
        val oldThisReceiver = irClass.thisReceiver!!
        val retStmt = JsIrBuilder.buildReturn(delegate.symbol, JsIrBuilder.buildGetValue(thisParam.symbol), context.irBuiltIns.nothingType)
        val statements = (constructor.body!!.deepCopyWithSymbols(delegate) as IrStatementContainer).statements

        val oldValueParameters = constructor.valueParameters + oldThisReceiver

        // TODO: replace parameters as well
        constructor.body = JsIrBuilder.buildBlockBody(statements + retStmt).apply {
            transformChildrenVoid(ThisUsageReplaceTransformer(delegate.symbol, oldValueParameters.zip(delegate.valueParameters).toMap()))
        }
    }


    private class ThisUsageReplaceTransformer(
        val function: IrFunctionSymbol,
        val symbolMapping: Map<IrValueParameter, IrValueParameter>
    ) : IrElementTransformerVoid() {

        val newThisSymbol = symbolMapping.values.last().symbol

        override fun visitReturn(expression: IrReturn): IrExpression = IrReturnImpl(
            expression.startOffset,
            expression.endOffset,
            expression.type,
            function,
            IrGetValueImpl(expression.startOffset, expression.endOffset, newThisSymbol.owner.type, newThisSymbol)
        )

        override fun visitGetValue(expression: IrGetValue) = symbolMapping[expression.symbol.owner]?.let {
            expression.run { IrGetValueImpl(startOffset, endOffset, type, it.symbol, origin) }
        } ?: expression
    }
}

private fun buildInitDeclaration(
    constructor: IrConstructor,
    irClass: IrClass
): IrSimpleFunction {
    val type = irClass.defaultType
    val constructorName = "${irClass.name}_init"
    val functionName = "${constructorName}_\$Init\$"

    return JsIrBuilder.buildFunction(
        functionName,
        type,
        constructor.parent,
        constructor.visibility,
        Modality.FINAL,
        constructor.isInline,
        constructor.isExternal
    ).also {
        it.copyTypeParametersFrom(constructor)

        constructor.valueParameters.mapTo(it.valueParameters) { p -> p.copyTo(it) }
        it.valueParameters += JsIrBuilder.buildValueParameter("\$this", constructor.valueParameters.size, type).apply { parent = it }
    }
}

private fun buildFactoryDeclaration(
    constructor: IrConstructor,
    irClass: IrClass
): IrSimpleFunction {
    val type = irClass.defaultType
    val constructorName = "${irClass.name}_init"
    val functionName = "${constructorName}_\$Create\$"

    return JsIrBuilder.buildFunction(
        functionName,
        type,
        constructor.parent,
        constructor.visibility,
        Modality.FINAL,
        constructor.isInline,
        constructor.isExternal
    ).also {
        it.copyTypeParametersFrom(constructor)
        it.valueParameters += constructor.valueParameters.map { p -> p.copyTo(it) }
    }
}

private fun buildConstructorStubDeclarations(
    constructor: IrConstructor,
    klass: IrClass
) =
    ConstructorPair(buildInitDeclaration(constructor, klass), buildFactoryDeclaration(constructor, klass))

class SecondaryFactoryInjectorLowering(val context: JsIrBackendContext) : FileLoweringPass {
    override fun lower(irFile: IrFile) {
        irFile.accept(CallsiteRedirectionTransformer(context), null)
    }

    class CallsiteRedirectionTransformer(context: JsIrBackendContext) : IrElementTransformer<IrFunction?> {

        private val oldCtorToNewMap = context.secondaryConstructorToFactoryCache

        override fun visitFunction(declaration: IrFunction, data: IrFunction?): IrStatement = super.visitFunction(declaration, declaration)

        override fun visitCall(expression: IrCall, data: IrFunction?): IrElement {
            super.visitCall(expression, data)

            val target = expression.symbol.owner

            if (target is IrConstructor) {
                if (!target.isPrimary) {
                    val ctor = oldCtorToNewMap.getOrPut(target) {
                        buildConstructorStubDeclarations(target, target.parent as IrClass)
                    }
                    return redirectCall(expression, ctor.stub.symbol)
                }
            }

            return expression
        }

        override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: IrFunction?): IrElement {
            super.visitDelegatingConstructorCall(expression, data)

            val target = expression.symbol.owner
            if (target.isPrimary) {
                // nothing to do here
                return expression
            }

            val fromPrimary = data!! is IrConstructor
            // TODO: what is `deserialized` constructor?
            val ctor = oldCtorToNewMap.getOrPut(target) { buildConstructorStubDeclarations(target, target.parent as IrClass) }
            val newCall = redirectCall(expression, ctor.delegate.symbol)

            val readThis = if (fromPrimary) {
                val thisKlass = expression.symbol.owner.parent as IrClass
                val thisSymbol = thisKlass.thisReceiver!!.symbol
                IrGetValueImpl(expression.startOffset, expression.endOffset, expression.type, thisSymbol)
            } else {
                IrGetValueImpl(expression.startOffset, expression.endOffset, expression.type, data.valueParameters.last().symbol)
            }

            newCall.putValueArgument(expression.valueArgumentsCount, readThis)

            return newCall
        }

        private fun redirectCall(
            call: IrFunctionAccessExpression,
            newTarget: IrSimpleFunctionSymbol
        ) = IrCallImpl(call.startOffset, call.endOffset, call.type, newTarget).apply {

            // TODO: Should constructors have type arguments
            // copyTypeArgumentsFrom(call)

            for (i in 0 until call.valueArgumentsCount) {
                putValueArgument(i, call.getValueArgument(i))
            }
        }
    }
}

class SecondaryCtorLowering(val context: JsIrBackendContext) {

    private val oldCtorToNewMap = context.secondaryConstructorToFactoryCache

    val constructorProcessorLowering = object : DeclarationContainerLoweringPass {
        override fun lower(irDeclarationContainer: IrDeclarationContainer) {
            irDeclarationContainer.declarations.filterIsInstance<IrClass>().forEach {
                if (!it.isInline)  // Inline classes are lowered separately
                    lowerClass(it)
            }
        }
    }

    val constructorRedirectorLowering = object : DeclarationContainerLoweringPass {
        override fun lower(irDeclarationContainer: IrDeclarationContainer) {
            if (irDeclarationContainer is IrClass) {
                if (!irDeclarationContainer.isInline)   // Inline classes are lowered separately
                    updateConstructorDeclarations(irDeclarationContainer)
            }
            for (it in irDeclarationContainer.declarations) {
                it.accept(CallsiteRedirectionTransformer(), null)
            }
        }
    }

    private fun updateConstructorDeclarations(irClass: IrClass) {
        irClass.transformDeclarationsFlat {
            if (it is IrConstructor) {
                oldCtorToNewMap[it.symbol]?.let { (newInit, newCreate) ->
                    listOf(newInit, newCreate)
                }
            } else null
        }
    }

    private fun lowerClass(irClass: IrClass): List<IrSimpleFunction> {
        val className = irClass.name.asString()
        val newConstructors = mutableListOf<IrSimpleFunction>()

        for (declaration in irClass.declarations) {
            if (declaration is IrConstructor && !declaration.isPrimary) {
                // TODO delegate name generation
                val constructorName = "${className}_init"
                // We should split secondary constructor into two functions,
                //   *  Initializer which contains constructor's body and takes just created object as implicit param `$this`
                //   **   This function is also delegation constructor
                //   *  Creation function which has same signature with original constructor,
                //      creates new object via `Object.create` builtIn and passes it to corresponding `Init` function
                // In other words:
                // Foo::constructor(...) {
                //   body
                // }
                // =>
                // Foo_init_$Init$(..., $this) {
                //   body[ this = $this ]
                //   return $this
                // }
                // Foo_init_$Create$(...) {
                //   val t = Object.create(Foo.prototype);
                //   return Foo_init_$Init$(..., t)
                // }
                val newInitConstructor = createInitConstructor(declaration, irClass, constructorName, irClass.defaultType)
                val newCreateConstructor = createCreateConstructor(declaration, newInitConstructor, constructorName, irClass.defaultType)

                oldCtorToNewMap[declaration] = ConstructorPair(newInitConstructor, newCreateConstructor)

                newConstructors += newInitConstructor
                newConstructors += newCreateConstructor
            }
        }

        return newConstructors
    }

    private class ThisUsageReplaceTransformer(
        val function: IrFunctionSymbol,
        val symbolMapping: Map<IrValueParameter, IrValueParameter>
    ) :
        IrElementTransformerVoid() {

        val newThisSymbol = symbolMapping.values.last().symbol

        override fun visitReturn(expression: IrReturn): IrExpression = IrReturnImpl(
            expression.startOffset,
            expression.endOffset,
            expression.type,
            function,
            IrGetValueImpl(expression.startOffset, expression.endOffset, newThisSymbol.owner.type, newThisSymbol)
        )

        override fun visitGetValue(expression: IrGetValue): IrExpression =
            if (expression.symbol.owner in symbolMapping) IrGetValueImpl(
                expression.startOffset,
                expression.endOffset,
                expression.type,
                symbolMapping[expression.symbol.owner]!!.symbol,
                expression.origin
            ) else {
                expression
            }
    }

    private fun createInitConstructor(
        declaration: IrConstructor,
        klass: IrClass,
        name: String,
        type: IrType
    ): IrSimpleFunction {

        val thisParam = JsIrBuilder.buildValueParameter("\$this", declaration.valueParameters.size, type)
        val oldThisReceiver = klass.thisReceiver!!
        val functionName = "${name}_\$Init\$"

        return JsIrBuilder.buildFunction(
            functionName,
            type,
            declaration.parent,
            declaration.visibility,
            Modality.FINAL,
            declaration.isInline,
            declaration.isExternal
        ).also {
            thisParam.run { parent = it }
            val retStmt = JsIrBuilder.buildReturn(it.symbol, JsIrBuilder.buildGetValue(thisParam.symbol), context.irBuiltIns.nothingType)
            val statements = (declaration.body!!.deepCopyWithSymbols(it) as IrStatementContainer).statements

            it.copyTypeParametersFrom(declaration)

            val newValueParameters = declaration.valueParameters.map { p -> p.copyTo(it) }
            it.valueParameters += (newValueParameters + thisParam)

            val oldValueParameters = declaration.valueParameters + oldThisReceiver

            // TODO: replace parameters as well
            it.body = JsIrBuilder.buildBlockBody(statements + retStmt).apply {
                transformChildrenVoid(ThisUsageReplaceTransformer(it.symbol, oldValueParameters.zip(it.valueParameters).toMap()))
            }
        }
    }

    private fun createCreateConstructor(
        declaration: IrConstructor,
        ctorImpl: IrSimpleFunction,
        name: String,
        type: IrType
    ): IrSimpleFunction {

        val functionName = "${name}_\$Create\$"

        return JsIrBuilder.buildFunction(
            functionName,
            type,
            declaration.parent,
            declaration.visibility,
            Modality.FINAL,
            declaration.isInline,
            declaration.isExternal
        ).also {
            it.copyTypeParametersFrom(declaration)
            it.valueParameters += declaration.valueParameters.map { p -> p.copyTo(it) }

            val createFunctionIntrinsic = context.intrinsics.jsObjectCreate
            val irCreateCall = JsIrBuilder.buildCall(createFunctionIntrinsic.symbol, type, listOf(type))
            val irDelegateCall = JsIrBuilder.buildCall(ctorImpl.symbol, type).also { call ->
                for (i in 0 until it.valueParameters.size) {
                    call.putValueArgument(i, JsIrBuilder.buildGetValue(it.valueParameters[i].symbol))
                }
//                    valueParameters.forEachIndexed { i, p -> it.putValueArgument(i, JsIrBuilder.buildGetValue(p.symbol)) }
                call.putValueArgument(declaration.valueParameters.size, irCreateCall)

//                typeParameters.mapIndexed { i, t -> ctorImpl.typeParameters[i].descriptor ->  }
            }
            val irReturn = JsIrBuilder.buildReturn(it.symbol, irDelegateCall, context.irBuiltIns.nothingType)


            it.body = JsIrBuilder.buildBlockBody(listOf(irReturn))
        }
    }

    inner class CallsiteRedirectionTransformer : IrElementTransformer<IrFunction?> {

        override fun visitFunction(declaration: IrFunction, data: IrFunction?): IrStatement = super.visitFunction(declaration, declaration)

        override fun visitCall(expression: IrCall, data: IrFunction?): IrElement {
            super.visitCall(expression, data)

            val target = expression.symbol.owner

            if (target is IrConstructor) {
                if (!target.isPrimary) {
                    val ctor = oldCtorToNewMap[target.symbol]
                    if (ctor != null) {
                        return redirectCall(expression, ctor.stub.symbol)
                    }
                }
            }

            return expression
        }

        override fun visitDelegatingConstructorCall(expression: IrDelegatingConstructorCall, data: IrFunction?): IrElement {
            super.visitDelegatingConstructorCall(expression, data)

            val target = expression.symbol
            if (target.owner.isPrimary) {
                // nothing to do here
                return expression
            }

            val fromPrimary = data!! is IrConstructor
            // TODO: what is `deserialized` constructor?
            val ctor = oldCtorToNewMap[target] ?: return expression
            val newCall = redirectCall(expression, ctor.delegate.symbol)

            val readThis = if (fromPrimary) {
                val thisKlass = expression.symbol.owner.parent as IrClass
                val thisSymbol = thisKlass.thisReceiver!!.symbol
                IrGetValueImpl(expression.startOffset, expression.endOffset, expression.type, thisSymbol)
            } else {
                IrGetValueImpl(expression.startOffset, expression.endOffset, expression.type, data.valueParameters.last().symbol)
            }

            newCall.putValueArgument(expression.valueArgumentsCount, readThis)

            return newCall
        }

        private fun redirectCall(
            call: IrFunctionAccessExpression,
            newTarget: IrSimpleFunctionSymbol
        ) = IrCallImpl(call.startOffset, call.endOffset, call.type, newTarget).apply {

            // TODO: Should constructors have type arguments
            // copyTypeArgumentsFrom(call)

            for (i in 0 until call.valueArgumentsCount) {
                putValueArgument(i, call.getValueArgument(i))
            }
        }
    }
}
