/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.export.isExported
import org.jetbrains.kotlin.ir.backend.js.utils.getJsName
import org.jetbrains.kotlin.ir.backend.js.utils.isHashCodeInheritedFromAny
import org.jetbrains.kotlin.ir.backend.js.utils.isJsValueOf
import org.jetbrains.kotlin.ir.backend.js.utils.isToStringInheritedFromAny
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classifierOrFail
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import java.util.*

fun eliminateDeadDeclarations(
    module: IrModuleFragment,
    context: JsIrBackendContext,
    mainFunction: IrSimpleFunction?
) {

    val allRoots = buildRoots(module, context, mainFunction)

    val usefulDeclarations = usefulDeclarations(allRoots, context)

    removeUselessDeclarations(module, usefulDeclarations)
}

private fun buildRoots(module: IrModuleFragment, context: JsIrBackendContext, mainFunction: IrSimpleFunction?): Iterable<IrDeclaration> {
    val rootDeclarations =
        (module.files + context.packageLevelJsModules + context.externalPackageFragment.values).flatMapTo(mutableListOf()) { file ->
            file.declarations.filter {
                it is IrField && it.initializer != null && it.fqNameWhenAvailable?.asString()?.startsWith("kotlin") != true
                        || it.isExported(context)
                        || it.isEffectivelyExternal()
                        || it is IrField && it.correspondingPropertySymbol?.owner?.isExported(context) == true
                        || it is IrSimpleFunction && it.correspondingPropertySymbol?.owner?.isExported(context) == true
            }
        }

    if (context.hasTests) rootDeclarations += context.testContainer

    if (mainFunction != null) {
        rootDeclarations += mainFunction
        if (mainFunction.isSuspend) {
            rootDeclarations += context.coroutineEmptyContinuation.owner
        }
    }

    return rootDeclarations
}

private fun removeUselessDeclarations(module: IrModuleFragment, usefulDeclarations: Set<IrDeclaration>) {
    module.files.forEach {
        it.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitFile(declaration: IrFile) {
                process(declaration)
            }

            override fun visitClass(declaration: IrClass) {
                process(declaration)
            }

            override fun visitConstructor(declaration: IrConstructor) {
                if (declaration !in usefulDeclarations) {
                    // Keep the constructor declaration without body in order to declare the JS constructor function
                    declaration.body = IrBlockBodyImpl(UNDEFINED_OFFSET, UNDEFINED_OFFSET, emptyList())
                }
            }

            private fun process(container: IrDeclarationContainer) {
                container.declarations.transformFlat { member ->
                    if (member !in usefulDeclarations && member !is IrConstructor) {
                        emptyList()
                    } else {
                        member.acceptVoid(this)
                        null
                    }
                }
            }
        })
    }
}

private val PRINT_REACHABILITY_INFO = java.lang.Boolean.getBoolean("kotlin.js.ir.dce.print.reachability.info")

fun usefulDeclarations(roots: Iterable<IrDeclaration>, context: JsIrBackendContext): Set<IrDeclaration> {
    val queue = ArrayDeque<IrDeclaration>()
    val result = hashSetOf<IrDeclaration>()
    val constructedClasses = hashSetOf<IrClass>()

    fun IrDeclaration.enqueue(from: IrDeclaration?, description: String?, altFromFqn: String? = null) {
        if (this !in result) {
            // TODO overloads on inline classes? should not be problematic at the end of pipeline
            // TODO don't mark declarations from external to non-external? Or define overrides explicitly?
            // it's wrong solution -- we should prohibit only marking overridens
            if ((from?.isEffectivelyExternal() == true) && !this.isEffectivelyExternal()) return

            if (PRINT_REACHABILITY_INFO) {
                val fromFqn = (from as? IrDeclarationWithName)?.fqNameWhenAvailable?.asString() ?: altFromFqn ?: "<unknown>"

                val toFqn = (this as? IrDeclarationWithName)?.fqNameWhenAvailable?.asString() ?: "<unknown>"

                val v = "\"$fromFqn\" -> \"$toFqn\"" + (if (description.isNullOrBlank()) "" else " // $description")

                println(v)
            }

            result.add(this)
            queue.addLast(this)
        }
    }

    // Add roots
    roots.forEach {
        it.enqueue(null, null, altFromFqn = "<ROOT>")
    }

    // Add roots' nested declarations
    roots.forEach {
        it.acceptVoid(object : IrElementVisitorVoid {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitBody(body: IrBody) {
                // Skip
            }

            override fun visitDeclaration(declaration: IrDeclaration) {
                super.visitDeclaration(declaration)
                declaration.enqueue(it, "roots' nested declaration")
            }
        })
    }

    val toStringMethod =
        context.irBuiltIns.anyClass.owner.declarations.filterIsInstance<IrFunction>().single { it.name.asString() == "toString" }
    val equalsMethod =
        context.irBuiltIns.anyClass.owner.declarations.filterIsInstance<IrFunction>().single { it.name.asString() == "equals" }
    val hashCodeMethod =
        context.irBuiltIns.anyClass.owner.declarations.filterIsInstance<IrFunction>().single { it.name.asString() == "hashCode" }

    while (queue.isNotEmpty()) {
        while (queue.isNotEmpty()) {
            val declaration = queue.pollFirst()

            fun IrDeclaration.enqueue(description: String) {
                enqueue(declaration, description)
            }

            if (declaration is IrClass) {
                declaration.superTypes.forEach {
                    (it.classifierOrNull as? IrClassSymbol)?.owner?.enqueue("superTypes")
                }

                // Special hack for `IntrinsicsJs.kt` support
                if (declaration.superTypes.any { it.isSuspendFunctionTypeOrSubtype() }) {
                    declaration.declarations.forEach {
                        if (it is IrSimpleFunction && it.name.asString().startsWith("invoke")) {
                            it.enqueue("hack for SuspendFunctionN::invoke")
                        }
                    }
                }

                // TODO find out how `doResume` gets removed
                if (declaration.symbol == context.ir.symbols.coroutineImpl) {
                    declaration.declarations.forEach {
                        if (it is IrSimpleFunction && it.name.asString() == "doResume") {
                            it.enqueue("hack for CoroutineImpl::doResume")
                        }
                    }
                }
            }

            if (declaration is IrSimpleFunction) {
                declaration.resolveFakeOverride()?.enqueue("real overridden fun")
            }

            // Collect instantiated classes.
            if (declaration is IrConstructor) {
                declaration.constructedClass.let {
                    it.enqueue("constructed class")
                    constructedClasses += it
                }
            }

            val body = when (declaration) {
                is IrFunction -> declaration.body
                is IrField -> declaration.initializer
                is IrVariable -> declaration.initializer
                else -> null
            }

            body?.acceptVoid(object : IrElementVisitorVoid {
                override fun visitElement(element: IrElement) {
                    element.acceptChildrenVoid(this)
                }

                override fun visitFunctionAccess(expression: IrFunctionAccessExpression) {
                    super.visitFunctionAccess(expression)

                    expression.symbol.owner.enqueue("function access")
                }

                override fun visitVariableAccess(expression: IrValueAccessExpression) {
                    super.visitVariableAccess(expression)

                    expression.symbol.owner.enqueue("variable access")
                }

                override fun visitFieldAccess(expression: IrFieldAccessExpression) {
                    super.visitFieldAccess(expression)

                    expression.symbol.owner.enqueue("field access")
                }

                override fun visitCall(expression: IrCall) {
                    super.visitCall(expression)

                    when (expression.symbol) {
                        context.intrinsics.jsBoxIntrinsic -> {
                            val inlineClass = expression.getTypeArgument(0)!!.getInlinedClass()!!
                            val constructor = inlineClass.declarations.filterIsInstance<IrConstructor>().single { it.isPrimary }
                            constructor.enqueue("intrinsic: jsBoxIntrinsic")
                        }
                        context.intrinsics.jsClass -> {
                            (expression.getTypeArgument(0)!!.classifierOrFail.owner as IrDeclaration)
                                .enqueue("intrinsic: jsClass")
                        }
                        context.intrinsics.jsObjectCreate.symbol -> {
                            val classToCreate = expression.getTypeArgument(0)!!.classifierOrFail.owner as IrClass
                            classToCreate.enqueue("intrinsic: jsObjectCreate")
                            constructedClasses += classToCreate
                        }
                        context.intrinsics.jsEquals -> {
                            ///
                            equalsMethod.enqueue("intrinsic: jsEquals")
                        }
                        context.intrinsics.jsToString -> {
                            // call on concrete type
//                            toStringMethod.enqueue()
                            enqueueMember(declaration, expression.getValueArgument(0)!!.type, toStringMethod, "intrinsic: jsToString") {
                                find { it is IrFunction && it.isToStringInheritedFromAny() }
                            }
                        }
                        context.intrinsics.jsHashCode -> {
                            // call on concrete type
//                            hashCodeMethod.enqueue()
                            enqueueMember(declaration, expression.getValueArgument(0)!!.type, toStringMethod, "intrinsic: jsHashCode") {
                                find { it is IrFunction && it.isHashCodeInheritedFromAny() }
                            }
                        }
                        context.intrinsics.jsPlus -> {
                            /// valueOf, on other ops ??? +=
                            if (expression.getValueArgument(0)?.type?.classOrNull == context.irBuiltIns.stringClass) {
                                enqueueToStringOrValueOf(declaration, expression.getValueArgument(1)?.type, "intrinsic: jsPlus")
//                                toStringMethod.enqueue()
                            }
                        }
                    }
                }

                override fun visitStringConcatenation(expression: IrStringConcatenation) {
                    super.visitStringConcatenation(expression)

//                    toStringMethod.enqueue()
                    expression.arguments.forEach { enqueueToStringOrValueOf(declaration, it.type, "visitStringConcatenation") }
                }

                fun enqueueToStringOrValueOf(from: IrDeclaration, type: IrType?, description: String) {
                    if (type == null) return

                    // can we skip default here, like it's unsafe to do it on dynamic
                    enqueueMember(from, type, toStringMethod, description) {
                        val candidates = filter {
                            it is IrFunction && (it.isJsValueOf(context.irBuiltIns) || it.isToStringInheritedFromAny())
                        }

                        when(candidates.size) {
                            1 -> {
                                candidates[0]
                            }
                            2 -> {
                                if ((candidates[0] as IrFunction).isToStringInheritedFromAny())
                                    candidates[1]
                                else
                                    candidates[0]
                            }
                            else -> null
                        }
                    }
                }

                fun enqueueMember(from: IrDeclaration, type: IrType, default: IrFunction, description: String, getCandidateOrNull: List<IrDeclaration>.() -> IrDeclaration?) {
                    when (val classifier = type.classifierOrNull) {
                        is IrClassSymbol -> {
                            classifier.owner.declarations.getCandidateOrNull()?.enqueue(from, "$description -- candidate")
                        }
                        is IrTypeParameterSymbol -> {
                            classifier.owner.superTypes.forEach { enqueueMember(from, it, default, description, getCandidateOrNull) }
                        }
                        else -> {
                            // it's dynamic or Error type
                            default.enqueue(from, "$description -- default (type: ${type.render()})")
                        }
                    }
                }
            })
        }

        fun IrOverridableDeclaration<*>.findOverriddenUsefulDeclaration(): IrOverridableDeclaration<*>? {
            for (overriddenSymbol in this.overriddenSymbols) {
                (overriddenSymbol.owner as? IrOverridableDeclaration<*>)?.let { overridableDeclaration ->
                    if (overridableDeclaration in result) return overridableDeclaration

                    overridableDeclaration.findOverriddenUsefulDeclaration()?.let {
                        return it
                    }
                }
            }

            return null
        }

        for (klass in constructedClasses) {
            for (declaration in klass.declarations) {
                if (declaration in result) continue

                if (declaration is IrOverridableDeclaration<*>) {
                    declaration.findOverriddenUsefulDeclaration()?.let {
                        declaration.enqueue(it, "overrides useful declaration")
                    }
                }

//                // + < == // TODO prohibit to rename to valueOf, define valueOf somewhere?
//                if (declaration is IrSimpleFunction && declaration.getJsNameOrKotlinName().asString() == "valueOf") {
//                    declaration.enqueue()
//                }

                // A hack to support `toJson` and other js-specific members
                if (declaration.getJsName() != null ||
                    declaration is IrField && declaration.correspondingPropertySymbol?.owner?.getJsName() != null ||
                    declaration is IrSimpleFunction && declaration.correspondingPropertySymbol?.owner?.getJsName() != null
                ) {
                    declaration.enqueue(klass, "annotated by @JsName")
                }
            }
        }
    }

    return result
}