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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ActivityInfo.ScreenOrientation
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.graphics.Rect
import android.window.WindowContainerTransaction
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.TaskStackListenerCallback
import com.android.wm.shell.common.TaskStackListenerImpl
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit

/** Handles task resizing to respect orientation change of non-resizeable activities in desktop. */
class DesktopActivityOrientationChangeHandler(
    context: Context,
    shellInit: ShellInit,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val taskStackListener: TaskStackListenerImpl,
    private val resizeHandler: ToggleResizeDesktopTaskTransitionHandler,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val displayController: DisplayController,
    private val desktopState: DesktopState,
) {

    init {
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback({ onInit() }, this)
        }
    }

    private fun onInit() {
        taskStackListener.addListener(
            object : TaskStackListenerCallback {
                override fun onActivityRequestedOrientationChanged(
                    taskId: Int,
                    @ScreenOrientation requestedOrientation: Int,
                ) {
                    // Handle requested screen orientation changes at runtime.
                    handleActivityOrientationChange(taskId, requestedOrientation)
                }
            }
        )
    }

    /**
     * Triggered with onTaskInfoChanged to handle:
     * * New activity launching from same task with different orientation
     * * Top activity closing in same task with different orientation to previous activity
     */
    fun handleActivityOrientationChange(oldTask: RunningTaskInfo, newTask: RunningTaskInfo) {
        val newTopActivityInfo = newTask.topActivityInfo ?: return
        val oldTopActivityInfo = oldTask.topActivityInfo ?: return
        // Check if screen orientation is different from old task info so there is no duplicated
        // calls to handle runtime requested orientation changes.
        if (oldTopActivityInfo.screenOrientation != newTopActivityInfo.screenOrientation) {
            handleActivityOrientationChange(newTask.taskId, newTopActivityInfo.screenOrientation)
        }
    }

    private fun handleActivityOrientationChange(
        taskId: Int,
        @ScreenOrientation requestedOrientation: Int,
    ) {
        if (!Flags.respectOrientationChangeForUnresizeable()) return
        val task = shellTaskOrganizer.getRunningTaskInfo(taskId) ?: return
        val taskRepository = desktopUserRepositories.current
        val isDesktopModeShowing = taskRepository.isAnyDeskActive(task.displayId)
        if (!isDesktopModeShowing || !task.isFreeform || task.isResizeable) return

        val taskBounds = task.configuration.windowConfiguration.bounds
        val taskHeight = taskBounds.height()
        val taskWidth = taskBounds.width()
        if (taskWidth == taskHeight) return
        val orientation =
            if (taskWidth > taskHeight) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT

        // Non-resizeable activity requested opposite orientation.
        if (
            orientation == ORIENTATION_PORTRAIT &&
                ActivityInfo.isFixedOrientationLandscape(requestedOrientation) ||
                orientation == ORIENTATION_LANDSCAPE &&
                    ActivityInfo.isFixedOrientationPortrait(requestedOrientation)
        ) {
            val displayLayout = displayController.getDisplayLayout(task.displayId) ?: return
            val captionInsets =
                task.configuration.windowConfiguration.appBounds?.let {
                    it.top - task.configuration.windowConfiguration.bounds.top
                } ?: 0
            val newOrientationBounds =
                calculateInitialBounds(
                    displayLayout = displayLayout,
                    taskInfo = task,
                    captionInsets = captionInsets,
                    requestedScreenOrientation = requestedOrientation,
                )

            // Use the center x as the resizing anchor point.
            val left = taskBounds.centerX() - newOrientationBounds.width() / 2
            val right = left + newOrientationBounds.width()
            val finalBounds =
                Rect(left, taskBounds.top, right, taskBounds.top + newOrientationBounds.height())

            val wct = WindowContainerTransaction().setBounds(task.token, finalBounds)
            resizeHandler.startTransition(wct)
        }
    }
}
