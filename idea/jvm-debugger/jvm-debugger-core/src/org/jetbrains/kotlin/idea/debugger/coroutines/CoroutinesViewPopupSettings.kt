/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.debugger.coroutines

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "CoroutinesViewPopupSettings", storages = [Storage("coroutinesViewPopupSettings.xml")])
class CoroutinesViewPopupSettings : PersistentStateComponent<CoroutinesViewPopupSettings> {

    @JvmField
    var showCoroutineCreationStackTrace: Boolean = false

    @JvmField
    var showIntrinsicFrames: Boolean = false

    override fun getState(): CoroutinesViewPopupSettings? = this

    override fun loadState(state: CoroutinesViewPopupSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {

        fun getInstance(): CoroutinesViewPopupSettings {
            return ServiceManager.getService(CoroutinesViewPopupSettings::class.java)
        }
    }
}