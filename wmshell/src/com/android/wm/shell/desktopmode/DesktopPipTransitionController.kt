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
import android.app.ActivityTaskManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Rect
import android.os.IBinder
import android.window.DesktopExperienceFlags
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.pip.PipDesktopState
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.data.DesktopRepository
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/** Controller to perform extra handling to PiP transitions while in Desktop mode. */
class DesktopPipTransitionController(
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desktopTasksController: DesktopTasksController,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val pipDesktopState: PipDesktopState,
) {
    /**
     * This is called by [PipScheduler#getExitPipViaExpandTransaction] before starting a PiP
     * transition. In the case of multi-activity PiP, we might need to update the parent task's
     * windowing mode and bounds based on whether we are in Desktop Windowing.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param parentTaskId id taken from TaskInfo#lastParentTaskIdBeforePip
     */
    fun maybeUpdateParentInWct(wct: WindowContainerTransaction, parentTaskId: Int) {
        if (!pipDesktopState.isDesktopWindowingPipEnabled()) {
            return
        }

        if (parentTaskId == ActivityTaskManager.INVALID_TASK_ID) {
            logD("maybeUpdateParentInWct: Task is not multi-activity PiP")
            return
        }

        val parentTask = shellTaskOrganizer.getRunningTaskInfo(parentTaskId)
        if (parentTask == null) {
            logW(
                "maybeUpdateParentInWct: Failed to find RunningTaskInfo for parentTaskId %d",
                parentTaskId,
            )
            return
        }

        val defaultFreeformBounds =
            parentTask.lastNonFullscreenBounds?.takeUnless { it.isEmpty }
                ?: calculateDefaultDesktopTaskBounds(pipDesktopState.getCurrentDisplayLayout())

        val resolvedWinMode =
            if (pipDesktopState.isPipInDesktopMode()) WINDOWING_MODE_FREEFORM
            else WINDOWING_MODE_FULLSCREEN

        logD(
            "maybeUpdateParentInWct: parentTaskId=%d parentWinMode=%d resolvedWinMode=%d",
            parentTask.taskId,
            parentTask.windowingMode,
            resolvedWinMode,
        )
        if (resolvedWinMode != parentTask.windowingMode) {
            wct.setWindowingMode(parentTask.token, resolvedWinMode)
            wct.setBounds(
                parentTask.token,
                if (resolvedWinMode == WINDOWING_MODE_FREEFORM) defaultFreeformBounds else Rect(),
            )
        }
        if (resolvedWinMode == WINDOWING_MODE_FULLSCREEN) {
            maybeAddMoveToFullscreenChanges(wct, parentTask)
        }
    }

    /**
     * In multi-activity PiP case, if the task entering PiP was previously active in a Desk and is
     * now expanding to fullscreen, call [DesktopTasksController#addMoveToFullscreenChanges] for the
     * parent task to properly move the task to fullscreen.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param parentTask the multi-activity PiP parent
     */
    private fun maybeAddMoveToFullscreenChanges(
        wct: WindowContainerTransaction,
        parentTask: RunningTaskInfo,
    ) {
        val desktopRepository = desktopUserRepositories.getProfile(parentTask.userId)
        if (!desktopRepository.isActiveTask(parentTask.taskId)) {
            logW(
                "maybeAddMoveToFullscreenChanges: parentTask with id=%d is not active in any desk",
                parentTask.taskId,
            )
            return
        }

        logD(
            "maybeAddMoveToFullscreenChanges: addMoveToFullscreenChanges, taskId=%d displayId=%d",
            parentTask.taskId,
            parentTask.displayId,
        )
        desktopTasksController.addMoveToFullscreenChanges(
            wct = wct,
            taskInfo = parentTask,
            willExitDesktop = true,
        )
    }

    /**
     * This is called by [PipScheduler#getExitPipViaExpandTransaction] before starting an EXIT_PiP
     * transition. If the ENABLE_MULTIPLE_DESKTOPS_BACKEND flag is enabled and the PiP task is going
     * to freeform windowing mode, we need to reparent the task to the root desk. In addition, if we
     * are expanding PiP at Home (as in with a Desktop-first display), we also need to activate the
     * default desk.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param taskId of the task that is exiting PiP
     */
    fun maybeReparentTaskToDesk(wct: WindowContainerTransaction, taskId: Int) {
        // Temporary workaround for b/409201669: We always expand to fullscreen if we're exiting PiP
        // in the middle of Recents animation from Desktop session, so don't reparent to the Desk.
        if (
            !pipDesktopState.isDesktopWindowingPipEnabled() ||
                pipDesktopState.isRecentsAnimating() ||
                !DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            return
        }

        val runningTaskInfo = shellTaskOrganizer.getRunningTaskInfo(taskId)
        if (runningTaskInfo == null) {
            logW("maybeReparentTaskToDesk: Failed to find RunningTaskInfo for taskId=%d", taskId)
            return
        }

        val desktopRepository = desktopUserRepositories.getProfile(runningTaskInfo.userId)
        val displayId = runningTaskInfo.displayId
        if (!pipDesktopState.isPipInDesktopMode()) {
            logD("maybeReparentTaskToDesk: PiP transition is not in Desktop session")
            return
        }

        val deskId = getDeskId(desktopRepository, displayId)
        if (deskId == INVALID_DESK_ID) return

        val parentTaskId = runningTaskInfo.lastParentTaskIdBeforePip
        var parentTask: RunningTaskInfo? = null
        var shouldAddParentToDesk = false

        // If PiP is multi-activity, we should use the parent task for the rest of this method
        if (parentTaskId != ActivityTaskManager.INVALID_TASK_ID) {
            parentTask = shellTaskOrganizer.getRunningTaskInfo(parentTaskId)
            if (parentTask == null) {
                logW(
                    "maybeReparentTaskToDesk: Failed to find RunningTaskInfo for parentTaskId %d",
                    parentTaskId,
                )
                return
            }
            if (desktopRepository.isActiveTask(parentTaskId)) {
                logD(
                    "maybeReparentTaskToDesk: Multi-activity PiP with parent taskId=%d already " +
                        "in the Desk, move parent task to front",
                    parentTaskId,
                )
                moveParentTaskToFront(wct, parentTask, deskId)
                return
            } else {
                logD(
                    "maybeReparentTaskToDesk: Multi-activity PiP with parent taskId=%d not " +
                        "already in the Desk, should add parent to the desk",
                    parentTaskId,
                )
                shouldAddParentToDesk = true
            }
        }

        if (!desktopRepository.isDeskActive(deskId)) {
            logD(
                "maybeReparentTaskToDesk: addDeskActivationChanges, taskId=%d deskId=%d, " +
                    "displayId=%d",
                if (shouldAddParentToDesk) parentTaskId else runningTaskInfo.taskId,
                deskId,
                displayId,
            )
            desktopTasksController.addDeskActivationChanges(
                deskId = deskId,
                wct = wct,
                newTask = if (shouldAddParentToDesk) parentTask!! else runningTaskInfo,
                displayId = displayId,
                userId = desktopRepository.userId,
                enterReason = EnterReason.EXIT_PIP,
            )
        }

        logD(
            "maybeReparentTaskToDesk: addMoveToDeskTaskChanges, taskId=%d deskId=%d",
            if (shouldAddParentToDesk) parentTaskId else runningTaskInfo.taskId,
            deskId,
        )
        desktopTasksController.addMoveToDeskTaskChanges(
            wct = wct,
            task = if (shouldAddParentToDesk) parentTask!! else runningTaskInfo,
            deskId = deskId,
        )
    }

    /**
     * In multi-activity PiP case, call [DesktopTasksController#addMoveTaskToFrontChanges] to move
     * the parent task to front within the desk.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param parentTask the parent task
     * @param deskId desk id that the multi-activity PiP parent is in
     */
    private fun moveParentTaskToFront(
        wct: WindowContainerTransaction,
        parentTask: RunningTaskInfo,
        deskId: Int,
    ) {
        logD("moveParentTaskToFront: parentTaskId=%d deskId=%d", parentTask.taskId, deskId)
        desktopTasksController.addMoveTaskToFrontChanges(
            wct = wct,
            deskId = deskId,
            taskInfo = parentTask,
        )
    }

    /**
     * This is called by [PipTransition#handleRequest] when a request for entering PiP is received.
     *
     * @param wct WindowContainerTransaction that will apply these changes
     * @param transition that will apply this transaction
     * @param taskInfo of the task that is entering PiP
     */
    fun handlePipTransition(
        wct: WindowContainerTransaction,
        transition: IBinder,
        taskInfo: RunningTaskInfo,
    ) {
        if (!pipDesktopState.isDesktopWindowingPipEnabled()) {
            return
        }

        // Early return if the transition is a synthetic transition that is not backed by a true
        // system transition.
        if (transition == DesktopTasksController.SYNTHETIC_TRANSITION) {
            logD("handlePipTransition: SYNTHETIC_TRANSITION, not a true transition")
            return
        }

        val taskId = taskInfo.taskId
        val displayId = taskInfo.displayId
        val desktopRepository = desktopUserRepositories.getProfile(taskInfo.userId)
        if (!pipDesktopState.isPipInDesktopMode()) {
            logD("handlePipTransition: PiP transition is not in Desktop session")
            return
        }

        val deskId = getDeskId(desktopRepository, displayId)
        if (deskId == INVALID_DESK_ID) return

        // For multi-activity PiP, minimize the parent/original task
        if (taskInfo.numActivities > 1) {
            logD(
                "handlePipTransition: minimizeMultiActivityPipTask, taskId=%d deskId=%d",
                taskInfo.taskId,
                deskId,
            )
            val runOnTransitStart =
                desktopTasksController.minimizeMultiActivityPipTask(
                    wct = wct,
                    deskId = deskId,
                    task = taskInfo,
                )
            runOnTransitStart?.invoke(transition)
        }

        val isLastTask =
            desktopRepository.isOnlyVisibleNonClosingTaskInDesk(
                taskId = taskId,
                deskId = deskId,
                displayId = displayId,
            )
        if (!isLastTask) {
            logD("handlePipTransition: PiP task is not last visible task in Desk")
            return
        }

        logD(
            "handlePipTransition: performDesktopExitCleanUp, taskId=%d deskId=%d displayId=%d",
            taskInfo.taskId,
            deskId,
            displayId,
        )
        val desktopExitRunnable =
            desktopTasksController.performDesktopExitCleanUp(
                wct = wct,
                deskId = deskId,
                displayId = displayId,
                userId = taskInfo.userId,
                willExitDesktop = true,
                removingLastTaskId = taskId,
                exitReason = ExitReason.ENTER_PIP,
            )
        desktopExitRunnable?.invoke(transition)
    }

    private fun getDeskId(repository: DesktopRepository, displayId: Int): Int =
        repository.getActiveDeskId(displayId)
            ?: if (
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                    !pipDesktopState.isDisplayDesktopFirst(displayId)
            ) {
                logW("getDeskId: Active desk not found for display id %d", displayId)
                INVALID_DESK_ID
            } else {
                checkNotNull(repository.getDefaultDeskId(displayId)) {
                    "$TAG: getDeskId: " +
                        "Expected a default desk to exist in display with id $displayId"
                }
            }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        private const val TAG = "DesktopPipTransitionController"
        private const val INVALID_DESK_ID = -1
    }
}
