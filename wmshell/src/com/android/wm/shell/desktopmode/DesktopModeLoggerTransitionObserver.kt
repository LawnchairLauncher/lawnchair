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
import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.app.TaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.os.IBinder
import android.os.SystemProperties
import android.os.Trace
import android.util.SparseArray
import android.view.SurfaceControl
import android.view.WindowManager
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import androidx.annotation.VisibleForTesting
import androidx.core.util.containsKey
import androidx.core.util.forEach
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import androidx.core.util.plus
import androidx.core.util.putAll
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.FocusReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.TaskUpdate
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

/**
 * A [Transitions.TransitionObserver] that observes transitions and the proposed changes to log
 * appropriate desktop mode session log events. This observes transitions related to desktop mode
 * and other transitions that originate both within and outside shell.
 */
class DesktopModeLoggerTransitionObserver(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val desktopModeEventLogger: DesktopModeEventLogger,
    private val desktopTasksLimiter: Optional<DesktopTasksLimiter>,
    desktopState: DesktopState,
) : Transitions.TransitionObserver {

    init {
        if (desktopState.canEnterDesktopMode) {
            shellInit.addInitCallback(this::onInit, this)
        }
    }

    // A sparse array of visible freeform tasks and taskInfos
    private val visibleFreeformTaskInfos: SparseArray<TaskInfo> = SparseArray()

    // Caching the taskInfos to handle canceled recents animations, if we identify that the recents
    // animation was cancelled, we restore these tasks to calculate the post-Transition state
    private val tasksSavedForRecents: SparseArray<TaskInfo> = SparseArray()

    // Caching whether the previous transition was exit to overview.
    private var wasPreviousTransitionExitToOverview: Boolean = false

    // Caching whether the previous transition was exit due to screen off. This helps check if a
    // following enter reason could be Screen On
    private var wasPreviousTransitionExitByScreenOff: Boolean = false

    private var focusedFreeformTask: TaskInfo? = null

    @VisibleForTesting var isSessionActive: Boolean = false

    fun onInit() {
        transitions.registerObserver(this)
        SystemProperties.set(
            VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY,
            VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY_DEFAULT_VALUE,
        )
        desktopModeEventLogger.logTaskInfoStateInit()
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        // this was a new recents animation
        if (info.isExitToRecentsTransition() && tasksSavedForRecents.isEmpty()) {
            ProtoLog.v(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: Recents animation running, saving tasks for later",
            )
            // TODO (b/326391303) - avoid logging session exit if we can identify a cancelled
            // recents animation

            // when recents animation is running, all freeform tasks are sent TO_BACK temporarily
            // if the user ends up at home, we need to update the visible freeform tasks
            // if the user cancels the animation, the subsequent transition is NONE
            // if the user opens a new task, the subsequent transition is OPEN with flag
            tasksSavedForRecents.putAll(visibleFreeformTaskInfos)
        }

        // figure out what the new state of freeform tasks would be post transition
        var postTransitionVisibleFreeformTasks = getPostTransitionVisibleFreeformTaskInfos(info)

        // A canceled recents animation is followed by a TRANSIT_NONE transition with no flags, if
        // that's the case, we might have accidentally logged a session exit and would need to
        // revaluate again. Add all the tasks back.
        // This will start a new desktop mode session.
        if (
            info.type == WindowManager.TRANSIT_NONE &&
                info.flags == 0 &&
                tasksSavedForRecents.isNotEmpty()
        ) {
            ProtoLog.v(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: Canceled recents animation, restoring tasks",
            )
            // restore saved tasks in the updated set and clear for next use
            postTransitionVisibleFreeformTasks += tasksSavedForRecents
            tasksSavedForRecents.clear()
        }

        // identify if we need to log any changes and update the state of visible freeform tasks
        identifyLogEventAndUpdateState(
            transition = transition,
            transitionInfo = info,
            preTransitionVisibleFreeformTasks = visibleFreeformTaskInfos,
            postTransitionVisibleFreeformTasks = postTransitionVisibleFreeformTasks,
            newFocusedFreeformTask = getNewFocusedFreeformTask(info),
        )
        wasPreviousTransitionExitToOverview = info.isExitToRecentsTransition()
    }

    override fun onTransitionStarting(transition: IBinder) {}

    override fun onTransitionMerged(merged: IBinder, playing: IBinder) {}

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {}

    fun onTaskVanished(taskInfo: RunningTaskInfo) {
        // At this point the task should have been cleared up due to transition. If it's not yet
        // cleared up, it might be one of the edge cases where transitions don't give the correct
        // signal.
        if (visibleFreeformTaskInfos.containsKey(taskInfo.taskId)) {
            val postTransitionFreeformTasks: SparseArray<TaskInfo> = SparseArray()
            postTransitionFreeformTasks.putAll(visibleFreeformTaskInfos)
            postTransitionFreeformTasks.remove(taskInfo.taskId)
            ProtoLog.v(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: processing tasks after task vanished %s",
                postTransitionFreeformTasks.size(),
            )
            identifyLogEventAndUpdateState(
                transition = null,
                transitionInfo = null,
                preTransitionVisibleFreeformTasks = visibleFreeformTaskInfos,
                postTransitionVisibleFreeformTasks = postTransitionFreeformTasks,
                newFocusedFreeformTask = null,
            )
        }
    }

    // Returns null if there was no change in focused task
    private fun getNewFocusedFreeformTask(info: TransitionInfo): TaskInfo? {
        val freeformWindowChanges =
            info.changes
                .filter { it.taskInfo != null && it.requireTaskInfo().taskId != INVALID_TASK_ID }
                .filter { it.requireTaskInfo().isFreeformWindow() }
        return freeformWindowChanges
            .findLast { change ->
                change.hasFlags(FLAG_MOVED_TO_TOP) || change.mode == TRANSIT_OPEN
            }
            ?.taskInfo
    }

    private fun getPostTransitionVisibleFreeformTaskInfos(
        info: TransitionInfo
    ): SparseArray<TaskInfo> {
        // device is sleeping, so no task will be visible anymore
        if (info.type == WindowManager.TRANSIT_SLEEP) {
            return SparseArray()
        }

        // filter changes involving freeform tasks or tasks that were cached in previous state
        val changesToFreeformWindows =
            info.changes
                .filter { it.taskInfo != null && it.requireTaskInfo().taskId != INVALID_TASK_ID }
                .filter {
                    it.requireTaskInfo().isFreeformWindow() ||
                        visibleFreeformTaskInfos.containsKey(it.requireTaskInfo().taskId)
                }

        val postTransitionFreeformTasks: SparseArray<TaskInfo> = SparseArray()
        // start off by adding all existing tasks
        postTransitionFreeformTasks.putAll(visibleFreeformTaskInfos)

        // the combined set of taskInfos we are interested in this transition change
        for (change in changesToFreeformWindows) {
            val taskInfo = change.requireTaskInfo()

            // check if this task existed as freeform window in previous cached state and it's now
            // changing window modes
            if (
                visibleFreeformTaskInfos.containsKey(taskInfo.taskId) &&
                    visibleFreeformTaskInfos.get(taskInfo.taskId).isFreeformWindow() &&
                    !taskInfo.isFreeformWindow()
            ) {
                postTransitionFreeformTasks.remove(taskInfo.taskId)
                // no need to evaluate new visibility of this task, since it's no longer a freeform
                // window
                continue
            }

            // check if the task is visible after this change, otherwise remove it
            if (isTaskVisibleAfterChange(change)) {
                postTransitionFreeformTasks.put(taskInfo.taskId, taskInfo)
            } else {
                postTransitionFreeformTasks.remove(taskInfo.taskId)
            }
        }

        ProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: taskInfo map after processing changes %s",
            postTransitionFreeformTasks.size(),
        )

        return postTransitionFreeformTasks
    }

    /**
     * Look at the [TransitionInfo.Change] and figure out if this task will be visible after this
     * change is processed
     */
    private fun isTaskVisibleAfterChange(change: TransitionInfo.Change): Boolean =
        when {
            TransitionUtil.isOpeningType(change.mode) -> true
            TransitionUtil.isClosingType(change.mode) -> false
            // change mode TRANSIT_CHANGE is only for visible to visible transitions
            change.mode == WindowManager.TRANSIT_CHANGE -> true
            else -> false
        }

    /**
     * Log the appropriate log event based on the new state of TasksInfos and previously cached
     * state and update it
     */
    private fun identifyLogEventAndUpdateState(
        transition: IBinder?,
        transitionInfo: TransitionInfo?,
        preTransitionVisibleFreeformTasks: SparseArray<TaskInfo>,
        postTransitionVisibleFreeformTasks: SparseArray<TaskInfo>,
        newFocusedFreeformTask: TaskInfo?,
    ) {
        if (
            postTransitionVisibleFreeformTasks.isEmpty() &&
                preTransitionVisibleFreeformTasks.isNotEmpty() &&
                isSessionActive
        ) {
            // Sessions is finishing, log task updates followed by an exit event
            identifyAndLogTaskUpdates(
                transition,
                transitionInfo,
                preTransitionVisibleFreeformTasks,
                postTransitionVisibleFreeformTasks,
                newFocusedFreeformTask,
            )

            desktopModeEventLogger.logSessionExit(getExitReason(transitionInfo))
            isSessionActive = false
        } else if (
            postTransitionVisibleFreeformTasks.isNotEmpty() &&
                preTransitionVisibleFreeformTasks.isEmpty() &&
                !isSessionActive
        ) {
            // Session is starting, log enter event followed by task updates
            isSessionActive = true
            desktopModeEventLogger.logSessionEnter(getEnterReason(transitionInfo))

            identifyAndLogTaskUpdates(
                transition,
                transitionInfo,
                preTransitionVisibleFreeformTasks,
                postTransitionVisibleFreeformTasks,
                newFocusedFreeformTask,
            )
        } else if (isSessionActive) {
            // Session is neither starting, nor finishing, log task updates if there are any
            identifyAndLogTaskUpdates(
                transition,
                transitionInfo,
                preTransitionVisibleFreeformTasks,
                postTransitionVisibleFreeformTasks,
                newFocusedFreeformTask,
            )
        }

        // update the state to the new version
        visibleFreeformTaskInfos.clear()
        visibleFreeformTaskInfos.putAll(postTransitionVisibleFreeformTasks)
        focusedFreeformTask = newFocusedFreeformTask
    }

    /** Compare the old and new state of taskInfos and identify and log the changes */
    private fun identifyAndLogTaskUpdates(
        transition: IBinder?,
        transitionInfo: TransitionInfo?,
        preTransitionVisibleFreeformTasks: SparseArray<TaskInfo>,
        postTransitionVisibleFreeformTasks: SparseArray<TaskInfo>,
        newFocusedFreeformTask: TaskInfo?,
    ) {
        postTransitionVisibleFreeformTasks.forEach { taskId, taskInfo ->
            val focusChangedReason = getFocusChangedReason(taskId, newFocusedFreeformTask)
            val currentTaskUpdate =
                buildTaskUpdateForTask(
                    taskInfo,
                    postTransitionVisibleFreeformTasks.size(),
                    focusChangedReason = focusChangedReason,
                )
            val previousTaskInfo = preTransitionVisibleFreeformTasks[taskId]
            when {
                // new tasks added
                previousTaskInfo == null -> {
                    // The current task is now visible while before it wasn't - this might be the
                    // result of an unminimize action.
                    val unminimizeReason = getUnminimizeReason(transition, taskInfo)
                    desktopModeEventLogger.logTaskAdded(
                        currentTaskUpdate.copy(unminimizeReason = unminimizeReason)
                    )
                    Trace.setCounter(
                        Trace.TRACE_TAG_WINDOW_MANAGER,
                        VISIBLE_TASKS_COUNTER_NAME,
                        postTransitionVisibleFreeformTasks.size().toLong(),
                    )
                    SystemProperties.set(
                        VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY,
                        postTransitionVisibleFreeformTasks.size().toString(),
                    )
                }
                focusChangedReason != null ->
                    desktopModeEventLogger.logTaskInfoChanged(currentTaskUpdate)
                // old tasks that were resized or repositioned
                // TODO(b/347935387): Log changes only once they are stable.
                buildTaskUpdateForTask(
                    previousTaskInfo,
                    postTransitionVisibleFreeformTasks.size(),
                    focusChangedReason = focusChangedReason,
                ) != currentTaskUpdate ->
                    desktopModeEventLogger.logTaskInfoChanged(currentTaskUpdate)
            }
        }

        // find old tasks that were removed
        preTransitionVisibleFreeformTasks.forEach { taskId, taskInfo ->
            if (!postTransitionVisibleFreeformTasks.containsKey(taskId)) {
                // The task is no longer visible, it might have been minimized, get the minimize
                // reason (if any)
                val minimizeReason = getMinimizeReason(transition, transitionInfo, taskInfo)
                val taskUpdate =
                    buildTaskUpdateForTask(
                        taskInfo,
                        postTransitionVisibleFreeformTasks.size(),
                        minimizeReason,
                    )
                desktopModeEventLogger.logTaskRemoved(taskUpdate)
                Trace.setCounter(
                    Trace.TRACE_TAG_WINDOW_MANAGER,
                    VISIBLE_TASKS_COUNTER_NAME,
                    postTransitionVisibleFreeformTasks.size().toLong(),
                )
                SystemProperties.set(
                    VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY,
                    postTransitionVisibleFreeformTasks.size().toString(),
                )
            }
        }
    }

    private fun getMinimizeReason(
        transition: IBinder?,
        transitionInfo: TransitionInfo?,
        taskInfo: TaskInfo,
    ): MinimizeReason? {
        if (transitionInfo?.type == Transitions.TRANSIT_MINIMIZE) {
            return MinimizeReason.MINIMIZE_BUTTON
        }
        if (transitionInfo?.type == TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE) {
            return MinimizeReason.TASK_LIMIT
        }
        val minimizingTask =
            transition?.let { desktopTasksLimiter.getOrNull()?.getMinimizingTask(transition) }
        if (minimizingTask?.taskId == taskInfo.taskId) {
            return minimizingTask.minimizeReason
        }
        return null
    }

    private fun getUnminimizeReason(transition: IBinder?, taskInfo: TaskInfo): UnminimizeReason? {
        val unminimizingTask =
            transition?.let { desktopTasksLimiter.getOrNull()?.getUnminimizingTask(transition) }
        if (unminimizingTask?.taskId == taskInfo.taskId) {
            return unminimizingTask.unminimizeReason
        }
        return null
    }

    private fun getFocusChangedReason(
        taskId: Int,
        newFocusedFreeformTask: TaskInfo?,
    ): FocusReason? {
        val newFocusedTask = newFocusedFreeformTask ?: return null
        if (taskId != newFocusedTask.taskId) return null
        return if (newFocusedTask != focusedFreeformTask) FocusReason.UNKNOWN else null
    }

    private fun buildTaskUpdateForTask(
        taskInfo: TaskInfo,
        visibleTasks: Int,
        minimizeReason: MinimizeReason? = null,
        unminimizeReason: UnminimizeReason? = null,
        focusChangedReason: FocusReason? = null,
    ): TaskUpdate {
        val screenBounds = taskInfo.configuration.windowConfiguration.bounds
        val positionInParent = taskInfo.positionInParent
        // We can't both minimize and unminimize the same task in one go.
        assert(minimizeReason == null || unminimizeReason == null)
        return TaskUpdate(
            instanceId = taskInfo.taskId,
            uid = taskInfo.effectiveUid,
            taskHeight = screenBounds.height(),
            taskWidth = screenBounds.width(),
            taskX = positionInParent.x,
            taskY = positionInParent.y,
            visibleTaskCount = visibleTasks,
            minimizeReason = minimizeReason,
            unminimizeReason = unminimizeReason,
            focusReason = focusChangedReason,
        )
    }

    /** Get [EnterReason] for this session enter */
    private fun getEnterReason(transitionInfo: TransitionInfo?): EnterReason {
        val enterReason =
            when {
                transitionInfo?.type == WindowManager.TRANSIT_WAKE
                // If there is a screen lock, desktop window entry is after dismissing keyguard
                ||
                    (transitionInfo?.type == WindowManager.TRANSIT_TO_BACK &&
                        wasPreviousTransitionExitByScreenOff) -> EnterReason.SCREEN_ON
                transitionInfo?.type == TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP ->
                    EnterReason.APP_HANDLE_DRAG
                transitionInfo?.type == TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON ->
                    EnterReason.APP_HANDLE_MENU_BUTTON
                transitionInfo?.type == TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW ->
                    EnterReason.APP_FROM_OVERVIEW
                transitionInfo?.type == TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT ->
                    EnterReason.KEYBOARD_SHORTCUT_ENTER
                // NOTE: the below condition also applies for EnterReason quickswitch
                transitionInfo?.type == WindowManager.TRANSIT_TO_FRONT -> EnterReason.OVERVIEW
                // Enter desktop mode from cancelled recents has no transition. Enter is detected on
                // the
                // next transition involving freeform windows.
                // TODO(b/346564416): Modify logging for cancelled recents once it transition is
                //  changed. Also see how to account to time difference between actual enter time
                // and
                //  time of this log. Also account for the missed session when exit happens just
                // after
                //  a cancelled recents.
                wasPreviousTransitionExitToOverview -> EnterReason.OVERVIEW
                transitionInfo?.type == WindowManager.TRANSIT_OPEN ->
                    EnterReason.APP_FREEFORM_INTENT
                else -> {
                    ProtoLog.w(
                        WM_SHELL_DESKTOP_MODE,
                        "Unknown enter reason for transition type: %s",
                        transitionInfo?.type,
                    )
                    EnterReason.UNKNOWN_ENTER
                }
            }
        wasPreviousTransitionExitByScreenOff = false
        return enterReason
    }

    /** Get [ExitReason] for this session exit */
    private fun getExitReason(transitionInfo: TransitionInfo?): ExitReason =
        when {
            transitionInfo?.type == WindowManager.TRANSIT_SLEEP -> {
                wasPreviousTransitionExitByScreenOff = true
                ExitReason.SCREEN_OFF
            }
            // TODO(b/384490301): differentiate back gesture / button exit from clicking the close
            // button located in the window top corner.
            transitionInfo?.type == WindowManager.TRANSIT_TO_BACK -> ExitReason.TASK_MOVED_TO_BACK
            transitionInfo?.type == WindowManager.TRANSIT_CLOSE -> ExitReason.TASK_FINISHED
            transitionInfo?.type == TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG -> ExitReason.DRAG_TO_EXIT
            transitionInfo?.type == TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON ->
                ExitReason.APP_HANDLE_MENU_BUTTON_EXIT

            transitionInfo?.type == TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT ->
                ExitReason.KEYBOARD_SHORTCUT_EXIT

            transitionInfo?.isExitToRecentsTransition() == true ->
                ExitReason.RETURN_HOME_OR_OVERVIEW
            transitionInfo?.type == Transitions.TRANSIT_MINIMIZE -> ExitReason.TASK_MINIMIZED
            else -> {
                ProtoLog.w(
                    WM_SHELL_DESKTOP_MODE,
                    "Unknown exit reason for transition type: %s",
                    transitionInfo?.type,
                )
                ExitReason.UNKNOWN_EXIT
            }
        }

    /** Adds tasks to the saved copy of freeform taskId, taskInfo. Only used for testing. */
    @VisibleForTesting
    fun addTaskInfosToCachedMap(taskInfo: TaskInfo) {
        visibleFreeformTaskInfos.set(taskInfo.taskId, taskInfo)
    }

    /** Sets the focused task - only used for testing. */
    @VisibleForTesting
    fun setFocusedTaskForTesting(taskInfo: TaskInfo) {
        focusedFreeformTask = taskInfo
    }

    private fun TransitionInfo.Change.requireTaskInfo(): RunningTaskInfo =
        this.taskInfo ?: throw IllegalStateException("Expected TaskInfo in the Change")

    private fun TaskInfo.isFreeformWindow(): Boolean = this.windowingMode == WINDOWING_MODE_FREEFORM

    private fun TransitionInfo.isExitToRecentsTransition(): Boolean =
        this.type == WindowManager.TRANSIT_TO_FRONT &&
            this.flags == WindowManager.TRANSIT_FLAG_IS_RECENTS

    companion object {
        @VisibleForTesting const val VISIBLE_TASKS_COUNTER_NAME = "desktop_mode_visible_tasks"
        @VisibleForTesting
        const val VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY =
            "debug.tracing." + VISIBLE_TASKS_COUNTER_NAME
        const val VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY_DEFAULT_VALUE = "0"
    }
}
