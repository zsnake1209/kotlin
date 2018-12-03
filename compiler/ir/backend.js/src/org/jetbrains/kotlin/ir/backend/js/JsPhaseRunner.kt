/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js

import org.jetbrains.kotlin.backend.common.PhaseRunnerDefault
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

object JsPhaseRunner : PhaseRunnerDefault<JsIrBackendContext, IrModuleFragment>() {
    override val startPhaseMarker = IrModuleStartPhase
    override val endPhaseMarker = IrModuleEndPhase

    override fun phases(context: JsIrBackendContext) = context.phases
    override fun elementName(input: IrModuleFragment) = input.name.asString()
    override fun configuration(context: JsIrBackendContext) = context.configuration
}