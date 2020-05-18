/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package templates

import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubtypeOf


typealias TemplateGroup<TBuilder> = () -> Sequence<MemberTemplate<TBuilder>>

abstract class TemplateGroupBase<TBuilder> : TemplateGroup<TBuilder> {

    override fun invoke(): Sequence<MemberTemplate<TBuilder>> = sequence {
        with(this@TemplateGroupBase) {
            this::class.members.filter { it.name.startsWith("f_") }.forEach {
                require(it.parameters.size == 1) { "Member $it violates naming convention" }
                @Suppress("UNCHECKED_CAST")
                when {
                    it.returnType.isSubtypeOf(typeMemberTemplate) ->
                        yield(it.call(this) as MemberTemplate<TBuilder>)
                    it.returnType.isSubtypeOf(typeIterableOfMemberTemplates) ->
                        yieldAll(it.call(this) as Iterable<MemberTemplate<TBuilder>>)
                    else ->
                        error("Member $it violates naming convention")
                }
            }
        }
    }.run {
        if (defaultActions.isEmpty()) this else onEach { t -> defaultActions.forEach(t::builder) }
    }

    private val defaultActions = mutableListOf<BuildAction<TBuilder>>()

    fun defaultBuilder(builderAction: BuildAction<TBuilder>) {
        defaultActions += builderAction
    }

    companion object {
        private val typeMemberTemplate = MemberTemplate::class.createType(arguments = listOf(KTypeProjection.STAR))
        private val typeIterableOfMemberTemplates = Iterable::class.createType(arguments = listOf(KTypeProjection.invariant(typeMemberTemplate)))
    }

}

typealias MemberTemplateGroupBase = TemplateGroupBase<MemberBuilder>
typealias TestTemplateGroupBase = TemplateGroupBase<TestBuilder>