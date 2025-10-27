/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.taskbar.customization

import com.android.launcher3.Flags.enableRecentsInTaskbar
import com.android.launcher3.config.FeatureFlags.enableTaskbarPinning
import com.android.launcher3.taskbar.TaskbarActivityContext

/** Evaluates all the features taskbar can have. */
class TaskbarFeatureEvaluator
private constructor(private val taskbarActivityContext: TaskbarActivityContext) {
    val hasAllApps = true
    val hasAppIcons = true
    val hasBubbles = false
    val hasNavButtons = taskbarActivityContext.isThreeButtonNav

    val isRecentsEnabled: Boolean
        get() = enableRecentsInTaskbar()

    val hasDivider: Boolean
        get() = enableTaskbarPinning() || isRecentsEnabled

    val isTransient: Boolean
        get() = taskbarActivityContext.isTransientTaskbar

    val isLandscape: Boolean
        get() = taskbarActivityContext.deviceProfile.isLandscape

    val supportsPinningPopup: Boolean
        get() = !hasNavButtons

    fun onDestroy() {
        taskbarFeatureEvaluator = null
    }

    companion object {
        @Volatile private var taskbarFeatureEvaluator: TaskbarFeatureEvaluator? = null

        @JvmStatic
        fun getInstance(taskbarActivityContext: TaskbarActivityContext): TaskbarFeatureEvaluator {
            synchronized(this) {
                if (taskbarFeatureEvaluator == null) {
                    taskbarFeatureEvaluator = TaskbarFeatureEvaluator(taskbarActivityContext)
                }
                return taskbarFeatureEvaluator!!
            }
        }
    }
}
