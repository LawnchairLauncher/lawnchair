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
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import androidx.annotation.VisibleForTesting
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.DesktopModeStatus
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionObserver
import com.android.wm.shell.util.KtProtoLog

/**
 * Limits the number of tasks shown in Desktop Mode.
 *
 * This class should only be used if
 * [com.android.window.flags.Flags.enableDesktopWindowingTaskLimit()] is true.
 */
class DesktopTasksLimiter (
        transitions: Transitions,
        private val taskRepository: DesktopModeTaskRepository,
        private val shellTaskOrganizer: ShellTaskOrganizer,
) {
    private val minimizeTransitionObserver = MinimizeTransitionObserver()

    init {
        transitions.registerObserver(minimizeTransitionObserver)
    }

    private data class TaskDetails (val displayId: Int, val taskId: Int)

    // TODO(b/333018485): replace this observer when implementing the minimize-animation
    private inner class MinimizeTransitionObserver : TransitionObserver {
        private val mPendingTransitionTokensAndTasks = mutableMapOf<IBinder, TaskDetails>()

        fun addPendingTransitionToken(transition: IBinder, taskDetails: TaskDetails) {
            mPendingTransitionTokensAndTasks[transition] = taskDetails
        }

        override fun onTransitionReady(
                transition: IBinder,
                info: TransitionInfo,
                startTransaction: SurfaceControl.Transaction,
                finishTransaction: SurfaceControl.Transaction
        ) {
            val taskToMinimize = mPendingTransitionTokensAndTasks.remove(transition) ?: return

            if (!taskRepository.isActiveTask(taskToMinimize.taskId)) return

            if (!isTaskReorderedToBackOrInvisible(info, taskToMinimize)) {
                KtProtoLog.v(
                        ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                        "DesktopTasksLimiter: task %d is not reordered to back nor invis",
                        taskToMinimize.taskId)
                return
            }
            this@DesktopTasksLimiter.markTaskMinimized(
                    taskToMinimize.displayId, taskToMinimize.taskId)
        }

        /**
         * Returns whether the given Task is being reordered to the back in the given transition, or
         * is already invisible.
         *
         * <p> This check can be used to double-check that a task was indeed minimized before
         * marking it as such.
         */
        private fun isTaskReorderedToBackOrInvisible(
                info: TransitionInfo,
                taskDetails: TaskDetails
        ): Boolean {
            val taskChange = info.changes.find { change ->
                change.taskInfo?.taskId == taskDetails.taskId }
            if (taskChange == null) {
                return !taskRepository.isVisibleTask(taskDetails.taskId)
            }
            return taskChange.mode == TRANSIT_TO_BACK
        }

        override fun onTransitionStarting(transition: IBinder) {}

        override fun onTransitionMerged(merged: IBinder, playing: IBinder) {
            mPendingTransitionTokensAndTasks.remove(merged)?.let { taskToTransfer ->
                mPendingTransitionTokensAndTasks[playing] = taskToTransfer
            }
        }

        override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
            KtProtoLog.v(
                    ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                    "DesktopTasksLimiter: transition %s finished", transition)
            mPendingTransitionTokensAndTasks.remove(transition)
        }
    }

    /**
     * Mark a task as minimized, this should only be done after the corresponding transition has
     * finished so we don't minimize the task if the transition fails.
     */
    private fun markTaskMinimized(displayId: Int, taskId: Int) {
        KtProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                "DesktopTasksLimiter: marking %d as minimized", taskId)
        taskRepository.minimizeTask(displayId, taskId)
    }

    /**
     * Add a minimize-transition to [wct] if adding [newFrontTaskInfo] brings us over the task
     * limit.
     *
     * @param transition the transition that the minimize-transition will be appended to, or null if
     * the transition will be started later.
     * @return the ID of the minimized task, or null if no task is being minimized.
     */
    fun addAndGetMinimizeTaskChangesIfNeeded(
            displayId: Int,
            wct: WindowContainerTransaction,
            newFrontTaskInfo: RunningTaskInfo,
    ): RunningTaskInfo? {
        KtProtoLog.v(
                ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                "DesktopTasksLimiter: addMinimizeBackTaskChangesIfNeeded, newFrontTask=%d",
                newFrontTaskInfo.taskId)
        val newTaskListOrderedFrontToBack = createOrderedTaskListWithGivenTaskInFront(
                taskRepository.getActiveNonMinimizedTasksOrderedFrontToBack(displayId),
                newFrontTaskInfo.taskId)
        val taskToMinimize = getTaskToMinimizeIfNeeded(newTaskListOrderedFrontToBack)
        if (taskToMinimize != null) {
            wct.reorder(taskToMinimize.token, false /* onTop */)
            return taskToMinimize
        }
        return null
    }

    /**
     * Add a pending minimize transition change, to update the list of minimized apps once the
     * transition goes through.
     */
    fun addPendingMinimizeChange(transition: IBinder, displayId: Int, taskId: Int) {
        minimizeTransitionObserver.addPendingTransitionToken(
                transition, TaskDetails(displayId, taskId))
    }

    /**
     * Returns the maximum number of tasks that should ever be displayed at the same time in Desktop
     * Mode.
     */
    fun getMaxTaskLimit(): Int = DesktopModeStatus.getMaxTaskLimit()

    /**
     * Returns the Task to minimize given 1. a list of visible tasks ordered from front to back and
     * 2. a new task placed in front of all the others.
     */
    fun getTaskToMinimizeIfNeeded(
            visibleFreeformTaskIdsOrderedFrontToBack: List<Int>,
            newTaskIdInFront: Int
    ): RunningTaskInfo? {
        return getTaskToMinimizeIfNeeded(
                createOrderedTaskListWithGivenTaskInFront(
                        visibleFreeformTaskIdsOrderedFrontToBack, newTaskIdInFront))
    }

    /** Returns the Task to minimize given a list of visible tasks ordered from front to back. */
    fun getTaskToMinimizeIfNeeded(
            visibleFreeformTaskIdsOrderedFrontToBack: List<Int>
    ): RunningTaskInfo? {
        if (visibleFreeformTaskIdsOrderedFrontToBack.size <= getMaxTaskLimit()) {
            KtProtoLog.v(
                    ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                    "DesktopTasksLimiter: no need to minimize; tasks below limit")
            // No need to minimize anything
            return null
        }
        val taskToMinimize =
                shellTaskOrganizer.getRunningTaskInfo(
                        visibleFreeformTaskIdsOrderedFrontToBack.last())
        if (taskToMinimize == null) {
            KtProtoLog.e(
                    ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE,
                    "DesktopTasksLimiter: taskToMinimize == null")
            return null
        }
        return taskToMinimize
    }

    private fun createOrderedTaskListWithGivenTaskInFront(
            existingTaskIdsOrderedFrontToBack: List<Int>,
            newTaskId: Int
    ): List<Int> {
        return listOf(newTaskId) +
                existingTaskIdsOrderedFrontToBack.filter { taskId -> taskId != newTaskId }
    }

    @VisibleForTesting
    fun getTransitionObserver(): TransitionObserver {
        return minimizeTransitionObserver
    }
}