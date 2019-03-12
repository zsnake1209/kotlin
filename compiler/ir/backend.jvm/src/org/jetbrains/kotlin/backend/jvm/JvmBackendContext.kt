/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm

import org.jetbrains.kotlin.backend.common.CommonBackendContext
import org.jetbrains.kotlin.backend.common.ir.Ir
import org.jetbrains.kotlin.backend.common.ir.Symbols
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.descriptors.JvmDeclarationFactory
import org.jetbrains.kotlin.backend.jvm.descriptors.JvmSharedVariablesManager
import org.jetbrains.kotlin.backend.jvm.lower.fqName
import org.jetbrains.kotlin.builtins.ReflectionTypes
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.descriptors.IrBuiltIns
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.AbstractIrTypeCheckerContext
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi2ir.PsiSourceManager
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.types.model.TypeConstructorMarker

class JvmBackendContext(
    val state: GenerationState,
    val psiSourceManager: PsiSourceManager,
    override val irBuiltIns: IrBuiltIns,
    irModuleFragment: IrModuleFragment, symbolTable: SymbolTable
) : CommonBackendContext {
    override val builtIns = state.module.builtIns
    override val declarationFactory: JvmDeclarationFactory = JvmDeclarationFactory(state)
    override val sharedVariablesManager = JvmSharedVariablesManager(builtIns, irBuiltIns)

    // TODO: inject a correct StorageManager instance, or store NotFoundClasses inside ModuleDescriptor
    internal val reflectionTypes = ReflectionTypes(state.module, NotFoundClasses(LockBasedStorageManager.NO_LOCKS, state.module))

    override val ir = JvmIr(irModuleFragment, symbolTable)

    val phaseConfig = PhaseConfig(jvmPhases, state.configuration)
    override var inVerbosePhase: Boolean = false

    override val configuration get() = state.configuration

    override val typeCheckerContext = object : AbstractIrTypeCheckerContext(irBuiltIns) {
        override fun checkIrTypeConstructorEquality(c1: TypeConstructorMarker, c2: TypeConstructorMarker): Boolean {
            val classifier1 = c1 as IrClassifierSymbol
            val classifier2 = c2 as IrClassifierSymbol
            if (!classifier1.isBound || !classifier2.isBound) checkViaDescriptors(classifier1.descriptor, classifier2.descriptor)
            return checkViaDeclarations(classifier1.owner, classifier2.owner)
        }

        private fun isClassesEqual(c1: IrClass, c2: IrClass) = c1.fqName == c2.fqName

        private fun checkViaDeclarations(classifier1: org.jetbrains.kotlin.ir.declarations.IrSymbolOwner, classifier2: org.jetbrains.kotlin.ir.declarations.IrSymbolOwner): Boolean {
            if (classifier1 is IrClass && classifier2 is IrClass) {
                return isClassesEqual(classifier1, classifier2)
            }

            if (classifier1 is IrTypeParameter && classifier2 is IrTypeParameter) {
                if (classifier1.name != classifier2.name) return false
                val p1 = classifier1.parent
                val p2 = classifier2.parent
                if (p1 is IrClass && p2 is IrClass) return isClassesEqual(p1, p2)
                if (p1 is org.jetbrains.kotlin.ir.declarations.IrSimpleFunction && p2 is org.jetbrains.kotlin.ir.declarations.IrSimpleFunction) {
                    // TODO: implement correct function equality checking
                    return p1.descriptor == p2.descriptor
                }
                return false
            }

            return false
        }

        private fun checkViaDescriptors(classifier1: ClassifierDescriptor, classifier2: ClassifierDescriptor): Boolean {
            if (classifier1 is ClassDescriptor && classifier2 is ClassDescriptor) {
                return classifier1.fqNameSafe == classifier2.fqNameSafe
            }
            if (classifier1 is TypeParameterDescriptor && classifier2 is TypeParameterDescriptor) {
                if (classifier1.name != classifier2.name) return false

                return classifier1.typeConstructor == classifier2.typeConstructor
            }

            return false
        }
    }

    init {
        if (state.configuration.get(CommonConfigurationKeys.LIST_PHASES) == true) {
            phaseConfig.list()
        }
    }

    private fun find(memberScope: MemberScope, className: String): ClassDescriptor {
        return find(memberScope, Name.identifier(className))
    }

    private fun find(memberScope: MemberScope, name: Name): ClassDescriptor {
        return memberScope.getContributedClassifier(name, NoLookupLocation.FROM_BACKEND) as ClassDescriptor
    }

    override fun getInternalClass(name: String): ClassDescriptor {
        return find(state.module.getPackage(FqName("kotlin.jvm.internal")).memberScope, name)
    }

    override fun getClass(fqName: FqName): ClassDescriptor {
        return find(state.module.getPackage(fqName.parent()).memberScope, fqName.shortName())
    }

    fun getIrClass(fqName: FqName): IrClassSymbol {
        return ir.symbols.externalSymbolTable.referenceClass(getClass(fqName))
    }

    override fun getInternalFunctions(name: String): List<FunctionDescriptor> {
        return when (name) {
            "ThrowUninitializedPropertyAccessException" ->
                getInternalClass("Intrinsics").staticScope.getContributedFunctions(
                    Name.identifier("throwUninitializedPropertyAccessException"),
                    NoLookupLocation.FROM_BACKEND
                ).toList()
            else -> TODO(name)
        }
    }

    override fun log(message: () -> String) {
        /*TODO*/
        if (inVerbosePhase) {
            print(message())
        }
    }

    override fun report(element: IrElement?, irFile: IrFile?, message: String, isError: Boolean) {
        /*TODO*/
        print(message)
    }

    inner class JvmIr(
        irModuleFragment: IrModuleFragment,
        private val symbolTable: SymbolTable
    ) : Ir<JvmBackendContext>(this, irModuleFragment) {
        override val symbols = JvmSymbols()

        inner class JvmSymbols : Symbols<JvmBackendContext>(this@JvmBackendContext, symbolTable.lazyWrapper) {

            override val areEqual
                get () = symbolTable.referenceSimpleFunction(context.getInternalFunctions("areEqual").single())

            override val ThrowNullPointerException
                get () = symbolTable.referenceSimpleFunction(
                    context.getInternalFunctions("ThrowNullPointerException").single()
                )

            override val ThrowNoWhenBranchMatchedException
                get () = symbolTable.referenceSimpleFunction(
                    context.getInternalFunctions("ThrowNoWhenBranchMatchedException").single()
                )

            override val ThrowTypeCastException
                get () = symbolTable.referenceSimpleFunction(
                    context.getInternalFunctions("ThrowTypeCastException").single()
                )

            override val ThrowUninitializedPropertyAccessException =
                symbolTable.referenceSimpleFunction(
                    context.getInternalFunctions("ThrowUninitializedPropertyAccessException").single()
                )

            override val stringBuilder
                get() = symbolTable.referenceClass(
                    context.getClass(FqName("java.lang.StringBuilder"))
                )

            override val copyRangeTo: Map<ClassDescriptor, IrSimpleFunctionSymbol>
                get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
            override val coroutineImpl: IrClassSymbol
                get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
            override val coroutineSuspendedGetter: IrSimpleFunctionSymbol
                get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

            override val lateinitIsInitializedPropertyGetter = symbolTable.referenceSimpleFunction(
                state.module.getPackage(FqName("kotlin")).memberScope.getContributedVariables(
                    Name.identifier("isInitialized"), NoLookupLocation.FROM_BACKEND
                ).single {
                    it.extensionReceiverParameter != null && !it.isExternal
                }.getter!!
            )

            val lambdaClass = calc { symbolTable.referenceClass(context.getInternalClass("Lambda")) }

            fun getKFunction(parameterCount: Int) = symbolTable.referenceClass(reflectionTypes.getKFunction(parameterCount))
        }


        override fun shouldGenerateHandlerParameterForDefaultBodyFun() = true
    }
}
