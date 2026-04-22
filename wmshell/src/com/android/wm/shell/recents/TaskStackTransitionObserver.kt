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

package com.android.wm.shell.recents

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.os.IBinder
import android.util.ArrayMap
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags.enableShellTopTaskTracking
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_TASK_OBSERVER
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.sysui.ShellCommandHandler
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import dagger.Lazy
import java.io.PrintWriter
import java.util.StringJoiner
import java.util.concurrent.Executor

/**
 * A [Transitions.TransitionObserver] that observes shell transitions, tracks the visible tasks
 * and notifies listeners whenever the visible tasks change (at the start and end of a transition).
 *
 * This can be replaced once we have a generalized task repository tracking visible tasks.
 */
class TaskStackTransitionObserver(
    shellInit: ShellInit,
    private val shellTaskOrganizer: Lazy<ShellTaskOrganizer>,
    private val shellCommandHandler: ShellCommandHandler,
    private val transitions: Lazy<Transitions>,
) : Transitions.TransitionObserver, ShellTaskOrganizer.TaskVanishedListener {

    // List of currently visible tasks sorted in z-order from top-most to bottom-most, only used
    // when Flags.enableShellTopTaskTracking() is enabled.
    private var visibleTasks: MutableList<RunningTaskInfo> = mutableListOf()
    private val pendingCloseTasks: MutableList<RunningTaskInfo> = mutableListOf()
    // Set of listeners to notify when the visible tasks change
    private val taskStackTransitionObserverListeners =
        ArrayMap<TaskStackTransitionObserverListener, Executor>()
    // Used to filter out leaf-tasks
    private val leafTaskFilter: TransitionUtil.LeafTaskFilter = TransitionUtil.LeafTaskFilter()

    init {
        shellInit.addInitCallback(::onInit, this)
    }

    fun onInit() {
        shellTaskOrganizer.get().addTaskVanishedListener(this)
        shellCommandHandler.addDumpCallback(::dump, this)
        transitions.get().registerObserver(this)

        // TODO(346588978): We need to update the running tasks once the ShellTaskOrganizer is
        // registered since there is no existing transition (yet) corresponding for the already
        // visible tasks
    }

    /**
     * This method handles transition ready when only
     * DesktopModeFlags.ENABLE_TASK_STACK_OBSERVER_IN_SHELL is set.
     */
    private fun onDesktopOnlyFlagTransitionReady(info: TransitionInfo) {
        for (change in info.changes) {
            if (change.flags and TransitionInfo.FLAG_IS_WALLPAPER != 0) {
                continue
            }

            val taskInfo = change.taskInfo
            if (taskInfo == null || taskInfo.taskId == -1) {
                continue
            }

            // Find the first task that is opening, this should be the one at the front after
            // the transition
            if (TransitionUtil.isOpeningType(change.mode)) {
                notifyOnTaskMovedToFront(taskInfo)
                break
            } else if (change.mode == TRANSIT_CHANGE) {
                notifyOnTaskChanged(taskInfo)
            }
        }
    }

    /**
     * This method handles transition ready when Flags.enableShellTopTaskTracking() is set.
     */
    private fun onShellTopTaskTrackerFlagTransitionReady(info: TransitionInfo) {
        ProtoLog.v(WM_SHELL_TASK_OBSERVER, "Transition ready: %d", info.debugId)

        // Filter out non-leaf tasks (we will likely need them later, but visible task tracking
        // is currently used only for visible leaf tasks)
        val changesReversed = mutableListOf<TransitionInfo.Change>()
        for (change in info.changes) {
            if (!leafTaskFilter.test(change)) {
                // Not a leaf task
                continue
            }
            changesReversed.add(0, change)
        }

        // We iterate the change list in reverse order because changes are sorted top to bottom and
        // we want to update the lists such that the top most tasks are inserted at the front last
        var notifyChanges = false
        for (change in changesReversed) {
            val taskInfo = change.taskInfo
            if (taskInfo == null || taskInfo.taskId == -1) {
                // Not a valid task
                continue
            }

            if (TransitionUtil.isClosingMode(change.mode)) {
                ProtoLog.v(WM_SHELL_TASK_OBSERVER, "\tClosing task=%d", taskInfo.taskId)

                // Closing task's visibilities are not committed until after the transition
                // completes, so track such tasks so that we can notify on finish
                if (!pendingCloseTasks.any { it.taskId == taskInfo.taskId }) {
                    pendingCloseTasks.add(taskInfo)
                }
            } else if (TransitionUtil.isOpeningMode(change.mode)
                    || TransitionUtil.isOrderOnly(change)) {
                ProtoLog.v(WM_SHELL_TASK_OBSERVER, "\tOpening task=%d", taskInfo.taskId)

                // Remove from pending close tasks list if it's being opened again
                pendingCloseTasks.removeIf { it.taskId == taskInfo.taskId }
                // Move the task to the front of the visible tasks list
                visibleTasks.removeIf { it.taskId == taskInfo.taskId }
                visibleTasks.add(0, taskInfo)
                notifyChanges = true
            }
        }

        // TODO(346588978): We should verify the task list has actually changed before notifying
        //  (ie. starting an activity that's already top-most would result in no visible change)
        if (notifyChanges) {
            updateVisibleTasksList("transition-start")
        }
    }

    private fun updateVisibleTasksList(reason: String) {
        // This simply constructs a list of visible tasks, where the always-on-top tasks are moved
        // to the front of the list in-order, to ensure that they match the visible z order
        val orderedVisibleTasks = mutableListOf<RunningTaskInfo>()
        var numAlwaysOnTop = 0
        for (info in visibleTasks) {
            if (info.windowingMode == WINDOWING_MODE_PINNED
                    || info.configuration.windowConfiguration.isAlwaysOnTop) {
                orderedVisibleTasks.add(numAlwaysOnTop, info)
                numAlwaysOnTop++
            } else {
                orderedVisibleTasks.add(info)
            }
        }
        visibleTasks = orderedVisibleTasks

        dumpVisibleTasks(reason)
        notifyVisibleTasksChanged(visibleTasks)
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction
    ) {
        if (enableShellTopTaskTracking()) {
            onShellTopTaskTrackerFlagTransitionReady(info)
        } else if (DesktopModeFlags.ENABLE_TASK_STACK_OBSERVER_IN_SHELL.isTrue) {
            onDesktopOnlyFlagTransitionReady(info)
        }
    }

    override fun onTransitionStarting(transition: IBinder) {}

    override fun onTransitionMerged(merged: IBinder, playing: IBinder) {}

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
        if (!enableShellTopTaskTracking()) {
            return
        }

        if (pendingCloseTasks.isNotEmpty()) {
            // Update the visible task list based on the pending close tasks
            for (change in pendingCloseTasks) {
                visibleTasks.removeIf {
                    it.taskId == change.taskId
                }
            }
            updateVisibleTasksList("transition-finished")
        }
    }

    override fun onTaskVanished(taskInfo: RunningTaskInfo?) {
        if (!enableShellTopTaskTracking()) {
            return
        }
        ProtoLog.v(WM_SHELL_TASK_OBSERVER, "Task vanished: task=%d", taskInfo?.taskId)
        pendingCloseTasks.removeIf { it.taskId == taskInfo?.taskId }
        if (visibleTasks.any { it.taskId == taskInfo?.taskId }) {
            visibleTasks.removeIf { it.taskId == taskInfo?.taskId }
            updateVisibleTasksList("task-vanished")
        }
    }

    /**
     * Adds a new task stack observer.
     */
    fun addTaskStackTransitionObserverListener(
        taskStackTransitionObserverListener: TaskStackTransitionObserverListener,
        executor: Executor
    ) {
        taskStackTransitionObserverListeners[taskStackTransitionObserverListener] = executor
    }

    /**
     * Removes an existing task stack observer.
     */
    fun removeTaskStackTransitionObserverListener(
        taskStackTransitionObserverListener: TaskStackTransitionObserverListener
    ) {
        taskStackTransitionObserverListeners.remove(taskStackTransitionObserverListener)
    }

    private fun notifyOnTaskMovedToFront(taskInfo: RunningTaskInfo) {
        if (enableShellTopTaskTracking()) {
            return
        }
        taskStackTransitionObserverListeners.forEach { (listener, executor) ->
            executor.execute { listener.onTaskMovedToFrontThroughTransition(taskInfo) }
        }
    }

    private fun notifyOnTaskChanged(taskInfo: RunningTaskInfo) {
        if (enableShellTopTaskTracking()) {
            return
        }
        taskStackTransitionObserverListeners.forEach { (listener, executor) ->
            executor.execute { listener.onTaskChangedThroughTransition(taskInfo) }
        }
    }

    private fun notifyVisibleTasksChanged(visibleTasks: List<RunningTaskInfo>) {
        taskStackTransitionObserverListeners.forEach { (listener, executor) ->
            executor.execute { listener.onVisibleTasksChanged(visibleTasks) }
        }
    }

    fun dump(pw: PrintWriter, prefix: String) {
        pw.println("${prefix}$TAG:")

        if (visibleTasks.isEmpty()) {
            pw.println("$prefix  visibleTasks=[]")
        } else {
            val stringJoiner = StringJoiner(",\n\t", "[\n\t", "\n]")
            visibleTasks.forEach {
                stringJoiner.add("id=${it.taskId} cmp=${it.baseIntent.component}")
            }
            pw.println("$prefix  visibleTasks=$stringJoiner")
        }
    }

    /** Dumps the set of visible tasks to protolog */
    private fun dumpVisibleTasks(reason: String) {
        if (!WM_SHELL_TASK_OBSERVER.isEnabled) {
            return
        }
        ProtoLog.v(WM_SHELL_TASK_OBSERVER, "\tVisible tasks (%s)", reason)
        for (task in visibleTasks) {
            ProtoLog.v(WM_SHELL_TASK_OBSERVER, "\t\ttaskId=%d package=%s", task.taskId,
                task.baseIntent.component?.packageName)
        }
    }

    /** Listener to use to get updates regarding task stack from this observer */
    interface TaskStackTransitionObserverListener {
        /** Called when a task is moved to front. */
        fun onTaskMovedToFrontThroughTransition(taskInfo: RunningTaskInfo) {}
        /** Called when the set of visible tasks have changed. */
        fun onVisibleTasksChanged(visibleTasks: List<RunningTaskInfo>) {}
        /** Called when a task info has changed. */
        fun onTaskChangedThroughTransition(taskInfo: RunningTaskInfo) {}
    }

    companion object {
        const val TAG = "TaskStackTransitionObserver"
    }
}
