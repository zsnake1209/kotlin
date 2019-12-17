/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.internal

import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.cli.common.arguments.*
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.logging.kotlinDebug
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.utils.getValue
import org.jetbrains.kotlin.gradle.utils.toSortedPathsArray
import org.jetbrains.kotlin.incremental.classpathAsList
import org.jetbrains.kotlin.incremental.destinationAsFile
import java.io.File

interface CompilerArgumentsContributor<in T : CommonToolArguments> {
    fun contributeArgumentsTo(
        args: T,
        flags: Collection<CompilerArgumentConfigurationFlag>
    )
}

interface CompilerArgumentConfigurationFlag

object DefaultsOnly : CompilerArgumentConfigurationFlag
object IgnoreClasspathResolutionErrors : CompilerArgumentConfigurationFlag

/** The primary purpose of this class is to encapsulate compiler arguments setup in the AbstractKotlinCompiler tasks,
 * but outside the tasks, so that this state & logic can be reused without referencing the task directly. */
open class AbstractKotlinCompileArgumentsContributor<T : CommonCompilerArguments>(
    // Don't save this reference into a property! That would be hostile to Gradle instant execution
    taskProvider: TaskProvider<out AbstractKotlinCompile<T>>
) : CompilerArgumentsContributor<T> {
    private val coroutines by taskProvider.map { it.coroutines }

    protected val logger by taskProvider.map { it.logger }

    private val isMultiplatform by taskProvider.map { it.isMultiplatform }

    private val pluginClasspath by taskProvider.map { it.pluginClasspath }
    private val pluginOptions by taskProvider.map { it.pluginOptions }

    override fun contributeArgumentsTo(
        args: T,
        flags: Collection<CompilerArgumentConfigurationFlag>
    ) {
        args.coroutinesState = when (coroutines) {
            Coroutines.ENABLE -> CommonCompilerArguments.ENABLE
            Coroutines.WARN -> CommonCompilerArguments.WARN
            Coroutines.ERROR -> CommonCompilerArguments.ERROR
            Coroutines.DEFAULT -> CommonCompilerArguments.DEFAULT
        }

        logger.kotlinDebug { "args.coroutinesState=${args.coroutinesState}" }

        if (logger.isDebugEnabled) {
            args.verbose = true
        }

        args.multiPlatform = isMultiplatform

        setupPlugins(args)
    }

    internal fun setupPlugins(compilerArgs: T) {
        compilerArgs.pluginClasspaths = pluginClasspath.toSortedPathsArray()
        compilerArgs.pluginOptions = pluginOptions.arguments.toTypedArray()
    }
}

open class KotlinJvmCompilerArgumentsContributor(
    // Don't save this reference into a property! That would be hostile to Gradle instant execution
    taskProvider: TaskProvider<out KotlinCompile>
) : AbstractKotlinCompileArgumentsContributor<K2JVMCompilerArguments>(taskProvider) {

    private val moduleName by taskProvider.map { it.moduleName }

    private val friendPaths by taskProvider.map { it.friendPaths }

    private val compileClasspath by taskProvider.map { it.compileClasspath }

    private val destinationDir by taskProvider.map { it.destinationDir }

    private val kotlinOptions by taskProvider.map {
        listOfNotNull(
            it.parentKotlinOptionsImpl as KotlinJvmOptionsImpl?,
            it.kotlinOptions as KotlinJvmOptionsImpl
        )
    }

    override fun contributeArgumentsTo(
        args: K2JVMCompilerArguments,
        flags: Collection<CompilerArgumentConfigurationFlag>
    ) {
        args.fillDefaultValues()

        super.contributeArgumentsTo(args, flags)

        args.moduleName = moduleName
        logger.kotlinDebug { "args.moduleName = ${args.moduleName}" }

        args.friendPaths = friendPaths
        logger.kotlinDebug { "args.friendPaths = ${args.friendPaths?.joinToString() ?: "[]"}" }

        if (DefaultsOnly in flags) return

        args.allowNoSourceFiles = true
        args.classpathAsList = try {
            compileClasspath.toList().filter { it.exists() }
        } catch (e: Exception) {
            if (IgnoreClasspathResolutionErrors in flags) emptyList() else throw(e)
        }
        args.destinationAsFile = destinationDir

        kotlinOptions.forEach { it.updateArguments(args) }
    }
}

open class KotlinJsCompilerArgumentsContributor(
    taskProvider: TaskProvider<out Kotlin2JsCompile>
) : AbstractKotlinCompileArgumentsContributor<K2JSCompilerArguments>(taskProvider) {
    private val outputFile by taskProvider.map { it.outputFile }

    private val kotlinOptions by taskProvider.map {
        it.kotlinOptions as KotlinJsOptionsImpl
    }

    override fun contributeArgumentsTo(args: K2JSCompilerArguments, flags: Collection<CompilerArgumentConfigurationFlag>) {
        args.apply { fillDefaultValues() }

        super.contributeArgumentsTo(args, flags)

        args.outputFile = outputFile.canonicalPath

        if (DefaultsOnly in flags) return

        kotlinOptions.updateArguments(args)
    }
}

open class KotlinCommonCompilerArgumentsContributor(
    taskProvider: TaskProvider<out KotlinCompileCommon>
) : AbstractKotlinCompileArgumentsContributor<K2MetadataCompilerArguments>(taskProvider) {

    private val moduleName by taskProvider.map { it.moduleName }

    private val compileClasspath by taskProvider.map { it.compileClasspath }

    private val destinationDir by taskProvider.map { it.destinationDir }

    private val kotlinOptions by taskProvider.map {
        it.kotlinOptions as KotlinMultiplatformCommonOptionsImpl
    }

    override fun contributeArgumentsTo(args: K2MetadataCompilerArguments, flags: Collection<CompilerArgumentConfigurationFlag>) {
        args.apply { fillDefaultValues() }

        super.contributeArgumentsTo(args, flags)

        args.moduleName = moduleName

        if (DefaultsOnly in flags) return

        val classpathList = compileClasspath.toMutableList()

        with(args) {
            classpath = classpathList.joinToString(File.pathSeparator)
            destination = destinationDir.canonicalPath
        }

        kotlinOptions.updateArguments(args)
    }
}

open class KaptGenerateStubsCompilerArgumentsContributor(
    kaptTaskProvider: TaskProvider<out KaptGenerateStubsTask>,
    compileKotlinTaskProvider: TaskProvider<out KotlinCompile>
) : CompilerArgumentsContributor<K2JVMCompilerArguments> {

    private val compileKotlinArgumentsContributor by compileKotlinTaskProvider.map { it.compilerArgumentsContributor }

    private val pluginOptions by kaptTaskProvider.map { it.pluginOptions }
    private val kaptClasspath by kaptTaskProvider.map { it.kaptClasspath }
    private val compileClasspath by kaptTaskProvider.map { it.compileClasspath }

    private val destinationDir by kaptTaskProvider.map { it.destinationDir }

    private val verbose by kaptTaskProvider.map {
        it.project.run {
            hasProperty("kapt.verbose") && property("kapt.verbose").toString().toBoolean() == true
        }
    }

    override fun contributeArgumentsTo(args: K2JVMCompilerArguments, flags: Collection<CompilerArgumentConfigurationFlag>) {
        compileKotlinArgumentsContributor.contributeArgumentsTo(args, flags)

        val pluginOptionsWithKapt = pluginOptions.withWrappedKaptOptions(withApClasspath = kaptClasspath)
        args.pluginOptions = (pluginOptionsWithKapt.arguments + args.pluginOptions!!).toTypedArray()

        args.verbose = verbose
        args.classpathAsList = compileClasspath.toList().filter { it.exists() }
        args.destinationAsFile = destinationDir
    }
}

open class KaptWithKotlincArgumentsContributor(
    private val kaptTaskProvider: TaskProvider<out KaptWithKotlincTask>,
    compileKotlinTaskProvider: TaskProvider<out KotlinCompile>
) : CompilerArgumentsContributor<K2JVMCompilerArguments> {
    private val compileKotlinArgumentsContributor by compileKotlinTaskProvider.map { it.compilerArgumentsContributor }

    private val pluginClasspath by kaptTaskProvider.map { it.pluginClasspath }
    private val pluginOptions by kaptTaskProvider.map { it.pluginOptions }
    private val kaptClasspath by kaptTaskProvider.map { it.kaptClasspath }

    private val changedFiles by kaptTaskProvider.map { it.changedFiles }
    private val classpathChanges by kaptTaskProvider.map { it.classpathChanges }
    private val compiledSources by kaptTaskProvider.map { it.compiledSources }
    private val processIncrementally by kaptTaskProvider.map { it.processIncrementally }

    private val verbose by kaptTaskProvider.map {
        it.project.run {
            hasProperty("kapt.verbose") && property("kapt.verbose").toString().toBoolean() == true
        }
    }

    override fun contributeArgumentsTo(args: K2JVMCompilerArguments, flags: Collection<CompilerArgumentConfigurationFlag>) {
        compileKotlinArgumentsContributor.contributeArgumentsTo(args, flags)

        args.pluginClasspaths = pluginClasspath.toSortedPathsArray()

        val pluginOptionsWithKapt: CompilerPluginOptions = pluginOptions.withWrappedKaptOptions(
            withApClasspath = kaptClasspath,
            changedFiles = changedFiles,
            classpathChanges = classpathChanges,
            compiledSourcesDir = compiledSources,
            processIncrementally = processIncrementally
        )

        args.pluginOptions = (pluginOptionsWithKapt.arguments + args.pluginOptions!!).toTypedArray()

        args.verbose = verbose
    }

}