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
import android.content.Context
import android.os.IBinder
import android.util.SparseArray
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import androidx.annotation.VisibleForTesting
import androidx.core.util.containsKey
import androidx.core.util.forEach
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import androidx.core.util.plus
import androidx.core.util.putAll
import com.android.internal.logging.InstanceId
import com.android.internal.logging.InstanceIdSequence
import com.android.internal.protolog.common.ProtoLog
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.TaskUpdate
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.DesktopModeStatus
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.util.KtProtoLog

/**
 * A [Transitions.TransitionObserver] that observes transitions and the proposed changes to log
 * appropriate desktop mode session log events. This observes transitions related to desktop mode
 * and other transitions that originate both within and outside shell.
 */
class DesktopModeLoggerTransitionObserver(
    context: Context,
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val desktopModeEventLogger: DesktopModeEventLogger
) : Transitions.TransitionObserver {

    private val idSequence: InstanceIdSequence by lazy { InstanceIdSequence(Int.MAX_VALUE) }

    init {
        if (
            Transitions.ENABLE_SHELL_TRANSITIONS && DesktopModeStatus.canEnterDesktopMode(context)
        ) {
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

    // The instanceId for the current logging session
    private var loggerInstanceId: InstanceId? = null

    private val isSessionActive: Boolean
        get() = loggerInstanceId != null

    private fun setSessionInactive() {
        loggerInstanceId = null
    }

    fun onInit() {
        transitions.registerObserver(this)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction
    ) {
        // this was a new recents animation
        if (info.isExitToRecentsTransition() && tasksSavedForRecents.isEmpty()) {
            KtProtoLog.v(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: Recents animation running, saving tasks for later"
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
            KtProtoLog.v(
                WM_SHELL_DESKTOP_MODE,
                "DesktopModeLogger: Canceled recents animation, restoring tasks"
            )
            // restore saved tasks in the updated set and clear for next use
            postTransitionVisibleFreeformTasks += tasksSavedForRecents
            tasksSavedForRecents.clear()
        }

        // identify if we need to log any changes and update the state of visible freeform tasks
        identifyLogEventAndUpdateState(
            transitionInfo = info,
            preTransitionVisibleFreeformTasks = visibleFreeformTaskInfos,
            postTransitionVisibleFreeformTasks = postTransitionVisibleFreeformTasks
        )
        wasPreviousTransitionExitToOverview = info.isExitToRecentsTransition()
    }

    override fun onTransitionStarting(transition: IBinder) {}

    override fun onTransitionMerged(merged: IBinder, playing: IBinder) {}

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {}

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

        KtProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeLogger: taskInfo map after processing changes %s",
            postTransitionFreeformTasks.size()
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
        transitionInfo: TransitionInfo,
        preTransitionVisibleFreeformTasks: SparseArray<TaskInfo>,
        postTransitionVisibleFreeformTasks: SparseArray<TaskInfo>
    ) {
        if (
            postTransitionVisibleFreeformTasks.isEmpty() &&
                preTransitionVisibleFreeformTasks.isNotEmpty() &&
                isSessionActive
        ) {
            // Sessions is finishing, log task updates followed by an exit event
            identifyAndLogTaskUpdates(
                loggerInstanceId!!.id,
                preTransitionVisibleFreeformTasks,
                postTransitionVisibleFreeformTasks
            )

            desktopModeEventLogger.logSessionExit(
                loggerInstanceId!!.id,
                getExitReason(transitionInfo)
            )

            setSessionInactive()
        } else if (
            postTransitionVisibleFreeformTasks.isNotEmpty() &&
                preTransitionVisibleFreeformTasks.isEmpty() &&
                !isSessionActive
        ) {
            // Session is starting, log enter event followed by task updates
            loggerInstanceId = idSequence.newInstanceId()
            desktopModeEventLogger.logSessionEnter(
                loggerInstanceId!!.id,
                getEnterReason(transitionInfo)
            )

            identifyAndLogTaskUpdates(
                loggerInstanceId!!.id,
                preTransitionVisibleFreeformTasks,
                postTransitionVisibleFreeformTasks
            )
        } else if (isSessionActive) {
            // Session is neither starting, nor finishing, log task updates if there are any
            identifyAndLogTaskUpdates(
                loggerInstanceId!!.id,
                preTransitionVisibleFreeformTasks,
                postTransitionVisibleFreeformTasks
            )
        }

        // update the state to the new version
        visibleFreeformTaskInfos.clear()
        visibleFreeformTaskInfos.putAll(postTransitionVisibleFreeformTasks)
    }

    // TODO(b/326231724) - Add logging around taskInfoChanges Updates
    /** Compare the old and new state of taskInfos and identify and log the changes */
    private fun identifyAndLogTaskUpdates(
        sessionId: Int,
        preTransitionVisibleFreeformTasks: SparseArray<TaskInfo>,
        postTransitionVisibleFreeformTasks: SparseArray<TaskInfo>
    ) {
        // find new tasks that were added
        postTransitionVisibleFreeformTasks.forEach { taskId, taskInfo ->
            if (!preTransitionVisibleFreeformTasks.containsKey(taskId)) {
                desktopModeEventLogger.logTaskAdded(sessionId, buildTaskUpdateForTask(taskInfo))
            }
        }

        // find old tasks that were removed
        preTransitionVisibleFreeformTasks.forEach { taskId, taskInfo ->
            if (!postTransitionVisibleFreeformTasks.containsKey(taskId)) {
                desktopModeEventLogger.logTaskRemoved(sessionId, buildTaskUpdateForTask(taskInfo))
            }
        }
    }

    // TODO(b/326231724: figure out how to get taskWidth and taskHeight from TaskInfo
    private fun buildTaskUpdateForTask(taskInfo: TaskInfo): TaskUpdate {
        val taskUpdate = TaskUpdate(taskInfo.taskId, taskInfo.userId)
        // add task x, y if available
        taskInfo.positionInParent?.let { taskUpdate.copy(taskX = it.x, taskY = it.y) }

        return taskUpdate
    }

    /** Get [EnterReason] for this session enter */
    private fun getEnterReason(transitionInfo: TransitionInfo): EnterReason =
        when {
            transitionInfo.type == WindowManager.TRANSIT_WAKE -> EnterReason.SCREEN_ON
            transitionInfo.type == Transitions.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP ->
                EnterReason.APP_HANDLE_DRAG
            transitionInfo.type == TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON ->
                EnterReason.APP_HANDLE_MENU_BUTTON
            transitionInfo.type == TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW ->
                EnterReason.APP_FROM_OVERVIEW
            transitionInfo.type == TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT ->
                EnterReason.KEYBOARD_SHORTCUT_ENTER
            // NOTE: the below condition also applies for EnterReason quickswitch
            transitionInfo.type == WindowManager.TRANSIT_TO_FRONT -> EnterReason.OVERVIEW
            // Enter desktop mode from cancelled recents has no transition. Enter is detected on the
            // next transition involving freeform windows.
            // TODO(b/346564416): Modify logging for cancelled recents once it transition is
            //  changed. Also see how to account to time difference between actual enter time and
            //  time of this log. Also account for the missed session when exit happens just after
            //  a cancelled recents.
            wasPreviousTransitionExitToOverview -> EnterReason.OVERVIEW
            transitionInfo.type == WindowManager.TRANSIT_OPEN -> EnterReason.APP_FREEFORM_INTENT
            else -> {
                ProtoLog.w(
                    WM_SHELL_DESKTOP_MODE,
                    "Unknown enter reason for transition type ${transitionInfo.type}",
                    transitionInfo.type
                )
                EnterReason.UNKNOWN_ENTER
            }
        }

    /** Get [ExitReason] for this session exit */
    private fun getExitReason(transitionInfo: TransitionInfo): ExitReason =
         when {
            transitionInfo.type == WindowManager.TRANSIT_SLEEP -> ExitReason.SCREEN_OFF
            transitionInfo.type == WindowManager.TRANSIT_CLOSE -> ExitReason.TASK_FINISHED
            transitionInfo.type == TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG -> ExitReason.DRAG_TO_EXIT
            transitionInfo.type == TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON ->
                ExitReason.APP_HANDLE_MENU_BUTTON_EXIT
            transitionInfo.type == TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT ->
                ExitReason.KEYBOARD_SHORTCUT_EXIT
            transitionInfo.isExitToRecentsTransition() -> ExitReason.RETURN_HOME_OR_OVERVIEW
            else -> {
                ProtoLog.w(
                    WM_SHELL_DESKTOP_MODE,
                    "Unknown exit reason for transition type ${transitionInfo.type}",
                    transitionInfo.type
                )
                ExitReason.UNKNOWN_EXIT
            }
        }

    /** Adds tasks to the saved copy of freeform taskId, taskInfo. Only used for testing. */
    @VisibleForTesting
    fun addTaskInfosToCachedMap(taskInfo: TaskInfo) {
        visibleFreeformTaskInfos.set(taskInfo.taskId, taskInfo)
    }

    @VisibleForTesting fun getLoggerSessionId(): Int? = loggerInstanceId?.id

    @VisibleForTesting
    fun setLoggerSessionId(id: Int) {
        loggerInstanceId = InstanceId.fakeInstanceId(id)
    }

    private fun TransitionInfo.Change.requireTaskInfo(): RunningTaskInfo {
        return this.taskInfo ?: throw IllegalStateException("Expected TaskInfo in the Change")
    }

    private fun TaskInfo.isFreeformWindow(): Boolean {
        return this.windowingMode == WINDOWING_MODE_FREEFORM
    }

    private fun TransitionInfo.isExitToRecentsTransition(): Boolean {
        return this.type == WindowManager.TRANSIT_TO_FRONT &&
            this.flags == WindowManager.TRANSIT_FLAG_IS_RECENTS
    }
}
