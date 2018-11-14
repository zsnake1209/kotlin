/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.run

import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.JUnitConfigurationType

class KotlinJUnitRunConfigurationProducer :
    AbstractKotlinJUnitRunConfigurationProducer<JUnitConfiguration>(JUnitConfigurationType.getInstance())