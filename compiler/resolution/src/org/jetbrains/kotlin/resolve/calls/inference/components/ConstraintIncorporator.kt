/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.components

import org.jetbrains.kotlin.resolve.calls.inference.model.Constraint
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintKind
import org.jetbrains.kotlin.resolve.calls.inference.model.VariableWithConstraints
import org.jetbrains.kotlin.types.*
import org.jetbrains.kotlin.types.model.*
import org.jetbrains.kotlin.utils.SmartSet
import org.jetbrains.kotlin.utils.addIfNotNull
import java.util.*

// todo problem: intersection types in constrains: A <: Number, B <: Inv<A & Any> =>? B <: Inv<out Number & Any>
class ConstraintIncorporator(
    val typeApproximator: TypeApproximator,
    val trivialConstraintTypeInferenceOracle: TrivialConstraintTypeInferenceOracle
) {

    interface Context : TypeSystemInferenceExtensionContext {
        val allTypeVariablesWithConstraints: Collection<VariableWithConstraints>

        // if such type variable is fixed then it is error
        fun getTypeVariable(typeConstructor: TypeConstructorMarker): TypeVariableMarker?

        fun getConstraintsForVariable(typeVariable: TypeVariableMarker): Collection<Constraint>

        fun addNewIncorporatedConstraint(lowerType: KotlinTypeMarker, upperType: KotlinTypeMarker)
    }

    // \alpha is typeVariable, \beta -- other type variable registered in ConstraintStorage
    fun incorporate(c: Context, typeVariable: TypeVariableMarker, constraint: Constraint) = with(c) {
        // we shouldn't incorporate recursive constraint -- It is too dangerous
        if (constraint.type.contains { it.typeConstructor() == typeVariable.freshTypeConstructor() }) return

        directWithVariable(c, typeVariable, constraint)
        otherInsideMyConstraint(c, typeVariable, constraint)
        insideOtherConstraint(c, typeVariable, constraint)
    }

    // A <:(=) \alpha <:(=) B => A <: B
    private fun directWithVariable(c: Context, typeVariable: TypeVariableMarker, constraint: Constraint) {
        // \alpha <: constraint.type
        if (constraint.kind != ConstraintKind.LOWER) {
            c.getConstraintsForVariable(typeVariable).forEach {
                if (it.kind != ConstraintKind.UPPER) {
                    c.addNewIncorporatedConstraint(it.type, constraint.type)
                }
            }
        }

        // constraint.type <: \alpha
        if (constraint.kind != ConstraintKind.UPPER) {
            c.getConstraintsForVariable(typeVariable).forEach {
                if (it.kind != ConstraintKind.LOWER) {
                    c.addNewIncorporatedConstraint(constraint.type, it.type)
                }
            }
        }
    }

    // \alpha <: Inv<\beta>, \beta <: Number => \alpha <: Inv<out Number>
    private fun otherInsideMyConstraint(c: Context, typeVariable: TypeVariableMarker, constraint: Constraint) = with(c) {
        val otherInMyConstraint = SmartSet.create<TypeVariableMarker>()
        constraint.type.contains {
            otherInMyConstraint.addIfNotNull(c.getTypeVariable(it.typeConstructor()))
            false
        }

        for (otherTypeVariable in otherInMyConstraint) {
            // to avoid ConcurrentModificationException
            val otherConstraints = ArrayList(c.getConstraintsForVariable(otherTypeVariable))
            for (otherConstraint in otherConstraints) {
                generateNewConstraint(c, typeVariable, constraint, otherTypeVariable, otherConstraint)
            }
        }
    }

    // \alpha <: Number, \beta <: Inv<\alpha> => \beta <: Inv<out Number>
    private fun insideOtherConstraint(c: Context, typeVariable: TypeVariableMarker, constraint: Constraint) = with(c) {
        for (typeVariableWithConstraint in c.allTypeVariablesWithConstraints) {
            val constraintsWhichConstraintMyVariable = typeVariableWithConstraint.constraints.filter {
                it.type.contains { it.typeConstructor() == typeVariable.freshTypeConstructor() }
            }
            constraintsWhichConstraintMyVariable.forEach {
                generateNewConstraint(c, typeVariableWithConstraint.typeVariable, it, typeVariable, constraint)
            }
        }
    }

    private fun generateNewConstraint(
        c: Context,
        targetVariable: TypeVariableMarker,
        baseConstraint: Constraint,
        otherVariable: TypeVariableMarker,
        otherConstraint: Constraint
    ) {

        val baseConstraintType = baseConstraint.type

        val typeForApproximation = when (otherConstraint.kind) {
            ConstraintKind.EQUALITY -> {
                baseConstraintType.substitute(c, otherVariable, otherConstraint.type)
            }
            ConstraintKind.UPPER -> {
                val temporaryCapturedType = c.createCapturedType(
                    c.createTypeArgument(otherConstraint.type, TypeVariance.OUT),
                    listOf(otherConstraint.type),
                    null,
                    CaptureStatus.FOR_INCORPORATION
                )
//                val newCapturedTypeConstructor = NewCapturedTypeConstructor(
//                    TypeProjectionImpl(Variance.OUT_VARIANCE, otherConstraint.type),
//                    listOf(otherConstraint.type)
//                )
//                val temporaryCapturedType = NewCapturedType(
//                    CaptureStatus.FOR_INCORPORATION,
//                    newCapturedTypeConstructor,
//                    lowerType = null
//                )
                baseConstraintType.substitute(c, otherVariable, temporaryCapturedType)
            }
            ConstraintKind.LOWER -> {
                val temporaryCapturedType = c.createCapturedType(
                    c.createTypeArgument(otherConstraint.type, TypeVariance.IN),
                    emptyList(),
                    otherConstraint.type,
                    CaptureStatus.FOR_INCORPORATION
                )

//                val newCapturedTypeConstructor = NewCapturedTypeConstructor(
//                    TypeProjectionImpl(Variance.IN_VARIANCE, otherConstraint.type),
//                    emptyList()
//                )
//                val temporaryCapturedType = NewCapturedType(
//                    CaptureStatus.FOR_INCORPORATION,
//                    newCapturedTypeConstructor,
//                    lowerType = otherConstraint.type
//                )
                baseConstraintType.substitute(c, otherVariable, temporaryCapturedType)
            }
        }

        if (baseConstraint.kind != ConstraintKind.UPPER) {
            val generatedConstraintType = approximateCapturedTypes(typeForApproximation, toSuper = false)
            if (!trivialConstraintTypeInferenceOracle.isGeneratedConstraintTrivial(c, otherConstraint, generatedConstraintType)) {
                c.addNewIncorporatedConstraint(generatedConstraintType, targetVariable.defaultType(c))
            }
        }
        if (baseConstraint.kind != ConstraintKind.LOWER) {
            val generatedConstraintType = approximateCapturedTypes(typeForApproximation, toSuper = true)
            if (!trivialConstraintTypeInferenceOracle.isGeneratedConstraintTrivial(c, otherConstraint, generatedConstraintType)) {
                c.addNewIncorporatedConstraint(targetVariable.defaultType(c), generatedConstraintType)
            }
        }
    }

    private fun KotlinTypeMarker.substitute(c: Context, typeVariable: TypeVariableMarker, value: KotlinTypeMarker): KotlinTypeMarker {
        val substitutor = c.typeSubstitutorByTypeConstructor(mapOf(typeVariable.freshTypeConstructor(c) to value))
        return substitutor.safeSubstitute(c, this)
    }


    private fun approximateCapturedTypes(type: KotlinTypeMarker, toSuper: Boolean): KotlinTypeMarker =
        if (toSuper) typeApproximator.approximateToSuperType(type, TypeApproximatorConfiguration.IncorporationConfiguration) ?: type
        else typeApproximator.approximateToSubType(type, TypeApproximatorConfiguration.IncorporationConfiguration) ?: type
}
