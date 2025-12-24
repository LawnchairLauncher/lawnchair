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
import android.app.ActivityTaskManager
import android.graphics.Rect
import android.os.RemoteException
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import androidx.annotation.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.server.am.Flags
import com.android.wm.shell.desktopmode.data.DesktopRepository
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
    private val perceptibleTasks: MutableSet<Int> = mutableSetOf()

    override fun onTaskOpening(taskInfo: RunningTaskInfo) {
        val desktopRepository: DesktopRepository =
            desktopUserRepositories.getProfile(taskInfo.userId)
        val isFreeformTask = taskInfo.isFreeform
        val isActiveTask = desktopRepository.isActiveTask(taskInfo.taskId)
        logD(
            "onTaskOpening for taskId=%d, displayId=%d userId=%s currentUserId=%d " +
                "parentTaskId=%d isFreeform=%b isActive=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            taskInfo.userId,
            shellController.currentUserId,
            taskInfo.parentTaskId,
            isFreeformTask,
            isActiveTask,
        )
        if (!isFreeformTask && isActiveTask) {
            removeTask(desktopRepository, taskInfo.taskId, isClosingTask = false)
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
            addTask(
                desktopRepository,
                taskInfo.displayId,
                taskInfo.taskId,
                taskInfo.isVisible,
                taskInfo.configuration.windowConfiguration.bounds,
            )
        }
    }

    override fun onTaskChanging(taskInfo: RunningTaskInfo) {
        val desktopRepository: DesktopRepository =
            desktopUserRepositories.getProfile(taskInfo.userId)
        val isFreeformTask = taskInfo.isFreeform
        val isActiveTask = desktopRepository.isActiveTask(taskInfo.taskId)
        if (
            !desktopState.isDesktopModeSupportedOnDisplay(taskInfo.displayId) &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            logD(
                "onTaskChanging for taskId=%d, displayId=%d - desktop not supported",
                taskInfo.taskId,
                taskInfo.displayId,
            )
            if (
                DesktopExperienceFlags.MOVE_TO_NEXT_DISPLAY_SHORTCUT_WITH_PROJECTED_MODE.isTrue &&
                    !isFreeformTask &&
                    isActiveTask
            ) {
                logD(
                    "Removing previous desktop task moved to non-desktop display",
                    taskInfo.taskId,
                    taskInfo.displayId,
                )
                removeTask(desktopRepository, taskInfo.taskId, isClosingTask = false)
            }
            return
        }
        logD(
            "onTaskChanging for taskId=%d, displayId=%d userId=%s currentUserId=%d " +
                "parentTaskId=%d isFreeform=%b isActive=%b",
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
            removeTask(desktopRepository, taskInfo.taskId, isClosingTask = false)
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
            addTask(
                desktopRepository,
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

    // This method should only be used for scenarios where the task close events are not propagated
    // to [DesktopTaskChangeListener#onTaskClosing] via [TransitionsObserver].
    // Any changes to [DesktopRepository] from this method should be made carefully to minimize risk
    // of race conditions and possible duplications with [onTaskClosing].
    override fun onNonTransitionTaskClosing(taskInfo: RunningTaskInfo) {
        logD(
            "onNonTransitionTaskClosing for taskId=%d, displayId=%d",
            taskInfo.taskId,
            taskInfo.displayId,
        )

        if (DesktopExperienceFlags.ENABLE_DESKTOP_INVISIBLE_TASK_REMOVAL_CLEANUP_BUGFIX.isTrue) {
            // Removing an invisible task is an invisible->invisible change, so no shell transition
            // runs for this, and DesktopRepository misses cleaning up task data, which could lead
            // to DesktopTasksController incorrectly trying to restore the tasks when the desk is
            // reactivated next time. See b/361419732.
            val repository: DesktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
            if (
                repository.getDeskIdForTask(taskInfo.taskId) != null &&
                    !repository.isVisibleTask(taskInfo.taskId)
            ) {
                repository.removeClosingTask(taskInfo.taskId)
                repository.removeTask(taskInfo.taskId)
            }
        }
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
                "parentTaskId=%d isFreeform=%b isActive=%b",
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
            removeTask(desktopRepository, taskInfo.taskId, isClosingTask = false)
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
            addTask(
                desktopRepository,
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
                "parentTaskId=%d isFreeform=%b isActive=%b",
            taskInfo.taskId,
            taskInfo.displayId,
            taskInfo.userId,
            shellController.currentUserId,
            taskInfo.parentTaskId,
            isFreeformTask,
            isActiveTask,
        )
        if (!isActiveTask) return
        updateTask(
            desktopRepository,
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
                "parentTaskId=%d isFreeform=%b isActive=%b",
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
            removeTask(desktopRepository, taskInfo.taskId, isClosingTask = true)
            if (isMinimized) {
                updateTask(
                    desktopRepository,
                    taskInfo.displayId,
                    taskInfo.taskId,
                    isVisible = false,
                    taskInfo.configuration.windowConfiguration.bounds,
                )
            } else {
                removeTask(desktopRepository, taskInfo.taskId, isClosingTask = false)
            }
        } else {
            removeTask(desktopRepository, taskInfo.taskId, isClosingTask = true)
            removeTask(desktopRepository, taskInfo.taskId, isClosingTask = false)
        }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun addTask(
        desktopRepository: DesktopRepository,
        displayId: Int,
        taskId: Int,
        isVisible: Boolean,
        taskBounds: Rect,
    ) {
        desktopRepository.addTask(displayId, taskId, isVisible, taskBounds)

        // Enables the task as a perceptible task (i.e. OOM adj is boosted)
        if (Flags.perceptibleTasks() && !isTaskPerceptible(taskId)) {
            try {
                ActivityTaskManager.getService().setTaskIsPerceptible(taskId, true)
                perceptibleTasks += taskId
            } catch (re: RemoteException) {
                logE("Failed to enable task as perceptible: $re")
            }
        }
    }

    private fun removeTask(
        desktopRepository: DesktopRepository,
        taskId: Int,
        isClosingTask: Boolean,
    ) {
        if (isClosingTask) {
            desktopRepository.removeClosingTask(taskId)
        } else {
            desktopRepository.removeTask(taskId)

            // Only need to unmark non-closing tasks (e.g. going fullscreen)
            // since closing tasks are dead anyways
            ActivityTaskManager.getService().setTaskIsPerceptible(taskId, false)
        }

        perceptibleTasks -= taskId
    }

    private fun updateTask(
        desktopRepository: DesktopRepository,
        displayId: Int,
        taskId: Int,
        isVisible: Boolean,
        taskBounds: Rect?,
    ) {
        desktopRepository.updateTask(displayId, taskId, isVisible, taskBounds)

        // Enables the task as a perceptible task (i.e. OOM adj is boosted)
        if (Flags.perceptibleTasks() && !isTaskPerceptible(taskId)) {
            try {
                ActivityTaskManager.getService().setTaskIsPerceptible(taskId, true)
                perceptibleTasks += taskId
            } catch (re: RemoteException) {
                logE("Failed to enable task as perceptible: $re")
            }
        }
    }

    @VisibleForTesting fun isTaskPerceptible(taskId: Int): Boolean = taskId in perceptibleTasks

    companion object {
        private const val TAG = "DesktopTaskChangeListener"
    }
}
