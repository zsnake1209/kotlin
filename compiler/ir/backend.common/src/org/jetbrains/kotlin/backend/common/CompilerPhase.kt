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
    val alreadyDone = mutableSetOf<AnyNamedPhase>()
    var depth = 0
}

fun <R> PhaserState.downlevel(nlevels: Int = 1, block: () -> R): R {
    depth += nlevels
    val result = block()
    depth -= nlevels
    return result
}

interface CompilerPhase<in Context : CommonBackendContext, in Input, out Output> {
    fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output

    fun getNamedSubphases(startDepth: Int = 0): List<Pair<Int, NamedCompilerPhase<*, *, *>>> = emptyList()
}

fun <Context: CommonBackendContext, Input, Output> CompilerPhase<Context,  Input, Output>.invokeToplevel(
    phaseConfig: PhaseConfig,
    context: Context,
    input: Input
): Output = invoke(phaseConfig, PhaserState(), context, input)

interface SameTypeCompilerPhase<in Context: CommonBackendContext, Data> : CompilerPhase<Context, Data, Data>

interface NamedCompilerPhase<in Context : CommonBackendContext, in Input, out Output> : CompilerPhase<Context, Input, Output> {
    val name: String
    val description: String
    val prerequisite: Set<AnyNamedPhase> get() = emptySet()
}

typealias AnyNamedPhase = NamedCompilerPhase<*, *, *>

enum class BeforeOrAfter { BEFORE, AFTER }

interface PhaseDumperVerifier<in Context : CommonBackendContext, Data> {
    fun dump(phase: AnyNamedPhase, context: Context, data: Data, beforeOrAfter: BeforeOrAfter)
    fun verify(context: Context, data: Data)
}

abstract class AbstractNamedPhaseWrapper<in Context : CommonBackendContext, Input, Output>(
    override val name: String,
    override val description: String,
    override val prerequisite: Set<AnyNamedPhase>,
    private val nlevels: Int = 0,
    private val lower: CompilerPhase<Context, Input, Output>
) : NamedCompilerPhase<Context, Input, Output> {
    abstract val inputDumperVerifier: PhaseDumperVerifier<Context, Input>
    abstract val outputDumperVerifier: PhaseDumperVerifier<Context, Output>

    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output {
        if (this is SameTypeCompilerPhase<*, *> &&
            this !in phaseConfig.enabled
        ) {
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
        checkAndRun(phaseConfig.toDumpStateBefore) { inputDumperVerifier.dump(this, context, input, BeforeOrAfter.BEFORE) }
        checkAndRun(phaseConfig.toValidateStateBefore) { inputDumperVerifier.verify(context, input) }
    }

    private fun runBody(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output {
        return if (phaseConfig.needProfiling) {
            runAndProfile(phaseConfig, phaserState, context, input)
        } else {
            phaserState.downlevel(nlevels) {
                lower.invoke(phaseConfig, phaserState, context, input)
            }
        }
    }

    private fun runAfter(phaseConfig: PhaseConfig, context: Context, output: Output) {
        checkAndRun(phaseConfig.toDumpStateAfter) { outputDumperVerifier.dump(this, context, output, BeforeOrAfter.AFTER) }
        checkAndRun(phaseConfig.toValidateStateAfter) { outputDumperVerifier.verify(context, output) }
    }

    private fun runAndProfile(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, source: Input): Output {
        var result: Output? = null
        val msec = measureTimeMillis {
            result = phaserState.downlevel(nlevels) {
                lower.invoke(phaseConfig, phaserState, context, source)
            }
        }
        // TODO: use a proper logger
        println("${"\t".repeat(phaserState.depth)}$description: $msec msec")
        return result!!
    }

    private fun checkAndRun(set: Set<AnyNamedPhase>, block: () -> Unit) {
        if (this in set) block()
    }

    override fun getNamedSubphases(startDepth: Int): List<Pair<Int, NamedCompilerPhase<*, *, *>>> =
        listOf(startDepth to this) + lower.getNamedSubphases(startDepth + nlevels)

    override fun toString() = "Compiler Phase @$name"
}

abstract class IrPhaseDumperVerifier<in Context : CommonBackendContext, Data : IrElement>(
    val verifier: (Context, Data) -> Unit
) : PhaseDumperVerifier<Context, Data> {
    abstract fun Data.getElementName(): String

    // TODO: use a proper logger.
    override fun dump(phase: AnyNamedPhase, context: Context, data: Data, beforeOrAfter: BeforeOrAfter) {
        fun separator(title: String) = println("\n\n--- $title ----------------------\n")

        if (!shouldBeDumped(context, data)) return

        val beforeOrAfterStr = beforeOrAfter.name.toLowerCase()
        val title = "IR for ${data.getElementName()} $beforeOrAfterStr ${phase.description}"
        separator(title)
        println(data.dump())
    }

    override fun verify(context: Context, data: Data) = verifier(context, data)

    private fun shouldBeDumped(context: Context, input: Data) =
            input.getElementName() !in context.configuration.get(CommonConfigurationKeys.EXCLUDED_ELEMENTS_FROM_DUMPING, emptySet())
}

class IrFileDumperVerifier<in Context : CommonBackendContext>(verifier: (Context, IrFile) -> Unit) :
    IrPhaseDumperVerifier<Context, IrFile>(verifier) {
    override fun IrFile.getElementName() = name
}

class IrModuleDumperVerifier<in Context : CommonBackendContext>(verifier: (Context, IrModuleFragment) -> Unit) :
    IrPhaseDumperVerifier<Context, IrModuleFragment>(verifier) {
    override fun IrModuleFragment.getElementName() = name.asString()
}

class EmptyDumperVerifier<in Context : CommonBackendContext, Data> : PhaseDumperVerifier<Context, Data> {
    override fun dump(phase: AnyNamedPhase, context: Context, data: Data, beforeOrAfter: BeforeOrAfter) {}
    override fun verify(context: Context, data: Data) {}
}

// Phase composition.
infix fun <Context : CommonBackendContext, Input, Mid, Output> CompilerPhase<Context, Input, Mid>.then(
    other: CompilerPhase<Context, Mid, Output>
) = object : CompilerPhase<Context, Input, Output> {
    override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input): Output =
        this@then.invoke(phaseConfig, phaserState, context, input).let { mid ->
            other.invoke(phaseConfig, phaserState, context, mid)
        }

    override fun getNamedSubphases(startDepth: Int) =
        this@then.getNamedSubphases(startDepth) + other.getNamedSubphases(startDepth)
}

// Naming compound phases.
class SameTypeNamedPhaseWrapper<in Context : CommonBackendContext, Data>(
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase>,
    nlevels: Int = 0,
    lower: CompilerPhase<Context, Data, Data>,
    val dumperVerifier: PhaseDumperVerifier<Context, Data>
) : AbstractNamedPhaseWrapper<Context, Data, Data>(name, description, prerequisite, nlevels, lower), SameTypeCompilerPhase<Context, Data> {
    override val inputDumperVerifier get() = dumperVerifier
    override val outputDumperVerifier get() = dumperVerifier
}

// The two-layer implementation for `named...Phase()` is because otherwise the compiler does not see
// that dumping functions are implemented by mixins.

fun <Context : CommonBackendContext> namedIrModulePhase(
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase> = emptySet(),
    verify: (Context, IrModuleFragment) -> Unit = { _, _ -> },
    nlevels: Int = 1,
    lower: CompilerPhase<Context, IrModuleFragment, IrModuleFragment>
) = SameTypeNamedPhaseWrapper(name, description, prerequisite, nlevels, lower, IrModuleDumperVerifier(verify))

fun <Context : CommonBackendContext> namedIrFilePhase(
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase> = emptySet(),
    verify: (Context, IrFile) -> Unit = { _, _ -> },
    nlevels: Int = 1,
    lower: CompilerPhase<Context, IrFile, IrFile>
) = SameTypeNamedPhaseWrapper(name, description, prerequisite, nlevels, lower, IrFileDumperVerifier(verify))

fun <Context : CommonBackendContext> namedUnitPhase(
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase> = emptySet(),
    nlevels: Int = 1,
    lower: CompilerPhase<Context, Unit, Unit>
) = SameTypeNamedPhaseWrapper(name, description, prerequisite, nlevels, lower, EmptyDumperVerifier())

fun <Context : CommonBackendContext> namedOpUnitPhase(
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase>,
    op: Context.() -> Unit
) = namedUnitPhase(
    name, description, prerequisite,
    nlevels = 0,
    lower = object : SameTypeCompilerPhase<Context, Unit> {
        override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Unit) {
            context.op()
        }
    }
)

fun <Context : CommonBackendContext> performByIrFile(
    name: String = "PerformByIrFile",
    description: String = "Perform phases by IrFile",
    prerequisite: Set<AnyNamedPhase> = emptySet(),
    verify: (Context, IrModuleFragment) -> Unit = { _, _ -> },
    lower: CompilerPhase<Context, IrFile, IrFile>
) = namedIrModulePhase(
    name, description, prerequisite, verify,
    nlevels = 1,
    lower = object : SameTypeCompilerPhase<Context, IrModuleFragment> {
        override fun invoke(
            phaseConfig: PhaseConfig,
            phaserState: PhaserState,
            context: Context,
            input: IrModuleFragment
        ): IrModuleFragment {
            for (irFile in input.files) {
                lower.invoke(phaseConfig, phaserState, context, irFile)
            }

            // TODO: no guarantee that module identity is preserved by `lower`
            return input
        }

        override fun getNamedSubphases(startDepth: Int) = lower.getNamedSubphases(startDepth)
    }
)

fun <Context : CommonBackendContext> makeIrFilePhase(
    lowering: (Context) -> FileLoweringPass,
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase> = emptySet(),
    verify: (Context, IrFile) -> Unit = { _, _ -> }
) = namedIrFilePhase(
    name, description, prerequisite, verify,
    nlevels = 0,
    lower = object : SameTypeCompilerPhase<Context, IrFile> {
        override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: IrFile): IrFile {
            lowering(context).lower(input)
            return input
        }
    }
)

fun <Context : CommonBackendContext> makeIrModulePhase(
    lowering: (Context) -> FileLoweringPass,
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase> = emptySet(),
    verify: (Context, IrModuleFragment) -> Unit = { _, _ -> }
) = namedIrModulePhase(
    name, description, prerequisite, verify,
    nlevels = 0,
    lower = object : SameTypeCompilerPhase<Context, IrModuleFragment> {
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
)

fun <Context : CommonBackendContext, Input> unitPhase(
    name: String,
    description: String,
    prerequisite: Set<AnyNamedPhase>,
    op: Context.() -> Unit
) =
    object : AbstractNamedPhaseWrapper<Context, Input, Unit>(
        name, description, prerequisite,
        nlevels = 0,
        lower = object : CompilerPhase<Context, Input, Unit> {
            override fun invoke(phaseConfig: PhaseConfig, phaserState: PhaserState, context: Context, input: Input) {
                context.op()
            }
        }
    ) {
        override val inputDumperVerifier = EmptyDumperVerifier<Context, Input>()
        override val outputDumperVerifier = EmptyDumperVerifier<Context, Unit>()
    }

fun <Context : CommonBackendContext, Input> unitSink() = object : CompilerPhase<Context, Input, Unit> {
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

class PhaseConfig(private val compoundPhase: CompilerPhase<*, *, *>, config: CompilerConfiguration) {

    val phases = compoundPhase.getNamedSubphases().map { (_, phase) -> phase }.associate { it.name to it }
    private val enabledMut = computeEnabled(config).toMutableSet()

    val enabled: Set<AnyNamedPhase> get() = enabledMut

    val verbose = phaseSetFromConfiguration(config, CommonConfigurationKeys.VERBOSE_PHASES)
    val toDumpStateBefore: Set<AnyNamedPhase>

    val toDumpStateAfter: Set<AnyNamedPhase>
    val toValidateStateBefore: Set<AnyNamedPhase>

    val toValidateStateAfter: Set<AnyNamedPhase>

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

    private fun phaseSetFromConfiguration(config: CompilerConfiguration, key: CompilerConfigurationKey<Set<String>>): Set<AnyNamedPhase> {
        val phaseNames = config.get(key) ?: emptySet()
        if ("ALL" in phaseNames) return phases.values.toSet()
        return phaseNames.map { phases[it]!! }.toSet()
    }

    fun enable(phase: AnyNamedPhase) {
        enabledMut.add(phase)
    }

    fun disable(phase: AnyNamedPhase) {
        enabledMut.remove(phase)
    }
    fun switch(phase: AnyNamedPhase, onOff: Boolean) {
        if (onOff) {
            enable(phase)
        } else {
            disable(phase)
        }
    }
}
