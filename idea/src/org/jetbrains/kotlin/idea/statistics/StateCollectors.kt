/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.statistics

import com.intellij.internal.statistic.service.fus.collectors.ProjectUsagesCollector
import com.intellij.internal.statistic.beans.UsageDescriptor;
import org.jetbrains.kotlin.idea.formatter.KotlinFormatterUtils
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.internal.statistic.utils.getEnumUsage
import com.intellij.codeInsight.hints.InlayParameterHintsExtension
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.formatter.kotlinCommonSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext
import org.jetbrains.kotlin.idea.statistics.StatisticsUtils.Companion.PLUGIN_VERSION

abstract class KotlinIdeStateCollector(private val ideFeature: String) : ProjectUsagesCollector() {
    override fun getGroupId() = "statistics.kotlin.ide.$ideFeature"
    override fun getContext(project: Project): FUSUsageContext? {
        return FUSUsageContext.create(PLUGIN_VERSION)
    }
}

// feel free to add state collectors below:

open class KotlinFormatterUsageCollector : KotlinIdeStateCollector("formatter") {
    override fun getUsages(project: Project): Set<UsageDescriptor> {
        val usedFormatter = KotlinFormatterUtils.getKotlinFormatterKind(project)

        val settings = CodeStyleSettingsManager.getSettings(project)
        val kotlinCommonSettings = settings.kotlinCommonSettings
        val kotlinCustomSettings = settings.kotlinCustomSettings

        return setOf(
                getEnumUsage("kotlin.formatter.kind", usedFormatter),
                getEnumStringPropertyUsage(
                        "kotlin.formatter.defaults",
                        kotlinCustomSettings.CODE_STYLE_DEFAULTS ?: kotlinCommonSettings.CODE_STYLE_DEFAULTS
                )
        )
    }

    private fun getEnumStringPropertyUsage(key: String, value: String?): UsageDescriptor {
        return UsageDescriptor(key + "." + value.toString().toLowerCase(java.util.Locale.ENGLISH), 1)
    }
}

open class KotlinInlayParameterHintsUsageCollector : KotlinIdeStateCollector("hints.inlay") {
    override fun getUsages(project: Project): Set<UsageDescriptor> {
        val provider = InlayParameterHintsExtension.forLanguage(KotlinLanguage.INSTANCE) ?: return emptySet()

        return provider.supportedOptions.mapTo(LinkedHashSet()) {
            com.intellij.internal.statistic.utils.getBooleanUsage(it.id, it.get())
        }
    }
}

