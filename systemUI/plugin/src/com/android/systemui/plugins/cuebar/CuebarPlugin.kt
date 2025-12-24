/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.plugins.cuebar

import com.android.systemui.plugins.Plugin
import com.android.systemui.plugins.annotations.DependsOn
import com.android.systemui.plugins.annotations.ProvidesInterface

@ProvidesInterface(action = CuebarPlugin.ACTION, version = CuebarPlugin.VERSION)
@DependsOn(target = ActionModel::class)
@DependsOn(target = IconModel::class)
interface CuebarPlugin : Plugin {
    companion object {
        const val VERSION = 1
        const val ACTION = "com.android.systemui.action.PLUGIN_CUEBAR"
    }

    fun filterActions(actions: List<ActionModel>): List<ActionModel> = actions

    fun addOnNewActionsListener(l: OnNewActionsListener)

    fun interface OnNewActionsListener {
        fun onNewActions(actions: List<ActionModel>)
    }
}
