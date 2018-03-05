/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls.inference.model

import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.resolve.calls.inference.components.SortedConstraints
import org.jetbrains.kotlin.resolve.calls.inference.components.SpecialTypeVariableKind
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability.INAPPLICABLE
import org.jetbrains.kotlin.resolve.calls.tower.ResolutionCandidateApplicability.INAPPLICABLE_WRONG_RECEIVER
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.UnwrappedType


sealed class ConstraintPosition {
    open val message: String?
        get() = toString()
}

class ExplicitTypeParameterConstraintPosition(val typeArgument: SimpleTypeArgument) : ConstraintPosition() {
    override fun toString() = "TypeParameter $typeArgument"
    override val message: String?
        get() = null
}
class ExpectedTypeConstraintPosition(val topLevelCall: KotlinCall) : ConstraintPosition() {
    override val message: String?
        get() = "expected type for '${topLevelCall.name.asString()}'"

    override fun toString() = "ExpectedType for call $topLevelCall"
}
class DeclaredUpperBoundConstraintPosition(val typeParameterDescriptor: TypeParameterDescriptor) : ConstraintPosition() {
    override val message get() = "declared upper bound ${typeParameterDescriptor.name}"
    override fun toString() = "DeclaredUpperBound ${typeParameterDescriptor.name} from ${typeParameterDescriptor.containingDeclaration}"
}
class ArgumentConstraintPosition(val argument: KotlinCallArgument, val parameterName: String? = null) : ConstraintPosition() {
    override val message
        get() = if (parameterName != null) "for parameter '$parameterName'" else null
    override fun toString() = "Argument $argument"
}

class ReceiverConstraintPosition(val argument: KotlinCallArgument) : ConstraintPosition() {
    override fun toString() = "Receiver $argument"
}

class FixVariableConstraintPosition(val variable: NewTypeVariable) : ConstraintPosition() {
    override fun toString() = "Fix variable $variable"
}

class KnownTypeParameterConstraintPosition(val typeArgument: KotlinType) : ConstraintPosition() {
    override fun toString() = "TypeArgument $typeArgument"
}

class LambdaArgumentConstraintPosition(val lambda: ResolvedLambdaAtom) : ConstraintPosition() {
    override fun toString(): String {
        return "LambdaArgument $lambda"
    }
}

class IncorporationConstraintPosition(val from: ConstraintPosition, val initialConstraint: InitialConstraint) : ConstraintPosition() {
    override fun toString() = "Incorporate $initialConstraint from position $from"
}

@Deprecated("Should be used only in SimpleConstraintSystemImpl")
object SimpleConstraintSystemConstraintPosition : ConstraintPosition()

abstract class ConstraintSystemCallDiagnostic(applicability: ResolutionCandidateApplicability) : KotlinCallDiagnostic(applicability) {
    override fun report(reporter: DiagnosticReporter) = reporter.constraintError(this)
}

class NewConstraintError(
    val lowerType: UnwrappedType,
    val upperType: UnwrappedType,
    val position: IncorporationConstraintPosition
) : ConstraintSystemCallDiagnostic(if (position.from is ReceiverConstraintPosition) INAPPLICABLE_WRONG_RECEIVER else INAPPLICABLE)

class CapturedTypeFromSubtyping(
    val typeVariable: NewTypeVariable,
    val constraintType: UnwrappedType,
    val position: ConstraintPosition
) : ConstraintSystemCallDiagnostic(INAPPLICABLE)

class NotEnoughInformationForTypeParameter(val typeVariable: NewTypeVariable) : ConstraintSystemCallDiagnostic(INAPPLICABLE)

class ContradictoryTypeVariableError(
    val constraintPosition: ConstraintPosition,
    val typeVariable: NewTypeVariable,
    val specialTypeVariableKind: SpecialTypeVariableKind?,
    val sortedConstraints: SortedConstraints
) : ConstraintSystemCallDiagnostic(INAPPLICABLE)
