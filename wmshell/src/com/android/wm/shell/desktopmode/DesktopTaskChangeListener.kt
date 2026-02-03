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
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.freeform.TaskChangeListener
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellController

/** Manages tasks handling specific to Android Desktop Mode. */
class DesktopTaskChangeListener(
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopState: DesktopState,
    private val shellController: ShellController,
) : TaskChangeListener {

    override fun onTaskOpening(taskInfo: RunningTaskInfo) {
        val desktopRepository: DesktopRepository =
            desktopUserRepositories.getProfile(taskInfo.userId)
        val isFreeformTask = taskInfo.isFreeform
        val isActiveTask = desktopRepository.isActiveTask(taskInfo.taskId)
        logD(
            "onTaskOpening for taskId=%d, displayId=%d userId=%s currentUserId=%d " +
                "parentTaskId=%b isFreeform=%b isActive=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            taskInfo.userId,
            shellController.currentUserId,
            taskInfo.parentTaskId,
            isFreeformTask,
            isActiveTask,
        )
        if (!isFreeformTask && isActiveTask) {
            desktopRepository.removeTask(taskInfo.taskId)
            return
        }
        if (
            !desktopState.isDesktopModeSupportedOnDisplay(taskInfo.displayId) &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            logD(
                "onTaskOpening for taskId=%d, displayId=%d - desktop not supported",
                taskInfo.taskId,
                taskInfo.displayId,
            )
            return
        }
        if (isFreeformTask && !isActiveTask) {
            // TODO: b/420917959 - Remove this once LaunchParams respects activity options set for
            // [DesktopWallpaperActivity] launch which should always be in fullscreen.
            if (DesktopWallpaperActivity.isWallpaperTask(taskInfo)) {
                logE(
                    "Trying to add freeform DesktopWallpaperActivity to DesktopRepository, " +
                        "returning early instead"
                )
                return
            }
            desktopRepository.addTask(
                taskInfo.displayId,
                taskInfo.taskId,
                taskInfo.isVisible,
                taskInfo.configuration.windowConfiguration.bounds,
            )
        }
    }

    override fun onTaskChanging(taskInfo: RunningTaskInfo) {
        if (
            !desktopState.isDesktopModeSupportedOnDisplay(taskInfo.displayId) &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            logD(
                "onTaskChanging for taskId=%d, displayId=%d - desktop not supported",
                taskInfo.taskId,
                taskInfo.displayId,
            )
            return
        }
        val desktopRepository: DesktopRepository =
            desktopUserRepositories.getProfile(taskInfo.userId)
        val isFreeformTask = taskInfo.isFreeform
        val isActiveTask = desktopRepository.isActiveTask(taskInfo.taskId)
        logD(
            "onTaskChanging for taskId=%d, displayId=%d userId=%s currentUserId=%d " +
                "parentTaskId=%b isFreeform=%b isActive=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            taskInfo.userId,
            shellController.currentUserId,
            taskInfo.parentTaskId,
            isFreeformTask,
            isActiveTask,
        )
        // TODO: b/394281403 - with multiple desks, it's possible to have a non-freeform task
        //  inside a desk, so this should be decoupled from windowing mode.
        //  Also, changes in/out of desks are handled by the [DesksTransitionObserver], which has
        //  more specific information about the desk involved in the transition, which might be
        //  more accurate than assuming it's always the default/active desk in the display, as this
        //  method does.
        // Case 1: When the task change is from a task in the desktop repository which is now
        // fullscreen,
        // remove the task from the desktop repository since it is no longer a freeform task.
        if (!isFreeformTask && isActiveTask) {
            desktopRepository.removeTask(taskInfo.taskId)
        } else if (isFreeformTask) {
            // TODO: b/420917959 - Remove this once LaunchParams respects activity options set for
            // [DesktopWallpaperActivity] launch which should always be in fullscreen.
            if (DesktopWallpaperActivity.isWallpaperTask(taskInfo)) {
                logE(
                    "Trying to add freeform DesktopWallpaperActivity to DesktopRepository, " +
                        "returning early instead"
                )
                return
            }
            // If the task is already active in the repository, then moves task to the front,
            // else adds the task.
            desktopRepository.addTask(
                taskInfo.displayId,
                taskInfo.taskId,
                taskInfo.isVisible,
                taskInfo.configuration.windowConfiguration.bounds,
            )
        }
    }

    // This method should only be used for scenarios where the task info changes are not propagated
    // to
    // [DesktopTaskChangeListener#onTaskChanging] via [TransitionsObserver].
    // Any changes to [DesktopRepository] from this method should be made carefully to minimize risk
    // of race conditions and possible duplications with [onTaskChanging].
    override fun onNonTransitionTaskChanging(taskInfo: RunningTaskInfo) {
        // TODO: b/367268953 - Propagate usages from FreeformTaskListener to this method.
        logD(
            "onNonTransitionTaskChanging for taskId=%d, displayId=%d",
            taskInfo.taskId,
            taskInfo.displayId,
        )
    }

    override fun onTaskMovingToFront(taskInfo: RunningTaskInfo) {
        if (
            !desktopState.isDesktopModeSupportedOnDisplay(taskInfo.displayId) &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            logD(
                "onTaskMovingToFront for taskId=%d, displayId=%d - desktop not supported",
                taskInfo.taskId,
                taskInfo.displayId,
            )
            return
        }
        val desktopRepository: DesktopRepository =
            desktopUserRepositories.getProfile(taskInfo.userId)
        val isFreeformTask = taskInfo.isFreeform
        val isActiveTask = desktopRepository.isActiveTask(taskInfo.taskId)
        logD(
            "onTaskMovingToFront for taskId=%d, displayId=%d userId=%s currentUserId=%d " +
                "parentTaskId=%b isFreeform=%b isActive=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            taskInfo.userId,
            shellController.currentUserId,
            taskInfo.parentTaskId,
            isFreeformTask,
            isActiveTask,
        )
        // When the task change is from a task in the desktop repository which is now fullscreen,
        // remove the task from the desktop repository since it is no longer a freeform task.
        if (!isFreeformTask && isActiveTask) {
            desktopRepository.removeTask(taskInfo.taskId)
        }
        if (isFreeformTask) {
            // TODO: b/420917959 - Remove this once LaunchParams respects activity options set for
            // [DesktopWallpaperActivity] launch which should always be in fullscreen.
            if (DesktopWallpaperActivity.isWallpaperTask(taskInfo)) {
                logE(
                    "Trying to add freeform DesktopWallpaperActivity to DesktopRepository, returning early instead"
                )
                return
            }
            // If the task is already active in the repository, then it only moves the task to the
            // front.
            desktopRepository.addTask(
                taskInfo.displayId,
                taskInfo.taskId,
                taskInfo.isVisible,
                taskInfo.configuration.windowConfiguration.bounds,
            )
        }
    }

    override fun onTaskMovingToBack(taskInfo: RunningTaskInfo) {
        if (
            !desktopState.isDesktopModeSupportedOnDisplay(taskInfo.displayId) &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            logD(
                "onTaskMovingToBack for taskId=%d, displayId=%d - desktop not supported",
                taskInfo.taskId,
                taskInfo.displayId,
            )
            return
        }
        val desktopRepository: DesktopRepository =
            desktopUserRepositories.getProfile(taskInfo.userId)
        val isFreeformTask = taskInfo.isFreeform
        val isActiveTask = desktopRepository.isActiveTask(taskInfo.taskId)
        logD(
            "onTaskMovingToBack for taskId=%d, displayId=%d userId=%s currentUserId=%d " +
                "parentTaskId=%b isFreeform=%b isActive=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            taskInfo.userId,
            shellController.currentUserId,
            taskInfo.parentTaskId,
            isFreeformTask,
            isActiveTask,
        )
        if (!isActiveTask) return
        desktopRepository.updateTask(
            taskInfo.displayId,
            taskInfo.taskId,
            isVisible = false,
            taskInfo.configuration.windowConfiguration.bounds,
        )
    }

    override fun onTaskClosing(taskInfo: RunningTaskInfo) {
        if (
            !desktopState.isDesktopModeSupportedOnDisplay(taskInfo.displayId) &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            logD(
                "onTaskClosing for taskId=%d, displayId=%d - desktop not supported",
                taskInfo.taskId,
                taskInfo.displayId,
            )
            return
        }
        val desktopRepository: DesktopRepository =
            desktopUserRepositories.getProfile(taskInfo.userId)
        val isFreeformTask = taskInfo.isFreeform
        val isActiveTask = desktopRepository.isActiveTask(taskInfo.taskId)
        logD(
            "onTaskClosing for taskId=%d, displayId=%d userId=%s currentUserId=%d " +
                "parentTaskId=%b isFreeform=%b isActive=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            taskInfo.userId,
            shellController.currentUserId,
            taskInfo.parentTaskId,
            isFreeformTask,
            isActiveTask,
        )
        if (!isActiveTask) return

        val isMinimized = desktopRepository.isMinimizedTask(taskInfo.taskId)
        // TODO: b/370038902 - Handle Activity#finishAndRemoveTask.
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue) {
            // A task that is closing might have been minimized previously by
            // [DesktopBackNavTransitionObserver]. If that's the case then do not remove it from
            // the repo.
            desktopRepository.removeClosingTask(taskInfo.taskId)
            if (isMinimized) {
                desktopRepository.updateTask(
                    taskInfo.displayId,
                    taskInfo.taskId,
                    isVisible = false,
                    taskInfo.configuration.windowConfiguration.bounds,
                )
            } else {
                desktopRepository.removeTask(taskInfo.taskId)
            }
        } else {
            desktopRepository.removeClosingTask(taskInfo.taskId)
            desktopRepository.removeTask(taskInfo.taskId)
        }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopTaskChangeListener"
    }
}
