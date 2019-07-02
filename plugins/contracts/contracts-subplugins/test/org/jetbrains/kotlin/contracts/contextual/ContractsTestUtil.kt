/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.contracts.contextual

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.contracts.contextual.dslmarker.DslMarkerContractExtension
import org.jetbrains.kotlin.contracts.contextual.exceptions.ExceptionContractExtension
import org.jetbrains.kotlin.contracts.contextual.extensions.SpecificContractExtension
import org.jetbrains.kotlin.contracts.contextual.safebuilders.CallContractExtension
import org.jetbrains.kotlin.contracts.contextual.serialization.ExtensionContractSerializer
import org.jetbrains.kotlin.contracts.contextual.transactions.TransactionContractExtension
import org.jetbrains.kotlin.extensions.ContractsExtension
import org.jetbrains.kotlin.serialization.ContractSerializerExtension
import java.io.File

fun registerExtensions(project: Project) {
    ContractsExtension.registerExtension(project, ContractsImplementationExtension())
    ContractSerializerExtension.registerExtension(project, ExtensionContractSerializer())

    SpecificContractExtension.registerExtensionPoint(project)
    SpecificContractExtension.registerExtension(project, ExceptionContractExtension())
    SpecificContractExtension.registerExtension(project, CallContractExtension())
    SpecificContractExtension.registerExtension(project, TransactionContractExtension())
    SpecificContractExtension.registerExtension(project, DslMarkerContractExtension())
}

val contractsDslClasspath = listOf(
    File("plugins/contracts/contracts-plugin/dsl/build/libs/kotlin-contracts-dsl-1.3-SNAPSHOT.jar"),
    File("plugins/contracts/contracts-subplugins/dsl/build/libs/kotlin-contracts-subplugins-dsl-1.3-SNAPSHOT.jar")
)