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

package com.android.launcher3.taskbar

import android.content.Context
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statehandlers.DesktopVisibilityController.TaskbarDesktopModeListener
import com.android.launcher3.taskbar.TaskbarBackgroundRenderer.Companion.MAX_ROUNDNESS

/** Handles Taskbar in Desktop Windowing mode. */
class TaskbarDesktopModeController(
    private val context: Context,
    private val desktopVisibilityController: DesktopVisibilityController,
) : TaskbarDesktopModeListener {
    private lateinit var taskbarControllers: TaskbarControllers
    private lateinit var taskbarSharedState: TaskbarSharedState

    fun init(controllers: TaskbarControllers, sharedState: TaskbarSharedState) {
        taskbarControllers = controllers
        taskbarSharedState = sharedState
        desktopVisibilityController.registerTaskbarDesktopModeListener(this)
    }

    fun isInDesktopMode(displayId: Int) = desktopVisibilityController.isInDesktopMode(displayId)

    fun isInDesktopModeAndNotInOverview(displayId: Int) =
        desktopVisibilityController.isInDesktopModeAndNotInOverview(displayId)

    override fun onTaskbarCornerRoundingUpdate(doesAnyTaskRequireTaskbarRounding: Boolean) {
        if (taskbarControllers.taskbarActivityContext.isDestroyed) return
        taskbarSharedState.showCornerRadiusInDesktopMode = doesAnyTaskRequireTaskbarRounding
        val cornerRadius = getTaskbarCornerRoundness(doesAnyTaskRequireTaskbarRounding)
        taskbarControllers.taskbarCornerRoundness.animateToValue(cornerRadius).start()
    }

    fun shouldShowDesktopTasksInTaskbar(): Boolean {
        val activityContext = taskbarControllers.taskbarActivityContext
        return isInDesktopMode(context.displayId) ||
            activityContext.showDesktopTaskbarForFreeformDisplay() ||
            (activityContext.showLockedTaskbarOnHome() &&
                taskbarControllers.taskbarStashController.isOnHome)
    }

    fun getTaskbarCornerRoundness(doesAnyTaskRequireTaskbarRounding: Boolean): Float {
        return if (doesAnyTaskRequireTaskbarRounding) {
            MAX_ROUNDNESS
        } else {
            0f
        }
    }

    fun onDestroy() = desktopVisibilityController.unregisterTaskbarDesktopModeListener(this)
}
