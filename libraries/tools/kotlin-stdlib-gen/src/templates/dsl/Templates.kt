/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates

@DslMarker
annotation class TemplateDsl

enum class Keyword(val value: String) {
    Function("fun"),
    Value("val"),
    Variable("var");
}

typealias MemberBuildAction = MemberBuilder.() -> Unit
typealias MemberBuildActionP<TParam> = MemberBuilder.(TParam) -> Unit

typealias TestBuildAction = TestBuilder.() -> Unit

typealias BuildAction<TBuilder> = TBuilder.() -> Unit
typealias BuildActionP<TBuilder, TParam> = TBuilder.(TParam) -> Unit

private fun def(signature: String, memberKind: Keyword): MemberBuildAction = {
    this.signature = signature
    this.keyword = memberKind
}

fun fn(defaultSignature: String): MemberBuildAction = def(defaultSignature, Keyword.Function)

fun fn(defaultSignature: String, setup: FamilyPrimitiveMemberDefinition.() -> Unit): FamilyPrimitiveMemberDefinition =
        FamilyPrimitiveMemberDefinition().apply {
            builder(fn(defaultSignature))
            setup()
        }

fun MemberBuildAction.byTwoPrimitives(setup: PairPrimitiveMemberDefinition.() -> Unit): PairPrimitiveMemberDefinition =
        PairPrimitiveMemberDefinition().apply {
            builder(this@byTwoPrimitives)
            setup()
        }

fun pval(name: String, setup: FamilyPrimitiveMemberDefinition.() -> Unit): FamilyPrimitiveMemberDefinition =
        FamilyPrimitiveMemberDefinition().apply {
            builder(def(name, Keyword.Value))
            setup()
        }

fun pvar(name: String, setup: FamilyPrimitiveMemberDefinition.() -> Unit): FamilyPrimitiveMemberDefinition =
        FamilyPrimitiveMemberDefinition().apply {
            builder(def(name, Keyword.Variable))
            setup()
        }

fun test(name: String): TestBuildAction = {
    this.name = name
}

fun test(name: String, setup: FamilyPrimitiveTestDefinition.() -> Unit): FamilyPrimitiveTestDefinition =
    FamilyPrimitiveTestDefinition().apply {
        builder(test(name))
        setup()
    }


interface MemberTemplate<TBuilder> {
    /** Specifies which platforms this member template should be generated for */
    fun platforms(vararg platforms: Platform)

    fun instantiate(targets: Collection<KotlinTarget> = KotlinTarget.values): Sequence<TBuilder>

    /** Registers parameterless member builder function */
    fun builder(b: TBuilder.() -> Unit)
}

infix fun <TBuilder, MT: MemberTemplate<TBuilder>> MT.builder(b: TBuilder.() -> Unit): MT = apply { builder(b) }
infix fun <TBuilder, TParam, MT : MemberTemplateDefinition<TBuilder, TParam>> MT.builderWith(b: TBuilder.(TParam) -> Unit): MT = apply { builderWith(b) }

abstract class MemberTemplateDefinition<TBuilder, TParam> : MemberTemplate<TBuilder> {

    sealed class BuildActions<TBuilder> {
        class Generic<TBuilder>(val action: BuildAction<TBuilder>) : BuildActions<TBuilder>() {
            operator fun invoke(builder: TBuilder) { action(builder) }
        }
        class Parametrized<TBuilder>(val action: BuildActionP<TBuilder, *>) : BuildActions<TBuilder>() {
            @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "UNCHECKED_CAST")
            operator fun <TParam> invoke(builder: TBuilder, p: @kotlin.internal.NoInfer TParam) {
                (action as BuildActionP<TBuilder, TParam>).invoke(builder, p)
            }
        }
    }

    private val buildActions = mutableListOf<BuildActions<TBuilder>>()

    private var allowedPlatforms = setOf(*Platform.values())
    override fun platforms(vararg platforms: Platform) {
        allowedPlatforms = setOf(*platforms)
    }


    private var filterPredicate: ((Family, TParam) -> Boolean)? = null
    /** Sets the filter predicate that is applied to a produced sequence of variations. */
    fun filter(predicate: (Family, TParam) -> Boolean) {
        this.filterPredicate = predicate
    }

    override fun builder(b: TBuilder.() -> Unit) { buildActions += BuildActions.Generic(b) }
    /** Registers member builder function with the parameter(s) of this DSL */
    fun builderWith(b: TBuilder.(TParam) -> Unit) { buildActions += BuildActions.Parametrized(b) }



    /** Provides the sequence of member variation parameters */
    protected abstract fun parametrize(): Sequence<Pair<Family, TParam>>

    private fun Sequence<Pair<Family, TParam>>.applyFilter() =
            filterPredicate?.let { predicate ->
                filter { (family, p) -> predicate(family, p) }
            } ?: this


    override fun instantiate(targets: Collection<KotlinTarget>): Sequence<TBuilder> {
        val resultingTargets = targets.filter { it.platform in allowedPlatforms }
        val resultingPlatforms = resultingTargets.map { it.platform }.distinct()
        val specificTargets by lazy { resultingTargets - KotlinTarget.Common }

        fun platformBuilders(family: Family, p: TParam) =
                if (Platform.Common in allowedPlatforms) {
                    val commonBuilder = createBuilder(allowedPlatforms, KotlinTarget.Common, family).applyBuildActions(p)
                    mutableListOf<TBuilder>().also { builders ->
                        if (Platform.Common in resultingPlatforms) builders.add(commonBuilder)
                        if (hasPlatformSpecializations(commonBuilder)) {
                            specificTargets.mapTo(builders) {
                                createBuilder(allowedPlatforms, it, family).applyBuildActions(p)
                            }
                        }
                    }
                } else {
                    resultingTargets.map { createBuilder(allowedPlatforms, it, family).applyBuildActions(p) }
                }

        return parametrize()
                .applyFilter()
                .map { (family, p) -> platformBuilders(family, p) }
                .flatten()
    }

    abstract fun createBuilder(allowedPlatforms: Set<Platform>, target: KotlinTarget, family: Family): TBuilder
    abstract fun hasPlatformSpecializations(builder: TBuilder): Boolean

    private fun TBuilder.applyBuildActions(p: TParam): TBuilder {
        for (action in buildActions) {
            when (action) {
                is BuildActions.Generic<TBuilder> -> action(this)
                is BuildActions.Parametrized<TBuilder> -> action<TParam>(this, p)
            }
        }
        return this
    }

}


private fun defaultPrimitives(f: Family): Set<PrimitiveType> =
    when {
        f == Family.Unsigned || f == Family.ArraysOfUnsigned -> PrimitiveType.unsignedPrimitives
        f == Family.RangesOfPrimitives -> PrimitiveType.rangePrimitives
        f.isPrimitiveSpecialization -> PrimitiveType.defaultPrimitives
        else -> emptySet()
    }

@TemplateDsl
abstract class FamilyPrimitiveDefinitionBase<TBuilder> : MemberTemplateDefinition<TBuilder, PrimitiveType?>() {

    private val familyPrimitives = mutableMapOf<Family, Set<PrimitiveType?>>()

    fun include(vararg fs: Family) {
        for (f in fs) familyPrimitives[f] = defaultPrimitives(f)
    }
    @Deprecated("Use include()", ReplaceWith("include(*fs)"))
    fun only(vararg fs: Family) = include(*fs)

    fun include(fs: Collection<Family>) {
        for (f in fs) familyPrimitives[f] = defaultPrimitives(f)
    }

    fun includeDefault() {
        include(Family.defaultFamilies)
    }

    fun include(f: Family, primitives: Set<PrimitiveType?>) {
        familyPrimitives[f] = primitives
    }

    fun exclude(vararg ps: PrimitiveType) {
        val toExclude = ps.toSet()
        for (e in familyPrimitives) {
            e.setValue(e.value - toExclude)
        }
    }

    override fun parametrize(): Sequence<Pair<Family, PrimitiveType?>> = sequence {
        for ((family, primitives) in familyPrimitives) {
            if (primitives.isEmpty())
                yield(family to null)
            else
                yieldAll(primitives.map { family to it })
        }
    }
}

@TemplateDsl
class FamilyPrimitiveMemberDefinition : FamilyPrimitiveDefinitionBase<MemberBuilder>() {
    override fun createBuilder(allowedPlatforms: Set<Platform>, target: KotlinTarget, family: Family): MemberBuilder {
        return MemberBuilder(allowedPlatforms, target, family)
    }

    override fun hasPlatformSpecializations(builder: MemberBuilder): Boolean {
        return builder.hasPlatformSpecializations
    }

    init {
        builderWith { p -> primitive = p }
    }
}

@TemplateDsl
class PairPrimitiveMemberDefinition : MemberTemplateDefinition<MemberBuilder, Pair<PrimitiveType, PrimitiveType>>() {

    private val familyPrimitives = mutableMapOf<Family, Set<Pair<PrimitiveType, PrimitiveType>>>()

    fun include(f: Family, primitives: Collection<Pair<PrimitiveType, PrimitiveType>>) {
        familyPrimitives[f] = primitives.toSet()
    }

    override fun parametrize(): Sequence<Pair<Family, Pair<PrimitiveType, PrimitiveType>>> {
        return familyPrimitives
                .flatMap { e -> e.value.map { e.key to it } }
                .asSequence()
    }

    override fun createBuilder(allowedPlatforms: Set<Platform>, target: KotlinTarget, family: Family): MemberBuilder {
        return MemberBuilder(allowedPlatforms, target, family)
    }

    override fun hasPlatformSpecializations(builder: MemberBuilder): Boolean {
        return builder.hasPlatformSpecializations
    }

    init {
        builderWith { (p1, p2) -> primitive = p1 }
    }
}


@TemplateDsl
class FamilyPrimitiveTestDefinition : FamilyPrimitiveDefinitionBase<TestBuilder>() {
    override fun createBuilder(allowedPlatforms: Set<Platform>, target: KotlinTarget, family: Family): TestBuilder {
        return TestBuilder(allowedPlatforms, target, family)
    }

    override fun hasPlatformSpecializations(builder: TestBuilder): Boolean {
        return false
    }

    init {
        builderWith { p -> primitive = p }
    }
}

/*
Replacement pattern:
    templates add f\(\"(\w+)(\(.*)
    val f_$1 = fn("$1$2
*/

