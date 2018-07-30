/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.migration

import com.intellij.application.options.schemes.SchemesCombo
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import org.jetbrains.kotlin.idea.quickfix.migration.MigrationFix

class CodeMigrationAction : CodeInspectionAction("Code Migration", "Code migration") {
    override fun getHelpTopic(): String {
        return "reference.dialogs.cleanup.scope"
    }

    override fun createConfigurable(
        projectProfileManager: ProjectInspectionProfileManager,
        profilesCombo: SchemesCombo<InspectionProfileImpl>
    ): CodeInspectionAction.ExternalProfilesComboboxAwareInspectionToolsConfigurable {
        return object :
            CodeInspectionAction.ExternalProfilesComboboxAwareInspectionToolsConfigurable(projectProfileManager, profilesCombo) {
            override fun acceptTool(entry: InspectionToolWrapper<*, *>): Boolean {
                return super.acceptTool(entry) && entry.isMigrationTool()
            }

            override fun getDisplayName(): String {
                return CODE_MIGRATION_INSPECTIONS_DISPLAY_NAME
            }

            private fun InspectionToolWrapper<*, *>.isMigrationTool(): Boolean {
                return tool is MigrationFix
            }
        }
    }

    companion object {
        const val CODE_MIGRATION_INSPECTIONS_DISPLAY_NAME = "Code Migration Inspections"
        const val ACTION_ID = "KotlinCodeMigration"
    }
}
