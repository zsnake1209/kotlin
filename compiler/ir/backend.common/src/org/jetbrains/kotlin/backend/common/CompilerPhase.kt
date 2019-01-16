/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.common

import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.name
import org.jetbrains.kotlin.ir.util.dump
import kotlin.system.measureTimeMillis

class PhaserState {
    val alreadyDone = mutableSetOf<AnyPhase>()
    var depth = 0
}

fun <R> PhaserState.downlevel(block: () -> R): R {
    depth++
    val result = block()
    depth--
    return result
}

interface CompilerPhase<in Context : CommonBackendContext, Input, Output> {
    fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output

    fun invokeFancy(
        phaseConfig: PhaseConfig,
        phaserState: PhaserState,
        context: Context,
        input: Input
    ): Output = invoke(phaseConfig, phaserState, context, input)

    val prerequisite: Set<CompilerPhase<*, *, *>>
        get() = emptySet()

    fun getNamedSubphases(startDepth: Int = 0): List<Pair<Int, NamedCompilerPhase<*, *, *>>> = emptyList()
}

fun <Context: CommonBackendContext, Input, Output> CompilerPhase<Context,  Input, Output>.invokeToplevel(
    phaseConfig: PhaseConfig,
    context: Context,
    input: Input
): Output = invokeFancy(phaseConfig, PhaserState(), context, input)

interface SameTypeCompilerPhase<in Context: CommonBackendContext, Data> : CompilerPhase<Context, Data, Data>

interface NamedCompilerPhase<in Context : CommonBackendContext, Input, Output> : CompilerPhase<Context, Input, Output> {
    val name: String
    val description: String
}

enum class BeforeOrAfter { BEFORE, AFTER }

abstract class AbstractNamedCompilerPhase<in Context : CommonBackendContext, Input, Output> : NamedCompilerPhase<Context, Input, Output> {
    abstract fun dumpInput(context: Context, input: Input)
    abstract fun verifyInput(context: Context, input: Input)
    abstract fun dumpOutput(context: Context, output: Output)
    abstract fun verifyOutput(context: Context, output: Output)

    override fun invokeFancy(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output {
        if (this is SameTypeCompilerPhase<*, *> &&
                this !in phaseConfig.enabled) {
            return input as Output
        }

        assert(phaserState.alreadyDone.containsAll(prerequisite))
        context.inVerbosePhase = this in phaseConfig.verbose

        runBefore(phaseConfig, context, input)
        val output = runBody(phaseConfig, phaserState, context, input)
        runAfter(phaseConfig, context, output)

        phaserState.alreadyDone.add(this)

        return output
    }

    private fun runBefore(phaseConfig: PhaseConfig, context: Context, input: Input) {
        checkAndRun(phaseConfig.toDumpStateBefore) { dumpInput(context, input) }
        checkAndRun(phaseConfig.toValidateStateBefore) { verifyInput(context, input) }
    }

    private fun runBody(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output {
        return if (phaseConfig.needProfiling) {
            runAndProfile(phaseConfig, phaserState, context, input)
        } else {
            invoke(phaseConfig, phaserState, context, input)
        }
    }

    private fun runAfter(phaseConfig: PhaseConfig, context: Context, output: Output) {
        checkAndRun(phaseConfig.toDumpStateAfter) { dumpOutput(context, output) }
        checkAndRun(phaseConfig.toValidateStateAfter) { verifyOutput(context, output) }
    }

    private fun runAndProfile(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, source: Input): Output {
        var result: Output? = null
        val msec = measureTimeMillis { result = invoke(phaseConfig, phaserState, context, source) }
        println("${"\t".repeat(phaserState.depth)}$description: $msec msec")
        return result!!
    }

    private fun checkAndRun(set: Set<AnyPhase>, block: () -> Unit) {
        if (this in set) block()
    }

    override fun getNamedSubphases(startDepth: Int): List<Pair<Int, NamedCompilerPhase<*, *, *>>> = listOf(startDepth to this)
}

fun <Context : CommonBackendContext, Data: IrElement> dumpIrElement(
    phase: NamedCompilerPhase<Context, *, *>,
    data: Data,
    getName: Data.() -> String,
    beforeOrAfter: BeforeOrAfter
) {
    fun separator(title: String) = println("\n\n--- $title ----------------------\n")

//    if (!shouldBeDumped(context, input)) return

    val beforeOrAfterStr = beforeOrAfter.name.toLowerCase()
    val title = "IR for ${data.getName()} $beforeOrAfterStr ${phase.description}"
    separator(title)
    println(data.dump())
}

// Mixins for dumping and verifying
interface PhaseWithIrInput<in Context: CommonBackendContext, Input: IrElement, Output>: NamedCompilerPhase<Context, Input, Output> {
    val verifyInput: (Context, Input) -> Unit
    val getNameForInput: Input.() -> String
    fun dumpInput(context: Context, input: Input) = dumpIrElement(this, input, getNameForInput, BeforeOrAfter.BEFORE)
    fun verifyInput(context: Context, input: Input): Unit = verifyInput(context, input)
}

interface PhaseWithIrOutput<in Context: CommonBackendContext, Input, Output: IrElement>: NamedCompilerPhase<Context, Input, Output> {
    val verifyOutput: (Context, Output) -> Unit
    val getNameForOutput: Output.() -> String
    fun dumpOutput(context: Context, output: Output) = dumpIrElement(this, output, getNameForOutput, BeforeOrAfter.BEFORE)
    fun verifyOutput(context: Context, output: Output): Unit = verifyOutput(context, output)
}

interface SameTypeIrPhase<in Context: CommonBackendContext, Data: IrElement>:
    SameTypeCompilerPhase<Context, Data>, PhaseWithIrInput<Context, Data, Data>, PhaseWithIrOutput<Context, Data, Data> {
    val verify: (Context, Data) -> Unit
    val getName: Data.() -> String

    override val verifyInput get() = verify
    override val verifyOutput get() = verify
    override val getNameForInput get() = getName
    override val getNameForOutput get() = getName
}

interface PhaseWithUnitInput<in Context: CommonBackendContext, Output> : NamedCompilerPhase<Context, Unit, Output> {
    fun dumpInput(context: Context, input: Unit) {}
    fun verifyInput(context: Context, input: Unit) {}
}

interface PhaseWithUnitOutput<in Context: CommonBackendContext, Input> : NamedCompilerPhase<Context, Input, Unit> {
    fun dumpOutput(context: Context, output: Unit) {}
    fun verifyOutput(context: Context, output: Unit) {}
}

interface SameTypeUnitPhase<in Context: CommonBackendContext>:
    SameTypeCompilerPhase<Context, Unit>, PhaseWithUnitInput<Context, Unit>, PhaseWithUnitOutput<Context, Unit>

abstract class AbstractIrCompilerPhase<in Context : CommonBackendContext, Data : IrElement>(
    override val verify: (Context, Data) -> Unit = { _, _ -> },
    override val getName: Data.() -> String
) : AbstractNamedCompilerPhase<Context, Data, Data>(), SameTypeIrPhase<Context, Data>

abstract class AbstractIrModuleCompilerPhase<in Context : CommonBackendContext>(
    verify: (Context, IrModuleFragment) -> Unit = { _, _ -> }
) : AbstractIrCompilerPhase<Context, IrModuleFragment>(verify, { name.asString() })

abstract class AbstractIrFileCompilerPhase<in Context : CommonBackendContext>(
    verify: (Context, IrFile) -> Unit = { _, _ -> }
) : AbstractIrCompilerPhase<Context, IrFile>(verify, { name })

// Phase composition.
infix fun <Context : CommonBackendContext, Input, Mid, Output> CompilerPhase<Context, Input, Mid>.then(
    other: CompilerPhase<Context, Mid, Output>
) = object : CompilerPhase<Context, Input, Output> {
    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output =
        this@then.invokeFancy(phaseConfig, phaserState, context, input).let { mid ->
            other.invokeFancy(phaseConfig, phaserState, context, mid)
        }

    override fun getNamedSubphases(startDepth: Int) =
        this@then.getNamedSubphases(startDepth) + other.getNamedSubphases(startDepth)
}

// Naming compound phases.
abstract class NamedPhase<in Context : CommonBackendContext, Input, Output>(
    private val lower: CompilerPhase<Context, Input, Output>,
    override val name: String,
    override val description: String,
    override val prerequisite: Set<AnyPhase>
) : AbstractNamedCompilerPhase<Context, Input, Output>() {
    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input) =
        phaserState.downlevel {
            lower.invoke(phaseConfig, phaserState, context, input)
        }

    override fun getNamedSubphases(startDepth: Int) =
        listOf(Pair(startDepth, this)) + lower.getNamedSubphases(startDepth + 1)

    override fun toString() = "Compiler Phase @$name"
}

// The two-layer implementation for `named...Phase()` is because otherwise the compiler does not see
// that dumping functions are implemented by mixins.

abstract class AbstractIrModuleNamedPhase<in Context : CommonBackendContext>(
        lower: CompilerPhase<Context, IrModuleFragment, IrModuleFragment>,
        override val name: String,
        override val description: String,
        override val prerequisite: Set<AnyPhase>
) : NamedPhase<Context, IrModuleFragment, IrModuleFragment>(lower, name, description, prerequisite),
        SameTypeIrPhase<Context, IrModuleFragment>

fun <Context : CommonBackendContext> namedIrModulePhase(
        lower: CompilerPhase<Context, IrModuleFragment, IrModuleFragment>,
        name: String,
        description: String,
        prerequisite: Set<AnyPhase> = emptySet(),
        verify: (Context, IrModuleFragment)-> Unit = { _, _ -> }
) = object : AbstractIrModuleNamedPhase<Context>(lower, name, description, prerequisite) {
    override val verify = verify
    override val getName get(): IrModuleFragment.() -> String = { this.name.asString() }
}

abstract class AbstractIrFileNamedPhase<in Context : CommonBackendContext>(
        lower: CompilerPhase<Context, IrFile, IrFile>,
        override val name: String,
        override val description: String,
        override val prerequisite: Set<AnyPhase>
) : NamedPhase<Context, IrFile, IrFile>(lower, name, description, prerequisite),
        SameTypeIrPhase<Context, IrFile>

fun <Context : CommonBackendContext> namedIrFilePhase(
        lower: CompilerPhase<Context, IrFile, IrFile>,
        name: String,
        description: String,
        prerequisite: Set<AnyPhase> = emptySet(),
        verify: (Context, IrFile)-> Unit = { _, _ -> }
) = object : AbstractIrFileNamedPhase<Context>(lower, name, description, prerequisite) {
    override val verify = verify
    override val getName get(): IrFile.() -> String = { this.name }
}

abstract class AbstractUnitNamedPhase<in Context: CommonBackendContext>(
        lower: CompilerPhase<Context, Unit, Unit>,
        override val name: String,
        override val description: String,
        override val prerequisite: Set<AnyPhase> = emptySet()
) : NamedPhase<Context, Unit, Unit>(lower, name, description, prerequisite),
       SameTypeUnitPhase<Context>

fun <Context: CommonBackendContext> namedUnitPhase(
        lower: CompilerPhase<Context, Unit, Unit>,
        name: String,
        description: String,
        prerequisite: Set<AnyPhase> = emptySet()
) = object : AbstractUnitNamedPhase<Context>(lower, name, description, prerequisite) {}

class PerformByIrFile<Context : CommonBackendContext>(
    private val lower: CompilerPhase<Context, IrFile, IrFile>,
    override val name: String = "PerformByIrFile",
    override val description: String = "Perform phases by IrFile"
) : NamedCompilerPhase<Context, IrModuleFragment, IrModuleFragment> {
    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: IrModuleFragment): IrModuleFragment {
        phaserState.downlevel {
            for (irFile in input.files) {
                lower.invoke(phaseConfig, phaserState, context, irFile)
            }
        }

        // TODO: no guarantee that module identity is preserved by `lower`
        return input
    }

    override fun getNamedSubphases(startDepth: Int) =
        listOf(Pair(startDepth, this)) + lower.getNamedSubphases(startDepth + 1)
}

private typealias AnyPhase = CompilerPhase<*, *, *>

class PhaseConfig(private val compoundPhase: CompilerPhase<*, *, *>, config: CompilerConfiguration) {

    val phases = compoundPhase.getNamedSubphases().map { (_, phase) -> phase }.associate { it.name to it }

    private val enabledMut = computeEnabled(config).toMutableSet()
    val enabled get() = enabledMut as Set<AnyPhase>

    val verbose = phaseSetFromConfiguration(config, CommonConfigurationKeys.VERBOSE_PHASES)

    val toDumpStateBefore: Set<AnyPhase>
    val toDumpStateAfter: Set<AnyPhase>

    val toValidateStateBefore: Set<AnyPhase>
    val toValidateStateAfter: Set<AnyPhase>

    init {
        with(CommonConfigurationKeys) {
            val beforeDumpSet = phaseSetFromConfiguration(config, PHASES_TO_DUMP_STATE_BEFORE)
            val afterDumpSet = phaseSetFromConfiguration(config, PHASES_TO_DUMP_STATE_AFTER)
            val bothDumpSet = phaseSetFromConfiguration(config, PHASES_TO_DUMP_STATE)
            toDumpStateBefore = beforeDumpSet + bothDumpSet
            toDumpStateAfter = afterDumpSet + bothDumpSet
            val beforeValidateSet = phaseSetFromConfiguration(config, PHASES_TO_VALIDATE_BEFORE)
            val afterValidateSet = phaseSetFromConfiguration(config, PHASES_TO_VALIDATE_AFTER)
            val bothValidateSet = phaseSetFromConfiguration(config, PHASES_TO_VALIDATE)
            toValidateStateBefore = beforeValidateSet + bothValidateSet
            toValidateStateAfter = afterValidateSet + bothValidateSet
        }
    }

    val needProfiling = config.getBoolean(CommonConfigurationKeys.PROFILE_PHASES)

    fun known(name: String): String {
        if (phases[name] == null) {
            error("Unknown phase: $name. Use -Xlist-phases to see the list of phases.")
        }
        return name
    }

    fun list() {
        compoundPhase.getNamedSubphases().forEach { (depth, phase) ->
            val enabled = if (phase in enabled) "(Enabled)" else ""
            val verbose = if (phase in verbose) "(Verbose)" else ""

            println(String.format("%1$-50s %2$-50s %3$-10s", "${"\t".repeat(depth)}${phase.name}:", phase.description, "$enabled $verbose"))
        }
    }

    private fun computeEnabled(config: CompilerConfiguration) =
        with(CommonConfigurationKeys) {
            val disabledPhases = phaseSetFromConfiguration(config, DISABLED_PHASES)
            phases.values.toSet() - disabledPhases
        }

    private fun phaseSetFromConfiguration(config: CompilerConfiguration, key: CompilerConfigurationKey<Set<String>>): Set<AnyPhase> {
        val phaseNames = config.get(key) ?: emptySet()
        if ("ALL" in phaseNames) return phases.values.toSet()
        return phaseNames.map { phases[it]!! }.toSet()
    }

    fun enable(phase: AnyPhase) {
        enabledMut.add(phase)
    }

    fun disable(phase: AnyPhase) {
        enabledMut.remove(phase)
    }

    fun switch(phase: AnyPhase, onOff: Boolean) {
        if (onOff) {
            enable(phase)
        } else {
            disable(phase)
        }
    }
}

fun <Context : CommonBackendContext, Data : IrElement> makeIrPhase(
        op : (Context, Data) -> Data,
        description: String,
        name: String,
        prerequisite: Set<AnyPhase> = emptySet(),
        getName: Data.() -> String,
        verify: (Context, Data) -> Unit = { _, _ -> }
) = object : AbstractIrCompilerPhase<Context, Data>(verify, getName) {
    override val name = name
    override val description = description
    override val prerequisite = prerequisite

    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Data): Data {
        op(context, input)
        return input
    }
}

fun <Context : CommonBackendContext> makeIrFilePhase(
    lowering: (Context) -> FileLoweringPass,
    description: String,
    name: String,
    prerequisite: Set<AnyPhase> = emptySet(),
    verify: (Context, IrFile) -> Unit = { _, _ -> }
) = object : AbstractIrFileCompilerPhase<Context>(verify) {
    override val name = name
    override val description = description
    override val prerequisite = prerequisite

    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: IrFile): IrFile {
        lowering(context).lower(input)
        return input
    }
}

fun <Context : CommonBackendContext> makeIrModulePhase(
    lowering: (Context) -> FileLoweringPass,
    description: String,
    name: String,
    prerequisite: Set<AnyPhase> = emptySet(),
    verify: (Context, IrModuleFragment) -> Unit = { _, _ -> }
) = object : AbstractIrModuleCompilerPhase<Context>(verify) {
    override val name = name
    override val description = description
    override val prerequisite = prerequisite

    override fun invoke(
        phaseConfig: PhaseConfig,
        phaserState: PhaserState,
        context: Context,
        input: IrModuleFragment
    ): IrModuleFragment {
        lowering(context).lower(input)
        return input
    }
}

fun <Context : CommonBackendContext, Input> unitPhase(
        name: String,
        description: String,
        prerequisite: Set<AnyPhase>,
        op: Context.() -> Unit
) = object : NamedCompilerPhase<Context, Input, Unit> {
    override val name = name
    override val description = description
    override val prerequisite = prerequisite

    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input) {
        assert(phaserState.alreadyDone.containsAll(prerequisite))
        context.op()
        phaserState.alreadyDone.add(this)
    }
}

fun <Context: CommonBackendContext, Input> unitSink() = object : CompilerPhase<Context, Input, Unit> {
    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input) {}
}

// Intermediate phases to change the object of transformations
fun <Context : CommonBackendContext, OldData, NewData> takeFromContext(op: (Context) -> NewData) =
        object : CompilerPhase<Context, OldData, NewData> {
            override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: OldData) = op(context)
        }

fun <Context : CommonBackendContext, OldData, NewData> transform(op: (OldData) -> NewData) =
        object : CompilerPhase<Context, OldData, NewData> {
            override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: OldData) = op(input)
        }