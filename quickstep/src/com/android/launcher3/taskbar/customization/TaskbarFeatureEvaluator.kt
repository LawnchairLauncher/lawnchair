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

import com.android.launcher3.config.FeatureFlags.enableTaskbarPinning
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.android.launcher3.taskbar.TaskbarControllers
import com.android.launcher3.taskbar.TaskbarRecentAppsController
import com.android.launcher3.util.DisplayController

/** Evaluates all the features taskbar can have. */
class TaskbarFeatureEvaluator(
    private val taskbarActivityContext: TaskbarActivityContext,
    private val taskbarControllers: TaskbarControllers,
) {

    val hasAllApps = true
    val hasAppIcons = true
    val hasBubbles = false
    val hasNavButtons = taskbarActivityContext.isThreeButtonNav

    val hasRecents: Boolean
        get() = taskbarControllers.taskbarRecentAppsController.isEnabled

    val hasDivider: Boolean
        get() = enableTaskbarPinning() || hasRecents

    val isTransient: Boolean
        get() = DisplayController.isTransientTaskbar(taskbarActivityContext)
}
