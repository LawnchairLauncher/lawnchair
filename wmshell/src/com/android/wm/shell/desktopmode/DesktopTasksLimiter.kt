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

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import android.window.WindowContainerTransaction
import androidx.annotation.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.sysui.UserChangeListener
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionObserver
import com.android.wm.shell.windowdecor.tiling.SnapEventHandler

/**
 * Keeps track of minimized tasks and limits the number of tasks shown in Desktop Mode.
 *
 * [maxTasksLimit] must be strictly greater than 0 if it's given.
 *
 * TODO(b/400634379): Separate two responsibilities of this class into two classes.
 */
class DesktopTasksLimiter(
    private val transitions: Transitions,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val shellTaskOrganizer: ShellTaskOrganizer,
    private val desksOrganizer: DesksOrganizer,
    private val desktopMixedTransitionHandler: DesktopMixedTransitionHandler,
    private val maxTasksLimit: Int?,
) {
    private val minimizeTransitionObserver = MinimizeTransitionObserver()
    @VisibleForTesting val leftoverMinimizedTasksRemover = LeftoverMinimizedTasksRemover()

    private var userId: Int
    lateinit var snapEventHandler: SnapEventHandler

    init {
        maxTasksLimit?.let {
            require(it > 0) {
                "DesktopTasksLimiter: maxTasksLimit should be greater than 0. Current value: $it."
            }
        }
        transitions.registerObserver(minimizeTransitionObserver)
        userId = ActivityManager.getCurrentUser()
        desktopUserRepositories.current.addActiveTaskListener(leftoverMinimizedTasksRemover)
        if (maxTasksLimit != null) {
            logV("Starting limiter with a maximum of %d tasks", maxTasksLimit)
        } else {
            logV("Starting limiter without the task limit")
        }
    }

    /** Describes a task launch that might trigger a task limit minimize transition. */
    data class LaunchDetails(val deskId: Int, val taskId: Int?)

    data class TaskDetails(
        val displayId: Int,
        val taskId: Int,
        var transitionInfo: TransitionInfo? = null,
        val minimizeReason: MinimizeReason? = null,
        val unminimizeReason: UnminimizeReason? = null,
    )

    /**
     * Returns the task being minimized in the given transition if that transition is a pending or
     * active minimize transition.
     */
    fun getMinimizingTask(transition: IBinder): TaskDetails? {
        return minimizeTransitionObserver.getMinimizingTask(transition)
    }

    /**
     * Returns the task being unminimized in the given transition if that transition is a pending or
     * active unminimize transition.
     */
    fun getUnminimizingTask(transition: IBinder): TaskDetails? {
        return minimizeTransitionObserver.getUnminimizingTask(transition)
    }

    // TODO(b/333018485): replace this observer when implementing the minimize-animation
    private inner class MinimizeTransitionObserver : TransitionObserver {
        private val pendingTaskLimitTransitionTokens = mutableMapOf<IBinder, LaunchDetails>()
        private val pendingTransitionTokensAndTasks = mutableMapOf<IBinder, TaskDetails>()
        private val activeTransitionTokensAndTasks = mutableMapOf<IBinder, TaskDetails>()
        private val pendingUnminimizeTransitionTokensAndTasks = mutableMapOf<IBinder, TaskDetails>()
        private val activeUnminimizeTransitionTokensAndTasks = mutableMapOf<IBinder, TaskDetails>()

        fun addPendingTransitionToken(transition: IBinder, taskDetails: TaskDetails) {
            pendingTransitionTokensAndTasks[transition] = taskDetails
        }

        fun addPendingTaskLimitTransitionToken(transition: IBinder, details: LaunchDetails) {
            pendingTaskLimitTransitionTokens[transition] = details
        }

        fun addPendingUnminimizeTransitionToken(transition: IBinder, taskDetails: TaskDetails) {
            pendingUnminimizeTransitionTokensAndTasks[transition] = taskDetails
        }

        fun getMinimizingTask(transition: IBinder): TaskDetails? {
            return pendingTransitionTokensAndTasks[transition]
                ?: activeTransitionTokensAndTasks[transition]
        }

        fun hasTaskLimitTransition(transition: IBinder): Boolean =
            pendingTaskLimitTransitionTokens.contains(transition)

        fun getUnminimizingTask(transition: IBinder): TaskDetails? {
            return pendingUnminimizeTransitionTokensAndTasks[transition]
                ?: activeUnminimizeTransitionTokensAndTasks[transition]
        }

        override fun onTransitionReady(
            transition: IBinder,
            info: TransitionInfo,
            startTransaction: SurfaceControl.Transaction,
            finishTransaction: SurfaceControl.Transaction,
        ) {
            val taskRepository = desktopUserRepositories.current
            handleTaskLimitTransitionReady(taskRepository, transition, info)
            handleMinimizeTransitionReady(taskRepository, transition, info)
            handleUnminimizeTransitionReady(transition)
        }

        /**
         * Handles [#onTransitionReady()] for transitions that might trigger task limit minimize.
         */
        private fun handleTaskLimitTransitionReady(
            taskRepository: DesktopRepository,
            transition: IBinder,
            info: TransitionInfo,
        ) {
            val launchDetails = pendingTaskLimitTransitionTokens.remove(transition) ?: return
            logV("handleTaskLimitTransitionReady, transition=$transition, info=$info")
            markClosingTasks(taskRepository, info, launchDetails)
            transitions.runOnIdle {
                val expandedTaskIds =
                    taskRepository.getExpandedTasksIdsInDeskOrdered(launchDetails.deskId).filter {
                        !taskRepository.isClosingTask(it)
                    }
                logV("runOnIdle, expandedTasks=$expandedTaskIds, after transition=$transition")
                triggerMinimizeTransition(launchDetails.deskId, expandedTaskIds)
            }
        }

        private fun markClosingTasks(
            taskRepository: DesktopRepository,
            info: TransitionInfo,
            launchDetails: LaunchDetails,
        ) {
            // markClosingTasks() is a workaround while
            // ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS is ramping up, so don't run this
            // logic when that flag has been enabled.
            if (DesktopExperienceFlags.ENABLE_WINDOWING_TRANSITION_HANDLERS_OBSERVERS.isTrue) {
                return
            }
            info.changes.forEach { change ->
                val taskInfo = change.taskInfo ?: return@forEach
                if (change.mode != TRANSIT_CLOSE || !taskInfo.isFreeform) {
                    return@forEach
                }
                taskRepository.addClosingTask(
                    taskInfo.displayId,
                    launchDetails.deskId,
                    taskInfo.taskId,
                )
            }
        }

        private fun triggerMinimizeTransition(deskId: Int, expandedTaskIds: List<Int>) {
            val task = getRunningTaskToMinimize(expandedTaskIds) ?: return
            logV("triggerMinimizeTransition, found running task -> start transition, %s", task)
            val wct = WindowContainerTransaction()
            addMinimizeChange(deskId, task, wct)
            snapEventHandler.removeTaskIfTiled(task.displayId, task.taskId)
            val transition =
                desktopMixedTransitionHandler.startTaskLimitMinimizeTransition(wct, task.taskId)
            addPendingMinimizeChange(
                transition,
                task.displayId,
                task.taskId,
                MinimizeReason.TASK_LIMIT,
            )
        }

        private fun getRunningTaskToMinimize(expandedTaskIds: List<Int>): RunningTaskInfo? {
            var taskIds = expandedTaskIds
            for (i in 1..expandedTaskIds.size) {
                val taskIdToMinimize =
                    getTaskIdToMinimize(taskIds, /* launchingNewIntent= */ false) ?: return null
                val task = shellTaskOrganizer.getRunningTaskInfo(taskIdToMinimize)
                if (task != null) {
                    return task
                } else {
                    logW(
                        "Tried to minimize non-running task#%s, Try next task instead.",
                        taskIdToMinimize,
                    )
                }
                // Ignore the non-existing task
                taskIds = taskIds.minus(taskIdToMinimize)
            }
            return null
        }

        private fun handleMinimizeTransitionReady(
            taskRepository: DesktopRepository,
            transition: IBinder,
            info: TransitionInfo,
        ) {
            val taskToMinimize = pendingTransitionTokensAndTasks.remove(transition) ?: return
            if (!taskRepository.isActiveTask(taskToMinimize.taskId)) return
            if (!isTaskReadyForMinimize(info, taskToMinimize)) {
                logV("task %d is not reordered to back nor invis", taskToMinimize.taskId)
                return
            }
            taskToMinimize.transitionInfo = info
            activeTransitionTokensAndTasks[transition] = taskToMinimize

            // Save current bounds before minimizing in case we need to restore to it later.
            val boundsBeforeMinimize =
                info.changes
                    .find { change -> change.taskInfo?.taskId == taskToMinimize.taskId }
                    ?.startAbsBounds
            taskRepository.saveBoundsBeforeMinimize(taskToMinimize.taskId, boundsBeforeMinimize)

            this@DesktopTasksLimiter.minimizeTask(taskToMinimize.displayId, taskToMinimize.taskId)
        }

        private fun handleUnminimizeTransitionReady(transition: IBinder) {
            val taskToUnminimize =
                pendingUnminimizeTransitionTokensAndTasks.remove(transition) ?: return
            activeUnminimizeTransitionTokensAndTasks[transition] = taskToUnminimize
        }

        /**
         * Returns whether the Task [taskDetails] is being reordered to the back in the transition
         * [info], or is already invisible.
         *
         * This check confirms a task should be minimized before minimizing it.
         */
        private fun isTaskReadyForMinimize(
            info: TransitionInfo,
            taskDetails: TaskDetails,
        ): Boolean {
            val taskChange =
                info.changes.find { change -> change.taskInfo?.taskId == taskDetails.taskId }
            val taskRepository = desktopUserRepositories.current
            if (taskChange == null) return !taskRepository.isVisibleTask(taskDetails.taskId)
            return taskChange.mode == TRANSIT_TO_BACK
        }

        private fun getMinimizeChange(info: TransitionInfo, taskId: Int): TransitionInfo.Change? =
            info.changes.find { change ->
                change.taskInfo?.taskId == taskId && change.mode == TRANSIT_TO_BACK
            }

        override fun onTransitionMerged(merged: IBinder, playing: IBinder) {
            activeTransitionTokensAndTasks.remove(merged)
            pendingTransitionTokensAndTasks.remove(merged)?.let { taskToTransfer ->
                pendingTransitionTokensAndTasks[playing] = taskToTransfer
            }

            activeUnminimizeTransitionTokensAndTasks.remove(merged)
            pendingUnminimizeTransitionTokensAndTasks.remove(merged)?.let { taskToTransfer ->
                pendingUnminimizeTransitionTokensAndTasks[playing] = taskToTransfer
            }
        }

        override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
            pendingTransitionTokensAndTasks.remove(transition)
            activeUnminimizeTransitionTokensAndTasks.remove(transition)
            pendingUnminimizeTransitionTokensAndTasks.remove(transition)
        }
    }

    @VisibleForTesting
    inner class LeftoverMinimizedTasksRemover :
        DesktopRepository.ActiveTasksListener, UserChangeListener {
        override fun onActiveTasksChanged(displayId: Int) {
            // If back navigation is enabled, we shouldn't remove the leftover tasks
            if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_BACK_NAVIGATION.isTrue()) return
            val wct = WindowContainerTransaction()
            removeLeftoverMinimizedTasks(displayId, wct)
            shellTaskOrganizer.applyTransaction(wct)
        }

        fun removeLeftoverMinimizedTasks(displayId: Int, wct: WindowContainerTransaction) {
            val taskRepository = desktopUserRepositories.current
            if (taskRepository.getExpandedTasksOrdered(displayId).isNotEmpty()) return
            val remainingMinimizedTasks = taskRepository.getMinimizedTasks(displayId)
            if (remainingMinimizedTasks.isEmpty()) return

            logV("Removing leftover minimized tasks: %s", remainingMinimizedTasks)
            remainingMinimizedTasks.forEach { taskIdToRemove ->
                val taskToRemove = shellTaskOrganizer.getRunningTaskInfo(taskIdToRemove)
                if (taskToRemove != null) {
                    wct.removeTask(taskToRemove.token)
                }
            }
        }

        override fun onUserChanged(newUserId: Int, userContext: Context) {
            // Removes active task listener for the previous repository
            desktopUserRepositories.getProfile(userId).removeActiveTasksListener(this)

            // Sets active listener for the current repository.
            userId = newUserId
            desktopUserRepositories.getProfile(newUserId).addActiveTaskListener(this)
        }
    }

    /**
     * Mark task with [taskId] on [displayId] as minimized.
     *
     * This should be after the corresponding transition has finished so we don't minimize the task
     * if the transition fails.
     */
    private fun minimizeTask(displayId: Int, taskId: Int) {
        logV("Minimize taskId=%d, displayId=%d", taskId, displayId)
        val taskRepository = desktopUserRepositories.current
        taskRepository.minimizeTask(displayId, taskId)
    }

    /**
     * Adds a minimize-transition to [wct] if adding [newFrontTaskInfo] crosses task limit,
     * returning the task to minimize.
     */
    fun addAndGetMinimizeTaskChanges(
        deskId: Int,
        wct: WindowContainerTransaction,
        newFrontTaskId: Int?,
        launchingNewIntent: Boolean = false,
    ): Int? {
        logV("addAndGetMinimizeTaskChanges, newFrontTask=%d", newFrontTaskId)
        val taskRepository = desktopUserRepositories.current
        val taskIdToMinimize =
            getTaskIdToMinimize(
                taskRepository.getExpandedTasksIdsInDeskOrdered(deskId),
                newFrontTaskId,
                launchingNewIntent,
            )
        taskIdToMinimize
            ?.let { shellTaskOrganizer.getRunningTaskInfo(it) }
            ?.let { task -> addMinimizeChange(deskId, task, wct) }
        return taskIdToMinimize
    }

    private fun addMinimizeChange(
        deskId: Int,
        task: ActivityManager.RunningTaskInfo,
        wct: WindowContainerTransaction,
    ) =
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            desksOrganizer.minimizeTask(wct, deskId, task)
        } else {
            wct.reorder(task.token, /* onTop= */ false)
        }

    /**
     * Add a pending transition to trigger a new minimize transition in case the pending transition
     * takes us over the task limit.
     */
    fun addPendingTaskLimitTransition(transition: IBinder, deskId: Int, taskId: Int?) =
        minimizeTransitionObserver.addPendingTaskLimitTransitionToken(
            transition,
            LaunchDetails(deskId, taskId),
        )

    /** For testing only: returns whether there are any pending task limit transitions. */
    @VisibleForTesting
    fun hasTaskLimitTransitionForTesting(transition: IBinder) =
        minimizeTransitionObserver.hasTaskLimitTransition(transition)

    /**
     * Add a pending minimize transition change to update the list of minimized apps once the
     * transition goes through.
     */
    fun addPendingMinimizeChange(
        transition: IBinder,
        displayId: Int,
        taskId: Int,
        minimizeReason: MinimizeReason,
    ) {
        minimizeTransitionObserver.addPendingTransitionToken(
            transition,
            TaskDetails(displayId, taskId, transitionInfo = null, minimizeReason = minimizeReason),
        )
    }

    /**
     * Add a pending unminimize transition change to allow tracking unminimizing transitions /
     * tasks.
     */
    fun addPendingUnminimizeChange(
        transition: IBinder,
        displayId: Int,
        taskId: Int,
        unminimizeReason: UnminimizeReason,
    ) =
        minimizeTransitionObserver.addPendingUnminimizeTransitionToken(
            transition,
            TaskDetails(displayId, taskId, unminimizeReason = unminimizeReason),
        )

    /**
     * Returns the minimized task from the list of visible tasks ordered from front to back with the
     * new task placed in front of other tasks.
     */
    fun getTaskIdToMinimize(
        visibleOrderedTasks: List<Int>,
        newTaskIdInFront: Int? = null,
        launchingNewIntent: Boolean = false,
    ): Int? {
        return getTaskIdToMinimize(
            createOrderedTaskListWithGivenTaskInFront(visibleOrderedTasks, newTaskIdInFront),
            launchingNewIntent,
        )
    }

    /** Returns the Task to minimize given a list of visible tasks ordered from front to back. */
    private fun getTaskIdToMinimize(
        visibleOrderedTasks: List<Int>,
        launchingNewIntent: Boolean,
    ): Int? {
        val newTasksOpening = if (launchingNewIntent) 1 else 0
        val taskLimit = (maxTasksLimit ?: Int.MAX_VALUE)
        if (visibleOrderedTasks.size + newTasksOpening <= taskLimit) {
            logV(
                "No need to minimize; tasks below limit, " +
                    " visible tasks: %s, new task: %s, task limit: %s",
                visibleOrderedTasks,
                launchingNewIntent,
                taskLimit,
            )
            // No need to minimize anything
            return null
        }
        return visibleOrderedTasks.last()
    }

    private fun createOrderedTaskListWithGivenTaskInFront(
        existingTaskIdsOrderedFrontToBack: List<Int>,
        newTaskId: Int?,
    ): List<Int> {
        return if (newTaskId == null) existingTaskIdsOrderedFrontToBack
        else
            listOf(newTaskId) +
                existingTaskIdsOrderedFrontToBack.filter { taskId -> taskId != newTaskId }
    }

    @VisibleForTesting fun getTransitionObserver(): TransitionObserver = minimizeTransitionObserver

    private fun logV(msg: String, vararg arguments: Any?) {
        ProtoLog.v(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private companion object {
        const val TAG = "DesktopTasksLimiter"
    }
}
