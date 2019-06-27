/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual.util

import org.jetbrains.kotlin.contracts.model.ESValue
import org.jetbrains.kotlin.contracts.model.structure.ESReceiver
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValue

fun ESValue.extractReceiverValue(): ReceiverValue? = if (this is ESReceiver) receiverValue else null