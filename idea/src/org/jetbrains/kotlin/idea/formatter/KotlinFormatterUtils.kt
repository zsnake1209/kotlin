/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.formatter

/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.util.isDefaultOfficialCodeStyle

class KotlinFormatterUtils {
    companion object {
        private val KOTLIN_DEFAULT_COMMON = KotlinLanguageCodeStyleSettingsProvider().defaultCommonSettings
                .also { KotlinStyleGuideCodeStyle.applyToCommonSettings(it) }

        private val KOTLIN_DEFAULT_CUSTOM = KotlinCodeStyleSettings.DEFAULT.cloneSettings()
                .also { KotlinStyleGuideCodeStyle.applyToKotlinCustomSettings(it) }

        private val KOTLIN_OBSOLETE_DEFAULT_COMMON = KotlinLanguageCodeStyleSettingsProvider().defaultCommonSettings
                .also { KotlinObsoleteCodeStyle.applyToCommonSettings(it) }

        private val KOTLIN_OBSOLETE_DEFAULT_CUSTOM = KotlinCodeStyleSettings.DEFAULT.cloneSettings()
                .also { KotlinObsoleteCodeStyle.applyToKotlinCustomSettings(it) }

        fun getKotlinFormatterKind(project: Project): KotlinFormatterKind {
            val isProject = CodeStyleSettingsManager.getInstance(project).USE_PER_PROJECT_SETTINGS
            val isDefaultOfficialCodeStyle = isDefaultOfficialCodeStyle

            val settings = CodeStyleSettingsManager.getSettings(project)
            val kotlinCommonSettings = settings.kotlinCommonSettings
            val kotlinCustomSettings = settings.kotlinCustomSettings

            val isDefaultKotlinCommonSettings = kotlinCommonSettings == KotlinLanguageCodeStyleSettingsProvider().defaultCommonSettings
            val isDefaultKotlinCustomSettings = kotlinCustomSettings == KotlinCodeStyleSettings.DEFAULT

            if (isDefaultKotlinCommonSettings && isDefaultKotlinCustomSettings) {
                return if (isDefaultOfficialCodeStyle) {
                    paired(KotlinFormatterKind.IDEA_OFFICIAL_DEFAULT, isProject)
                } else {
                    paired(KotlinFormatterKind.IDEA_DEFAULT, isProject)
                }
            }

            if (kotlinCommonSettings == KOTLIN_OBSOLETE_DEFAULT_COMMON && kotlinCustomSettings == KOTLIN_OBSOLETE_DEFAULT_CUSTOM) {
                return paired(KotlinFormatterKind.IDEA_OBSOLETE_KOTLIN, isProject)
            }

            if (kotlinCommonSettings == KOTLIN_DEFAULT_COMMON && kotlinCustomSettings == KOTLIN_DEFAULT_CUSTOM) {
                return paired(KotlinFormatterKind.IDEA_KOTLIN, isProject)
            }

            val isKotlinOfficialLikeSettings = settings == settings.clone().also {
                KotlinStyleGuideCodeStyle.apply(it)
            }
            if (isKotlinOfficialLikeSettings) {
                return paired(KotlinFormatterKind.IDEA_OFFICIAL_KOTLIN_WITH_CUSTOM, isProject)
            }

            val isKotlinObsoleteLikeSettings = settings == settings.clone().also {
                KotlinObsoleteCodeStyle.apply(it)
            }
            if (isKotlinObsoleteLikeSettings) {
                return paired(KotlinFormatterKind.IDEA_KOTLIN_WITH_CUSTOM, isProject)
            }

            return paired(KotlinFormatterKind.IDEA_CUSTOM, isProject)
        }

        private fun paired(kind: KotlinFormatterKind, isProject: Boolean): KotlinFormatterKind {
            if (!isProject) return kind

            return when (kind) {
                KotlinFormatterKind.IDEA_DEFAULT -> KotlinFormatterKind.PROJECT_DEFAULT
                KotlinFormatterKind.IDEA_OFFICIAL_DEFAULT -> KotlinFormatterKind.PROJECT_OFFICIAL_DEFAULT
                KotlinFormatterKind.IDEA_CUSTOM -> KotlinFormatterKind.PROJECT_CUSTOM
                KotlinFormatterKind.IDEA_KOTLIN_WITH_CUSTOM -> KotlinFormatterKind.PROJECT_KOTLIN_WITH_CUSTOM
                KotlinFormatterKind.IDEA_KOTLIN -> KotlinFormatterKind.PROJECT_KOTLIN
                KotlinFormatterKind.IDEA_OBSOLETE_KOTLIN -> KotlinFormatterKind.PROJECT_OBSOLETE_KOTLIN
                KotlinFormatterKind.IDEA_OFFICIAL_KOTLIN_WITH_CUSTOM -> KotlinFormatterKind.PROJECT_OBSOLETE_KOTLIN_WITH_CUSTOM
                else -> kind
            }
        }
    }

    enum class KotlinFormatterKind {
        IDEA_DEFAULT,
        IDEA_CUSTOM,
        IDEA_KOTLIN_WITH_CUSTOM,
        IDEA_KOTLIN,

        PROJECT_DEFAULT,
        PROJECT_CUSTOM,
        PROJECT_KOTLIN_WITH_CUSTOM,
        PROJECT_KOTLIN,

        IDEA_OFFICIAL_DEFAULT,
        IDEA_OBSOLETE_KOTLIN,
        IDEA_OFFICIAL_KOTLIN_WITH_CUSTOM,
        PROJECT_OFFICIAL_DEFAULT,
        PROJECT_OBSOLETE_KOTLIN,
        PROJECT_OBSOLETE_KOTLIN_WITH_CUSTOM
    }
}