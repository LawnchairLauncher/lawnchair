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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.view.Display.INVALID_DISPLAY
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions

/**
 * A [Transitions.TransitionObserver] that observes shell transitions to manage display focus in
 * desktop mode.
 *
 * When the last desktop task on a display is closed, this class prevents focus from returning to a
 * non-task surface (like the home screen) on that display. Instead, it attempts to move focus to a
 * desktop task on another display, if one exists.
 */
class DisplayFocusResolver(
    private val transitions: Transitions,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopTasksController: DesktopTasksController,
) {
    private var userId: Int = -1

    /**
     * Observes shell transitions to manage display focus.
     *
     * This method is called when a shell transition is ready. It inspects the transition to
     * determine if a desktop task is closing. If the closing task is the last one on its display,
     * this method may trigger a focus change to a task on another display to prevent the home
     * screen from taking focus.
     *
     * @param info The [TransitionInfo] for the transition, containing details about the windows
     *   involved.
     */
    fun onTransitionReady(info: TransitionInfo) {
        handleFocusOnTaskCloseIfNeeded(info)
    }

    private fun isDesktopTaskClosingTransitionOnDisplay(
        info: TransitionInfo,
        displayId: Int,
    ): Boolean {
        for (change in info.changes) {
            if (
                change.taskInfo != null &&
                    change.taskInfo?.taskId != INVALID_TASK_ID &&
                    change.taskInfo?.displayId == displayId &&
                    TransitionUtil.isClosingMode(change.mode)
            ) {
                userId = change.taskInfo!!.userId
                return true
            }
        }
        return false
    }

    /**
     * Finds the task that is being brought to the top on a given display during a transition. This
     * is identified by the [FLAG_MOVED_TO_TOP] flag.
     *
     * @return the [RunningTaskInfo] of the task being focused, or `null` if none is found.
     */
    private fun findNextFocusedTaskInDisplay(
        info: TransitionInfo,
        displayId: Int,
    ): RunningTaskInfo? {
        val change =
            info.changes.firstOrNull {
                it.endDisplayId == displayId && (it.flags and FLAG_MOVED_TO_TOP) != 0
            }
        return change?.taskInfo
    }

    /**
     * Finds the task ID of the top-most expanded desktop task on any display other than the one
     * that is currently focused.
     *
     * @param currentFocusedDisplayId The display that should be excluded from the search.
     * @return The task ID of the next task to focus, or [INVALID_TASK_ID] if none is found.
     */
    private fun findNextTopDesktopWindowGlobally(currentFocusedDisplayId: Int): Int {
        val desktopRepository = desktopUserRepositories.getProfile(userId)
        for (deskId in desktopRepository.getAllDeskIds()) {
            if (!desktopRepository.isDeskActive(deskId)) continue
            val expandedTasksInDesk = desktopRepository.getExpandedTasksIdsInDeskOrdered(deskId)
            if (!expandedTasksInDesk.isEmpty()) {
                if (desktopRepository.getDisplayForDesk(deskId) == currentFocusedDisplayId) {
                    // This should not happen. If the closing task's display is now empty of
                    // desktop tasks, the repository should reflect that. This is a safeguard.
                    ProtoLog.e(
                        WM_SHELL_DESKTOP_MODE,
                        "Unexpected task found in $currentFocusedDisplayId. This display is " +
                            "empty according to TransitionInfo but not empty in" +
                            "DesktopRepository",
                    )
                    continue
                }
                return expandedTasksInDesk.first()
            }
        }
        return INVALID_TASK_ID
    }

    /**
     * When a desktop task is closed, checks if focus should be moved to a task on another display.
     * This is done if the closing task was the last standard task on its display, preventing focus
     * from falling back to the home screen.
     */
    private fun handleFocusOnTaskCloseIfNeeded(info: TransitionInfo) {
        // TODO: b/436407117 - Re-evaluate with pressing home button scenario.
        val currentFocusDisplayId = focusTransitionObserver.globallyFocusedDisplayId
        if (
            currentFocusDisplayId == INVALID_DISPLAY ||
                !isDesktopTaskClosingTransitionOnDisplay(info, currentFocusDisplayId)
        ) {
            // This logic only applies when a desktop task is closing on the currently focused
            // display.
            return
        }

        // TODO: b/435099775 - Support DesktopWallpaperActivity
        val nextFocusedTaskInfo = findNextFocusedTaskInDisplay(info, currentFocusDisplayId)
        if (
            nextFocusedTaskInfo == null ||
                nextFocusedTaskInfo.topActivityType == ACTIVITY_TYPE_STANDARD
        ) {
            // If another standard task is becoming focused on the same display, let it proceed.
            // We only want to intervene if focus is falling back to a non-standard task,
            // like the home screen.
            return
        }

        // The focused display is now empty of standard tasks. Find a desktop task on another
        // display to focus instead.
        val newFocusedTaskId = findNextTopDesktopWindowGlobally(currentFocusDisplayId)
        if (newFocusedTaskId != INVALID_TASK_ID) {
            ProtoLog.e(
                WM_SHELL_DESKTOP_MODE,
                "Display $currentFocusDisplayId became empty. Moving focus to other display",
            )
            transitions.runOnIdle {
                desktopTasksController.moveTaskToFront(
                    taskId = newFocusedTaskId,
                    userId = userId,
                    remoteTransition = null,
                    unminimizeReason = UnminimizeReason.UNKNOWN,
                )
            }
        }
    }
}
