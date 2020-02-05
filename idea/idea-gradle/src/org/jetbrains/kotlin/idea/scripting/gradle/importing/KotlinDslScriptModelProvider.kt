/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.scripting.gradle.importing

import org.gradle.tooling.BuildController
import org.gradle.tooling.model.Model
import org.gradle.tooling.model.ProjectModel
import org.gradle.tooling.model.gradle.GradleBuild
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider

class KotlinDslScriptModelProvider : ProjectImportModelProvider {
    private val kotlinDslScriptModelClass: Class<*> = KotlinDslScriptsModel::class.java

    override fun populateBuildModels(
        controller: BuildController,
        buildModel: GradleBuild,
        consumer: ProjectImportModelProvider.BuildModelConsumer
    ) {
        // do nothing as "build" level models requested after "project" models
    }

    override fun populateProjectModels(
        controller: BuildController,
        projectModel: Model,
        modelConsumer: ProjectImportModelProvider.ProjectModelConsumer
    ) {
        if (projectModel.isRootProject()) {
            try {
                val scriptsModel = controller.findModel(projectModel, kotlinDslScriptModelClass)
                if (scriptsModel != null) {
                    modelConsumer.consume(scriptsModel, kotlinDslScriptModelClass)
                }
            } catch (e: Throwable) {
                modelConsumer.consume(BrokenKotlinDslScriptsModel(e), kotlinDslScriptModelClass)
            }
        }

    }

    private fun Model.isRootProject(): Boolean {
        return (this as? ProjectModel)?.projectIdentifier?.projectPath == ":"
    }
}
