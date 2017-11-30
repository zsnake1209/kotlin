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

package org.jetbrains.kotlin.android.compat

import com.intellij.mock.MockProject
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.android.compat.codegen.CompatExpressionCodegenExtension
import org.jetbrains.kotlin.android.compat.scope.CompatSyntheticsProviderExtension
import org.jetbrains.kotlin.codegen.extensions.ExpressionCodegenExtension
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.synthetic.extensions.SyntheticScopeProviderExtension

class AndroidCompatCommandLineProcessor: CommandLineProcessor {
    override val pluginId = ANDROID_COMPAT_COMPILER_PLUGIN_ID
    override val pluginOptions: Collection<CliOption> = emptyList()

    override fun processOption(option: CliOption, value: String, configuration: CompilerConfiguration) {
        // TODO add configurations
    }

    companion object {
        val ANDROID_COMPAT_COMPILER_PLUGIN_ID: String = "org.jetbrains.kotlin.android"
    }
}

class AndroidCompatComponentRegistrar : ComponentRegistrar {
    override fun registerProjectComponents(project: MockProject, configuration: CompilerConfiguration) {
        registerExtensions(project)
        // TODO: add targetApi version check
    }

    companion object {
        fun registerExtensions(project: Project) {
            SyntheticScopeProviderExtension.registerExtension(project, CompatSyntheticsProviderExtension)
            ExpressionCodegenExtension.registerExtension(project, CompatExpressionCodegenExtension)
        }
    }
}