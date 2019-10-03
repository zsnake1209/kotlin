/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.scripting.compiler.plugin

import com.intellij.core.JavaCoreProjectEnvironment
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.ir.backend.js.utils.NameTables
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.withMessageCollector
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.platform
import org.jetbrains.kotlin.scripting.repl.js.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.jvm.JsDependency

// TODO: the code below has to be considered as temporary hack and removed ASAP.
// Actual ScriptCompilationConfiguration should be set up from CompilerConfiguration.
fun loadScriptConfiguration(configuration: CompilerConfiguration) {
    val scriptConfiguration = ScriptCompilationConfiguration {
        baseClass("kotlin.Any")
        dependencies.append(JsDependency("compiler/ir/serialization.js/build/fullRuntime/klib"))
        platform.put("JS")
    }
    configuration.add(
        ScriptingConfigurationKeys.SCRIPT_DEFINITIONS,
        ScriptDefinition.FromConfigurations(ScriptingHostConfiguration(), scriptConfiguration, null)
    )
}

class JsScriptEvaluationExtension : AbstractScriptEvaluationExtension() {

    class JsScriptCompilerWithDependenciesProxy(private val environment: KotlinCoreEnvironment) : ScriptCompilerProxy {
        private val nameTables = NameTables(emptyList())
        private val symbolTable = SymbolTable()
        private val dependencies: List<ModuleDescriptor> = readLibrariesFromConfiguration(environment.configuration)
        private val compiler = JsCoreScriptingCompiler(environment, nameTables, symbolTable, dependencies)
        private var scriptDependencyCompiler: JsScriptDependencyCompiler? =
            JsScriptDependencyCompiler(environment.configuration, nameTables, symbolTable)

        override fun compile(
            script: SourceCode,
            scriptCompilationConfiguration: ScriptCompilationConfiguration
        ): ResultWithDiagnostics<CompiledScript<*>> {
            val parentMessageCollector = environment.configuration[CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY]
            return withMessageCollector(script = script, parentMessageCollector = parentMessageCollector) { messageCollector ->
                environment.configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
                try {
                    val dependenciesCode = scriptDependencyCompiler?.let { scriptDependencyCompiler = null; it.compile(dependencies) } ?: ""
                    when (val compileResult = compiler.compile(makeReplCodeLine(0, script.text))) {
                        is ReplCompileResult.CompiledClasses -> {
                            val compileJsCode = compileResult.data as String
                            ResultWithDiagnostics.Success(
                                JsCompiledScript(dependenciesCode + "\n" + compileJsCode, scriptCompilationConfiguration)
                            )
                        }
                        is ReplCompileResult.Incomplete -> ResultWithDiagnostics.Failure(
                            ScriptDiagnostic("Incomplete code")
                        )
                        is ReplCompileResult.Error -> ResultWithDiagnostics.Failure(
                            ScriptDiagnostic(
                                message = compileResult.message,
                                severity = ScriptDiagnostic.Severity.ERROR
                            )
                        )
                    }
                } finally {
                    if (parentMessageCollector != null)
                        environment.configuration.put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, parentMessageCollector)
                }
            }
        }
    }

    override fun setupScriptConfiguration(configuration: CompilerConfiguration, sourcePath: String) {
        loadScriptConfiguration(configuration)
    }

    override fun createEnvironment(
        projectEnvironment: JavaCoreProjectEnvironment,
        configuration: CompilerConfiguration
    ): KotlinCoreEnvironment {
        return KotlinCoreEnvironment.createForProduction(
            projectEnvironment,
            configuration,
            EnvironmentConfigFiles.JS_CONFIG_FILES
        )
    }

    override fun createScriptEvaluator(): ScriptEvaluator {
        return JsScriptEvaluator()
    }

    private var scriptCompilerProxy: ScriptCompilerProxy? = null

    override fun createScriptCompiler(environment: KotlinCoreEnvironment): ScriptCompilerProxy {
        return scriptCompilerProxy ?: JsScriptCompilerWithDependenciesProxy(environment).also { scriptCompilerProxy = it }
    }

    override fun ScriptEvaluationConfiguration.Builder.platformEvaluationConfiguration() {

    }

    override fun isAccepted(arguments: CommonCompilerArguments): Boolean {
        return arguments is K2JSCompilerArguments
    }
}
