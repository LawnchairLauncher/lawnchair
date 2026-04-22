/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.graphics.Rect
import android.graphics.Region
import android.util.ArrayMap
import android.util.ArraySet
import android.util.SparseArray
import android.view.Display.INVALID_DISPLAY
import android.window.WindowContainerToken
import androidx.core.util.forEach
import androidx.core.util.keyIterator
import androidx.core.util.valueIterator
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.util.KtProtoLog
import java.io.PrintWriter
import java.util.concurrent.Executor
import java.util.function.Consumer

/** Keeps track of task data related to desktop mode. */
class DesktopModeTaskRepository {

    /** Task data that is tracked per display */
    private data class DisplayData(
        /**
         * Set of task ids that are marked as active in desktop mode. Active tasks in desktop mode
         * are freeform tasks that are visible or have been visible after desktop mode was
         * activated. Task gets removed from this list when it vanishes. Or when desktop mode is
         * turned off.
         */
        val activeTasks: ArraySet<Int> = ArraySet(),
        val visibleTasks: ArraySet<Int> = ArraySet(),
        val minimizedTasks: ArraySet<Int> = ArraySet(),
        // Tasks currently in freeform mode, ordered from top to bottom (top is at index 0).
        val freeformTasksInZOrder: ArrayList<Int> = ArrayList(),
    )

    // Token of the current wallpaper activity, used to remove it when the last task is removed
    var wallpaperActivityToken: WindowContainerToken? = null
    private val activeTasksListeners = ArraySet<ActiveTasksListener>()
    // Track visible tasks separately because a task may be part of the desktop but not visible.
    private val visibleTasksListeners = ArrayMap<VisibleTasksListener, Executor>()
    // Track corner/caption regions of desktop tasks, used to determine gesture exclusion
    private val desktopExclusionRegions = SparseArray<Region>()
    // Track last bounds of task before toggled to stable bounds
    private val boundsBeforeMaximizeByTaskId = SparseArray<Rect>()
    private var desktopGestureExclusionListener: Consumer<Region>? = null
    private var desktopGestureExclusionExecutor: Executor? = null

    private val displayData =
        object : SparseArray<DisplayData>() {
            /**
             * Get the [DisplayData] associated with this [displayId]
             *
             * Creates a new instance if one does not exist
             */
            fun getOrCreate(displayId: Int): DisplayData {
                if (!contains(displayId)) {
                    put(displayId, DisplayData())
                }
                return get(displayId)
            }
        }

    /** Add a [ActiveTasksListener] to be notified of updates to active tasks in the repository. */
    fun addActiveTaskListener(activeTasksListener: ActiveTasksListener) {
        activeTasksListeners.add(activeTasksListener)
    }

    /** Add a [VisibleTasksListener] to be notified when freeform tasks are visible or not. */
    fun addVisibleTasksListener(visibleTasksListener: VisibleTasksListener, executor: Executor) {
        visibleTasksListeners[visibleTasksListener] = executor
        displayData.keyIterator().forEach { displayId ->
            val visibleTasksCount = getVisibleTaskCount(displayId)
            executor.execute {
                visibleTasksListener.onTasksVisibilityChanged(displayId, visibleTasksCount)
            }
        }
    }

    /**
     * Add a Consumer which will inform other classes of changes to exclusion regions for all
     * Desktop tasks.
     */
    fun setExclusionRegionListener(regionListener: Consumer<Region>, executor: Executor) {
        desktopGestureExclusionListener = regionListener
        desktopGestureExclusionExecutor = executor
        executor.execute {
            desktopGestureExclusionListener?.accept(calculateDesktopExclusionRegion())
        }
    }

    /** Create a new merged region representative of all exclusion regions in all desktop tasks. */
    private fun calculateDesktopExclusionRegion(): Region {
        val desktopExclusionRegion = Region()
        desktopExclusionRegions.valueIterator().forEach { taskExclusionRegion ->
            desktopExclusionRegion.op(taskExclusionRegion, Region.Op.UNION)
        }
        return desktopExclusionRegion
    }

    /** Remove a previously registered [ActiveTasksListener] */
    fun removeActiveTasksListener(activeTasksListener: ActiveTasksListener) {
        activeTasksListeners.remove(activeTasksListener)
    }

    /** Remove a previously registered [VisibleTasksListener] */
    fun removeVisibleTasksListener(visibleTasksListener: VisibleTasksListener) {
        visibleTasksListeners.remove(visibleTasksListener)
    }

    /**
     * Mark a task with given [taskId] as active on given [displayId]
     *
     * @return `true` if the task was not active on given [displayId]
     */
    fun addActiveTask(displayId: Int, taskId: Int): Boolean {
        // Check if task is active on another display, if so, remove it
        displayData.forEach { id, data ->
            if (id != displayId && data.activeTasks.remove(taskId)) {
                activeTasksListeners.onEach { it.onActiveTasksChanged(id) }
            }
        }

        val added = displayData.getOrCreate(displayId).activeTasks.add(taskId)
        if (added) {
            KtProtoLog.d(
                WM_SHELL_DESKTOP_MODE,
                "DesktopTaskRepo: add active task=%d displayId=%d",
                taskId,
                displayId
            )
            activeTasksListeners.onEach { it.onActiveTasksChanged(displayId) }
        }
        return added
    }

    /**
     * Remove task with given [taskId] from active tasks.
     *
     * @return `true` if the task was active
     */
    fun removeActiveTask(taskId: Int): Boolean {
        var result = false
        displayData.forEach { displayId, data ->
            if (data.activeTasks.remove(taskId)) {
                activeTasksListeners.onEach { it.onActiveTasksChanged(displayId) }
                result = true
            }
        }
        if (result) {
            KtProtoLog.d(WM_SHELL_DESKTOP_MODE, "DesktopTaskRepo: remove active task=%d", taskId)
        }
        return result
    }

    /** Check if a task with the given [taskId] was marked as an active task */
    fun isActiveTask(taskId: Int): Boolean {
        return displayData.valueIterator().asSequence().any { data ->
            data.activeTasks.contains(taskId)
        }
    }

    /** Whether a task is visible. */
    fun isVisibleTask(taskId: Int): Boolean {
        return displayData.valueIterator().asSequence().any { data ->
            data.visibleTasks.contains(taskId)
        }
    }

    /** Return whether the given Task is minimized. */
    fun isMinimizedTask(taskId: Int): Boolean {
        return displayData.valueIterator().asSequence().any { data ->
            data.minimizedTasks.contains(taskId)
        }
    }

    /** Check if a task with the given [taskId] is the only active task on its display */
    fun isOnlyActiveTask(taskId: Int): Boolean {
        return displayData.valueIterator().asSequence().any { data ->
            data.activeTasks.singleOrNull() == taskId
        }
    }

    /** Get a set of the active tasks for given [displayId] */
    fun getActiveTasks(displayId: Int): ArraySet<Int> {
        return ArraySet(displayData[displayId]?.activeTasks)
    }

    /**
     * Returns whether Desktop Mode is currently showing any tasks, i.e. whether any Desktop Tasks
     * are visible.
     */
    fun isDesktopModeShowing(displayId: Int): Boolean = getVisibleTaskCount(displayId) > 0

    /**
     * Returns a list of Tasks IDs representing all active non-minimized Tasks on the given display,
     * ordered from front to back.
     */
    fun getActiveNonMinimizedTasksOrderedFrontToBack(displayId: Int): List<Int> {
        val activeTasks = getActiveTasks(displayId)
        val allTasksInZOrder = getFreeformTasksInZOrder(displayId)
        return activeTasks
            // Don't show already minimized Tasks
            .filter { taskId -> !isMinimizedTask(taskId) }
            .sortedBy { taskId -> allTasksInZOrder.indexOf(taskId) }
    }

    /** Get a list of freeform tasks, ordered from top-bottom (top at index 0). */
    fun getFreeformTasksInZOrder(displayId: Int): ArrayList<Int> =
        ArrayList(displayData[displayId]?.freeformTasksInZOrder ?: emptyList())

    /**
     * Updates whether a freeform task with this id is visible or not and notifies listeners.
     *
     * If the task was visible on a different display with a different displayId, it is removed from
     * the set of visible tasks on that display. Listeners will be notified.
     */
    fun updateVisibleFreeformTasks(displayId: Int, taskId: Int, visible: Boolean) {
        if (visible) {
            // Task is visible. Check if we need to remove it from any other display.
            val otherDisplays = displayData.keyIterator().asSequence().filter { it != displayId }
            for (otherDisplayId in otherDisplays) {
                if (displayData[otherDisplayId].visibleTasks.remove(taskId)) {
                    notifyVisibleTaskListeners(
                        otherDisplayId,
                        displayData[otherDisplayId].visibleTasks.size
                    )
                }
            }
        } else if (displayId == INVALID_DISPLAY) {
            // Task has vanished. Check which display to remove the task from.
            displayData.forEach { displayId, data ->
                if (data.visibleTasks.remove(taskId)) {
                    notifyVisibleTaskListeners(displayId, data.visibleTasks.size)
                }
            }
            return
        }

        val prevCount = getVisibleTaskCount(displayId)
        if (visible) {
            displayData.getOrCreate(displayId).visibleTasks.add(taskId)
            unminimizeTask(displayId, taskId)
        } else {
            displayData[displayId]?.visibleTasks?.remove(taskId)
        }
        val newCount = getVisibleTaskCount(displayId)

        // Check if count changed
        if (prevCount != newCount) {
            KtProtoLog.d(
                WM_SHELL_DESKTOP_MODE,
                "DesktopTaskRepo: update task visibility taskId=%d visible=%b displayId=%d",
                taskId,
                visible,
                displayId
            )
            KtProtoLog.d(
                WM_SHELL_DESKTOP_MODE,
                "DesktopTaskRepo: visibleTaskCount has changed from %d to %d",
                prevCount,
                newCount
            )
            notifyVisibleTaskListeners(displayId, newCount)
        }
    }

    private fun notifyVisibleTaskListeners(displayId: Int, visibleTasksCount: Int) {
        visibleTasksListeners.forEach { (listener, executor) ->
            executor.execute { listener.onTasksVisibilityChanged(displayId, visibleTasksCount) }
        }
    }

    /** Get number of tasks that are marked as visible on given [displayId] */
    fun getVisibleTaskCount(displayId: Int): Int {
        KtProtoLog.d(
            WM_SHELL_DESKTOP_MODE,
            "DesktopTaskRepo: visibleTaskCount= %d",
            displayData[displayId]?.visibleTasks?.size ?: 0
        )
        return displayData[displayId]?.visibleTasks?.size ?: 0
    }

    /** Add (or move if it already exists) the task to the top of the ordered list. */
    // TODO(b/342417921): Identify if there is additional checks needed to move tasks for
    // multi-display scenarios.
    fun addOrMoveFreeformTaskToTop(displayId: Int, taskId: Int) {
        KtProtoLog.d(
            WM_SHELL_DESKTOP_MODE,
            "DesktopTaskRepo: add or move task to top: display=%d, taskId=%d",
            displayId,
            taskId
        )
        displayData[displayId]?.freeformTasksInZOrder?.remove(taskId)
        displayData.getOrCreate(displayId).freeformTasksInZOrder.add(0, taskId)
    }

    /** Mark a Task as minimized. */
    fun minimizeTask(displayId: Int, taskId: Int) {
        KtProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeTaskRepository: minimize Task: display=%d, task=%d",
            displayId,
            taskId
        )
        displayData.getOrCreate(displayId).minimizedTasks.add(taskId)
    }

    /** Mark a Task as non-minimized. */
    fun unminimizeTask(displayId: Int, taskId: Int) {
        KtProtoLog.v(
            WM_SHELL_DESKTOP_MODE,
            "DesktopModeTaskRepository: unminimize Task: display=%d, task=%d",
            displayId,
            taskId
        )
        displayData[displayId]?.minimizedTasks?.remove(taskId)
    }

    /** Remove the task from the ordered list. */
    fun removeFreeformTask(displayId: Int, taskId: Int) {
        KtProtoLog.d(
            WM_SHELL_DESKTOP_MODE,
            "DesktopTaskRepo: remove freeform task from ordered list: display=%d, taskId=%d",
            displayId,
            taskId
        )
        displayData[displayId]?.freeformTasksInZOrder?.remove(taskId)
        boundsBeforeMaximizeByTaskId.remove(taskId)
        KtProtoLog.d(
            WM_SHELL_DESKTOP_MODE,
            "DesktopTaskRepo: remaining freeform tasks: %s",
            displayData[displayId]?.freeformTasksInZOrder?.toDumpString() ?: ""
        )
    }

    /**
     * Updates the active desktop gesture exclusion regions; if desktopExclusionRegions has been
     * accepted by desktopGestureExclusionListener, it will be updated in the appropriate classes.
     */
    fun updateTaskExclusionRegions(taskId: Int, taskExclusionRegions: Region) {
        desktopExclusionRegions.put(taskId, taskExclusionRegions)
        desktopGestureExclusionExecutor?.execute {
            desktopGestureExclusionListener?.accept(calculateDesktopExclusionRegion())
        }
    }

    /**
     * Removes the desktop gesture exclusion region for the specified task; if exclusionRegion has
     * been accepted by desktopGestureExclusionListener, it will be updated in the appropriate
     * classes.
     */
    fun removeExclusionRegion(taskId: Int) {
        desktopExclusionRegions.delete(taskId)
        desktopGestureExclusionExecutor?.execute {
            desktopGestureExclusionListener?.accept(calculateDesktopExclusionRegion())
        }
    }

    /** Removes and returns the bounds saved before maximizing the given task. */
    fun removeBoundsBeforeMaximize(taskId: Int): Rect? {
        return boundsBeforeMaximizeByTaskId.removeReturnOld(taskId)
    }

    /** Saves the bounds of the given task before maximizing. */
    fun saveBoundsBeforeMaximize(taskId: Int, bounds: Rect) {
        boundsBeforeMaximizeByTaskId.set(taskId, Rect(bounds))
    }

    internal fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        pw.println("${prefix}DesktopModeTaskRepository")
        dumpDisplayData(pw, innerPrefix)
        pw.println("${innerPrefix}activeTasksListeners=${activeTasksListeners.size}")
        pw.println("${innerPrefix}visibleTasksListeners=${visibleTasksListeners.size}")
    }

    private fun dumpDisplayData(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        displayData.forEach { displayId, data ->
            pw.println("${prefix}Display $displayId:")
            pw.println("${innerPrefix}activeTasks=${data.activeTasks.toDumpString()}")
            pw.println("${innerPrefix}visibleTasks=${data.visibleTasks.toDumpString()}")
            pw.println(
                "${innerPrefix}freeformTasksInZOrder=${data.freeformTasksInZOrder.toDumpString()}"
            )
        }
    }

    /**
     * Defines interface for classes that can listen to changes for active tasks in desktop mode.
     */
    interface ActiveTasksListener {
        /** Called when the active tasks change in desktop mode. */
        fun onActiveTasksChanged(displayId: Int) {}
    }

    /**
     * Defines interface for classes that can listen to changes for visible tasks in desktop mode.
     */
    interface VisibleTasksListener {
        /** Called when the desktop changes the number of visible freeform tasks. */
        fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {}
    }
}

private fun <T> Iterable<T>.toDumpString(): String {
    return joinToString(separator = ", ", prefix = "[", postfix = "]")
}
