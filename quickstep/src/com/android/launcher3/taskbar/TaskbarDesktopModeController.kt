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

import com.android.launcher3.Flags.refactorTaskbarUiState
import com.android.launcher3.statehandlers.DesktopVisibilityController
import com.android.launcher3.statehandlers.DesktopVisibilityController.TaskbarDesktopModeListener
import com.android.launcher3.taskbar.TaskbarBackgroundRenderer.Companion.MAX_ROUNDNESS
import com.android.launcher3.taskbar.TaskbarManagerImpl.TASKBAR_UI_THREAD
import com.android.launcher3.util.DisplayController

/** Handles Taskbar in Desktop Windowing mode. */
class TaskbarDesktopModeController(
    private val taskbarActivityContext: TaskbarActivityContext,
    private val desktopVisibilityController: DesktopVisibilityController,
) : TaskbarDesktopModeListener {

    private val displayInfoChangeListener =
        DisplayController.DisplayInfoChangeListener { _, _, _ ->
            // DisplayInfoChangeListener is called on main thread, we should switch to taskbar's UI
            // thread to update UI state.
            TASKBAR_UI_THREAD.execute { updateTaskbarUiState() }
        }

    private lateinit var taskbarControllers: TaskbarControllers
    private lateinit var taskbarSharedState: TaskbarSharedState
    private lateinit var taskbarUiState: TaskbarUiState

    val isLauncherAnimationRunning: Boolean
        get() = desktopVisibilityController.launcherAnimationRunning

    fun init(
        controllers: TaskbarControllers,
        sharedState: TaskbarSharedState,
        uiState: TaskbarUiState,
    ) {
        taskbarControllers = controllers
        taskbarSharedState = sharedState
        taskbarUiState = uiState
        desktopVisibilityController.registerTaskbarDesktopModeListener(this)
        if (refactorTaskbarUiState()) {
            DisplayController.INSTANCE.get(taskbarActivityContext)
                .addChangeListener(displayInfoChangeListener)
            updateTaskbarUiState()
        }
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
        return shouldShowDesktopTasksInTaskbar(taskbarActivityContext.displayId)
    }

    fun shouldShowDesktopTasksInTaskbar(displayId: Int): Boolean {
        return isInDesktopMode(displayId) ||
            taskbarActivityContext.showDesktopTaskbarForFreeformDisplay() ||
            (taskbarActivityContext.showLockedTaskbarOnHome() &&
                taskbarControllers.taskbarStashController.isOnHome)
    }

    fun getTaskbarCornerRoundness(doesAnyTaskRequireTaskbarRounding: Boolean): Float {
        return if (doesAnyTaskRequireTaskbarRounding) {
            MAX_ROUNDNESS
        } else {
            0f
        }
    }

    val getActiveDeskId: Int
        get() =
            desktopVisibilityController.getActiveDeskId(taskbarActivityContext.displayId)

    fun onDestroy() {
        desktopVisibilityController.unregisterTaskbarDesktopModeListener(this)
        if (refactorTaskbarUiState()) {
            DisplayController.INSTANCE.get(taskbarActivityContext)
                .removeChangeListener(displayInfoChangeListener)
        }
    }

    private fun updateTaskbarUiState() {
        taskbarUiState.setShowDesktopTaskbarForFreeformDisplay(
            DisplayController.showDesktopTaskbarForFreeformDisplay(taskbarActivityContext)
        )
        taskbarUiState.setShowLockedTaskbarOnHome(
            DisplayController.showLockedTaskbarOnHome(taskbarActivityContext)
        )
    }
}
