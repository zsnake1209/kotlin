/*
 * Copyright 2010-2017 JetBrains s.r.o.
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

package org.jetbrains.kotlin.backend.jvm

import org.jetbrains.kotlin.backend.common.asFileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.*
import org.jetbrains.kotlin.backend.common.runOnFilePostfix
import org.jetbrains.kotlin.backend.jvm.lower.*
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.util.PatchDeclarationParentsVisitor
import org.jetbrains.kotlin.ir.util.dump
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.NameUtils
import java.io.File

class JvmLower(val context: JvmBackendContext) {
    fun lower(irFile: IrFile) {
        val fileName = File(irFile.name).name
        val stagesToDump = context.state.configuration[JVMConfigurationKeys.DUMP_IR_AT] ?: emptySet()
        val outputDirectory = context.state.configuration[JVMConfigurationKeys.OUTPUT_DIRECTORY] ?: File("").absoluteFile

        if ("start" in stagesToDump) {
            outputDirectory.resolve("$fileName.ir.start").writeText(irFile.dump())
        }

        // TODO run lowering passes as callbacks in bottom-up visitor
        listOf(
            FileClassLowering(context),
            KCallableNamePropertyLowering(context),

            LateinitLowering(context, true),

            ConstAndJvmFieldPropertiesLowering(context),
            PropertiesLowering(),
            AnnotationLowering(), //should be run before defaults lowering

            //Should be before interface lowering
            DefaultArgumentStubGenerator(context, false),

            InterfaceLowering(context),
            InterfaceDelegationLowering(context),
            SharedVariablesLowering(context),

            PatchDeclarationParentsVisitor().asFileLoweringPass(),

            LocalDeclarationsLowering(
                context,
                object : LocalNameProvider {
                    override fun localName(descriptor: DeclarationDescriptor): String =
                        NameUtils.sanitizeAsJavaIdentifier(super.localName(descriptor))
                },
                Visibilities.PUBLIC, //TODO properly figure out visibility
                true
            ),
            CallableReferenceLowering(context),

            InnerClassesLowering(context),
            InnerClassConstructorCallsLowering(context),

            PatchDeclarationParentsVisitor().asFileLoweringPass(),

            EnumClassLowering(context),
            //Should be before SyntheticAccessorLowering cause of synthetic accessor for companion constructor
            ObjectClassLowering(context),
            InitializersLowering(context, JvmLoweredDeclarationOrigin.CLASS_STATIC_INITIALIZER, true),
            SingletonReferencesLowering(context),
            SyntheticAccessorLowering(context),
            BridgeLowering(context),
            JvmOverloadsAnnotationLowering(context),
            JvmStaticAnnotationLowering(context),
            StaticDefaultFunctionLowering(context.state),

            TailrecLowering(context),
            ToArrayLowering(context),

            PatchDeclarationParentsVisitor().asFileLoweringPass()
        ).forEach { lowering ->
            lowering.lower(irFile)
            val loweringName = lowering::class.simpleName
            if (loweringName in stagesToDump) {
                outputDirectory.resolve("$fileName.ir.$loweringName").writeText(irFile.dump())
            }
        }

        if ("end" in stagesToDump) {
            outputDirectory.resolve("$fileName.ir.end").writeText(irFile.dump())
        }

    }
}
