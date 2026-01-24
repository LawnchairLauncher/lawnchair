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

import android.app.ActivityManager
import android.graphics.Rect
import android.graphics.Region
import android.util.ArrayMap
import android.util.ArraySet
import android.util.Slog
import android.util.SparseArray
import android.view.Display.INVALID_DISPLAY
import android.window.DesktopExperienceFlags
import android.window.DesktopModeFlags
import android.window.WindowContainerToken
import androidx.core.util.forEach
import androidx.core.util.valueIterator
import com.android.internal.annotations.VisibleForTesting
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.persistence.DesktopPersistentRepository
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.shared.desktopmode.DesktopConfig
import java.io.PrintWriter
import java.util.concurrent.Executor
import java.util.function.Consumer
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tracks desktop data for Android Desktop Windowing. */
class DesktopRepository(
    private val persistentRepository: DesktopPersistentRepository,
    @ShellMainThread private val mainCoroutineScope: CoroutineScope,
    val userId: Int,
    val desktopConfig: DesktopConfig,
) {
    /** A display that supports desktops. */
    private class DesktopDisplay(val displayId: Int) {
        // The set implementation must preserve order.
        val orderedDesks: MutableSet<Desk> = mutableSetOf()
        var activeDeskId: Int? = null
    }

    /** Specific [TaskInfo] data related to top transparent fullscreen task handling. */
    data class TopTransparentFullscreenTaskData(val taskId: Int, val token: WindowContainerToken)

    /**
     * Task data tracked per desk.
     *
     * @property activeTasks task ids of active tasks currently or previously visible in the desk.
     *   Tasks become inactive when task closes or when the desk becomes inactive.
     * @property visibleTasks task ids for active freeform tasks that are currently visible. There
     *   might be other active tasks in a desk that are not visible.
     * @property minimizedTasks task ids for active freeform tasks that are currently minimized.
     * @property closingTasks task ids for tasks that are going to close, but are currently visible.
     * @property freeformTasksInZOrder list of current freeform task ids ordered from top to bottom
     * @property fullImmersiveTaskId the task id of the desk's task that is in full-immersive mode.
     * @property topTransparentFullscreenTaskId the task id of any current top transparent
     *   fullscreen task launched on top of the desk. Cleared when the transparent task is closed or
     *   sent to back. (top is at index 0).
     * @property leftTiledTaskId task id of the task tiled on the left.
     * @property rightTiledTaskId task id of the task tiled on the right.
     */
    private data class Desk(
        val deskId: Int,
        var displayId: Int,
        // TODO: b/421928445 - Refactor these and boundsByTaskId into a new data class.
        val activeTasks: ArraySet<Int> = ArraySet(),
        val visibleTasks: ArraySet<Int> = ArraySet(),
        val minimizedTasks: ArraySet<Int> = ArraySet(),
        // TODO(b/332682201): Remove when the repository state is updated via TransitionObserver
        val closingTasks: ArraySet<Int> = ArraySet(),
        val freeformTasksInZOrder: ArrayList<Int> = ArrayList(),
        var fullImmersiveTaskId: Int? = null,
        var topTransparentFullscreenTaskData: TopTransparentFullscreenTaskData? = null,
        var leftTiledTaskId: Int? = null,
        var rightTiledTaskId: Int? = null,
    ) {
        // TODO: b/417907552 - Add these variables to persistent repository.
        // The display's unique id that will remain the same across reboots.
        var uniqueDisplayId: String? = null
        // Bounds of tasks in this desk mapped to their respective task ids. Used for reconnect.
        var boundsByTaskId: MutableMap<Int, Rect> = mutableMapOf()

        fun deepCopy(): Desk =
            Desk(
                    deskId = deskId,
                    displayId = displayId,
                    activeTasks = ArraySet(activeTasks),
                    visibleTasks = ArraySet(visibleTasks),
                    minimizedTasks = ArraySet(minimizedTasks),
                    closingTasks = ArraySet(closingTasks),
                    freeformTasksInZOrder = ArrayList(freeformTasksInZOrder),
                    fullImmersiveTaskId = fullImmersiveTaskId,
                    topTransparentFullscreenTaskData = topTransparentFullscreenTaskData,
                    leftTiledTaskId = leftTiledTaskId,
                    rightTiledTaskId = rightTiledTaskId,
                )
                .also {
                    it.uniqueDisplayId = uniqueDisplayId
                    it.boundsByTaskId = boundsByTaskId.toMutableMap()
                }

        // TODO: b/362720497 - remove when multi-desktops is enabled where instances aren't
        //  reusable.
        fun clear() {
            activeTasks.clear()
            visibleTasks.clear()
            minimizedTasks.clear()
            closingTasks.clear()
            freeformTasksInZOrder.clear()
            fullImmersiveTaskId = null
            topTransparentFullscreenTaskData = null
            leftTiledTaskId = null
            rightTiledTaskId = null
            boundsByTaskId.clear()
        }
    }

    private val deskChangeListeners = ArrayMap<DeskChangeListener, Executor>()
    private val activeTasksListeners = ArraySet<ActiveTasksListener>()
    private val visibleTasksListeners = ArrayMap<VisibleTasksListener, Executor>()

    /* Tracks corner/caption regions of desktop tasks, used to determine gesture exclusion. */
    private val desktopExclusionRegions = SparseArray<Region>()

    /* Tracks last bounds of task before toggled to stable bounds. */
    private val boundsBeforeMaximizeByTaskId = SparseArray<Rect>()

    /* Tracks last bounds of task before it is minimized. */
    private val boundsBeforeMinimizeByTaskId = SparseArray<Rect>()

    /* Tracks last bounds of task before toggled to immersive state. */
    private val boundsBeforeFullImmersiveByTaskId = SparseArray<Rect>()

    private var desktopGestureExclusionListener: Consumer<Region>? = null
    private var desktopGestureExclusionExecutor: Executor? = null

    // TODO - b/365873835: Add this to persistent repository.
    private val preservedDisplaysByUniqueId = ArrayMap<String, DesktopDisplay>()

    private val desktopData: DesktopData =
        if (DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            MultiDesktopData()
        } else {
            SingleDesktopData()
        }

    /** Adds a listener to be notified of updates about desk changes. */
    fun addDeskChangeListener(listener: DeskChangeListener, executor: Executor) {
        deskChangeListeners[listener] = executor
    }

    /** Adds [activeTasksListener] to be notified of updates to active tasks. */
    fun addActiveTaskListener(activeTasksListener: ActiveTasksListener) {
        activeTasksListeners.add(activeTasksListener)
    }

    /** Adds [visibleTasksListener] to be notified of updates to visible tasks. */
    fun addVisibleTasksListener(visibleTasksListener: VisibleTasksListener, executor: Executor) {
        visibleTasksListeners[visibleTasksListener] = executor
        desktopData
            .desksSequence()
            .groupBy { it.displayId }
            .keys
            .forEach { displayId ->
                val visibleTaskCount = getVisibleTaskCount(displayId)
                executor.execute {
                    visibleTasksListener.onTasksVisibilityChanged(displayId, visibleTaskCount)
                }
            }
    }

    /** Updates tasks changes on all the active task listeners for given display id. */
    private fun updateActiveTasksListeners(displayId: Int) {
        activeTasksListeners.onEach { it.onActiveTasksChanged(displayId) }
    }

    /** Stores the last state of the given display, along with the bounds of the tasks on it. */
    fun preserveDisplay(displayId: Int, uniqueId: String) {
        val orderedDesks = desktopData.getOrderedDesks(displayId)
        // Do not preserve the display if there are no active tasks on it.
        if (!orderedDesks.any { it.activeTasks.isNotEmpty() }) return
        val preservedDisplay = DesktopDisplay(displayId)
        orderedDesks.mapTo(preservedDisplay.orderedDesks) { it.deepCopy() }
        preservedDisplay.activeDeskId = desktopData.getActiveDesk(displayId)?.deskId
        preservedDisplaysByUniqueId[uniqueId] = preservedDisplay
    }

    /** Returns the bounds of all tasks in all desks of the preserved display. */
    @VisibleForTesting
    fun getPreservedTaskBounds(uniqueDisplayId: String): Map<Int, Rect> {
        val combinedBoundsMap = mutableMapOf<Int, Rect>()
        val orderedDesks =
            preservedDisplaysByUniqueId[uniqueDisplayId]?.orderedDesks ?: return emptyMap()
        for (desk in orderedDesks) {
            combinedBoundsMap.putAll(desk.boundsByTaskId)
        }
        return combinedBoundsMap
    }

    @VisibleForTesting
    fun getPreservedDeskIds(uniqueDisplayId: String): List<Int> =
        preservedDisplaysByUniqueId[uniqueDisplayId]?.orderedDesks?.map { it.deskId } ?: emptyList()

    /** Returns a list of all [Desk]s in the repository. */
    private fun desksSequence(): Sequence<Desk> = desktopData.desksSequence()

    /** Returns the number of desks across all displays. */
    fun getNumberOfDesks() = desktopData.getNumberOfDesks()

    /** Returns the number of desks in the given display. */
    fun getNumberOfDesks(displayId: Int) = desktopData.getNumberOfDesks(displayId)

    /** Returns the display the given desk is in. */
    fun getDisplayForDesk(deskId: Int) = desktopData.getDisplayForDesk(deskId)

    /** Adds [regionListener] to inform about changes to exclusion regions for all Desktop tasks. */
    fun setExclusionRegionListener(regionListener: Consumer<Region>, executor: Executor) {
        desktopGestureExclusionListener = regionListener
        desktopGestureExclusionExecutor = executor
        executor.execute {
            desktopGestureExclusionListener?.accept(calculateDesktopExclusionRegion())
        }
    }

    /** Creates a new merged region representative of all exclusion regions in all desktop tasks. */
    private fun calculateDesktopExclusionRegion(): Region {
        val desktopExclusionRegion = Region()
        desktopExclusionRegions.valueIterator().forEach { taskExclusionRegion ->
            desktopExclusionRegion.op(taskExclusionRegion, Region.Op.UNION)
        }
        return desktopExclusionRegion
    }

    /** Removes the previously registered listener. */
    fun removeDeskChangeListener(listener: DeskChangeListener) {
        deskChangeListeners.remove(listener)
    }

    /** Remove the previously registered [activeTasksListener] */
    fun removeActiveTasksListener(activeTasksListener: ActiveTasksListener) {
        activeTasksListeners.remove(activeTasksListener)
    }

    /** Removes the previously registered [visibleTasksListener]. */
    fun removeVisibleTasksListener(visibleTasksListener: VisibleTasksListener) {
        visibleTasksListeners.remove(visibleTasksListener)
    }

    /** Adds the given desk under the given display. */
    fun addDesk(displayId: Int, deskId: Int) {
        logD("addDesk for displayId=%d and deskId=%d", displayId, deskId)
        val couldCreateDesk = canCreateDesks()
        desktopData.createDesk(displayId, deskId)
        val canCreateDesk = canCreateDesks()
        deskChangeListeners.forEach { (listener, executor) ->
            executor.execute {
                listener.onDeskAdded(displayId = displayId, deskId = deskId)
                if (couldCreateDesk != canCreateDesk) {
                    listener.onCanCreateDesksChanged(canCreateDesk)
                }
            }
        }
    }

    /** Update the data to reflect a desk changing displays. */
    fun onDeskDisplayChanged(deskId: Int, newDisplayId: Int) {
        val couldCreateDesk = canCreateDesks()
        val desk =
            desktopData.getDesk(deskId)?.deepCopy()
                ?: error("Expected to find desk with id: $deskId")
        desk.displayId = newDisplayId
        // TODO: b/412484513 - consider de-duping unnecessary updates to listeners, such as the one
        //  made here by |removeDesk| that will be reverted at the end of this method.
        removeDesk(deskId)
        desktopData.addDesk(newDisplayId, desk)
        val canCreateDesk = canCreateDesks()
        deskChangeListeners.forEach { (listener, executor) ->
            executor.execute { listener.onDeskAdded(displayId = newDisplayId, deskId = deskId) }
            if (couldCreateDesk != canCreateDesk) {
                listener.onCanCreateDesksChanged(canCreateDesk)
            }
        }
    }

    /** Removes any references to the removed display. */
    fun removeDisplay(displayId: Int) {
        val deskIdsToRemove =
            getAllDeskIds().filter { deskId -> getDisplayForDesk(deskId) == displayId }
        deskIdsToRemove.forEach { deskId -> removeDesk(deskId) }
        desktopData.removeDisplay(displayId)
    }

    /** Returns the ids of the existing desks in the given display. */
    fun getDeskIds(displayId: Int): Set<Int> =
        desktopData.desksSequence(displayId).map { desk -> desk.deskId }.toSet()

    /** Returns all the ids of all desks in all displays. */
    fun getAllDeskIds(): Set<Int> = desktopData.desksSequence().map { desk -> desk.deskId }.toSet()

    /** Returns the id of the default desk in the given display. */
    fun getDefaultDeskId(displayId: Int): Int? = getDefaultDesk(displayId)?.deskId

    /** Returns the default desk in the given display. */
    private fun getDefaultDesk(displayId: Int): Desk? = desktopData.getDefaultDesk(displayId)

    /** Returns the id of the desk ordered previous to the given one, or null if there isn't one. */
    fun getPreviousDeskId(deskId: Int): Int? {
        val desks = desktopData.getOrderedDesks(getDisplayForDesk(deskId))
        val index = desks.indexOfFirst { it.deskId == deskId }
        if (index <= 0) return null
        return desks[index - 1].deskId
    }

    /** Returns the id of the desk ordered next to the given one, or null if there isn't one. */
    fun getNextDeskId(deskId: Int): Int? {
        val desks = desktopData.getOrderedDesks(getDisplayForDesk(deskId))
        val index = desks.indexOfFirst { it.deskId == deskId }
        return if (index >= 0 && index < desks.size - 1) {
            desks[index + 1].deskId
        } else {
            null
        }
    }

    /** Returns whether the given desk is active in its display. */
    fun isDeskActive(deskId: Int): Boolean =
        desktopData.getAllActiveDesks().any { desk -> desk.deskId == deskId }

    /** Sets the given desk as the active one in the given display. */
    fun setActiveDesk(displayId: Int, deskId: Int) {
        logD("setActiveDesk for displayId=%d and deskId=%d", displayId, deskId)
        val oldActiveDeskId = desktopData.getActiveDesk(displayId)?.deskId ?: INVALID_DESK_ID
        if (deskId == INVALID_DESK_ID || deskId == oldActiveDeskId) {
            return
        }
        desktopData.setActiveDesk(displayId = displayId, deskId = deskId)
        deskChangeListeners.forEach { (listener, executor) ->
            executor.execute {
                listener.onActiveDeskChanged(
                    displayId = displayId,
                    newActiveDeskId = deskId,
                    oldActiveDeskId = oldActiveDeskId,
                )
            }
        }
    }

    /** Sets the given desk as inactive if it was active. */
    fun setDeskInactive(deskId: Int) {
        val displayId = desktopData.getDisplayForDesk(deskId)
        val activeDeskId = desktopData.getActiveDesk(displayId)?.deskId ?: INVALID_DESK_ID
        if (activeDeskId == INVALID_DESK_ID || activeDeskId != deskId) {
            // Desk wasn't active.
            return
        }
        desktopData.setDeskInactive(deskId)
        deskChangeListeners.forEach { (listener, executor) ->
            executor.execute {
                listener.onActiveDeskChanged(
                    displayId = displayId,
                    newActiveDeskId = INVALID_DESK_ID,
                    oldActiveDeskId = deskId,
                )
            }
        }
    }

    fun addLeftTiledTaskToDesk(displayId: Int, taskId: Int, deskId: Int) {
        logD(
            "addLeftTiledTaskToDesk for displayId=%d, taskId=%d, deskId=%d",
            displayId,
            taskId,
            deskId,
        )
        val desk = checkNotNull(desktopData.getDesk(deskId)) { "Did not find desk: $deskId" }
        desk.leftTiledTaskId = taskId
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
            updatePersistentRepositoryForDesk(deskId)
        }
    }

    fun addRightTiledTaskToDesk(displayId: Int, taskId: Int, deskId: Int) {
        logD(
            "addRightTiledTaskToDesk for displayId=%d, taskId=%d, deskId=%d",
            displayId,
            taskId,
            deskId,
        )
        val desk = checkNotNull(desktopData.getDesk(deskId)) { "Did not find desk: $deskId" }
        desk.rightTiledTaskId = taskId
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
            updatePersistentRepositoryForDesk(deskId)
        }
    }

    /** Gets a registered left tiled task to desktop state or returns null. */
    fun getLeftTiledTask(deskId: Int): Int? {
        logD("getLeftTiledTask for deskId=%d", deskId)
        return desktopData.getDesk(deskId)?.leftTiledTaskId
    }

    /** gets a registered right tiled task to desktop state or returns null. */
    fun getRightTiledTask(deskId: Int): Int? {
        logD("getRightTiledTask for deskId=%d", deskId)
        return desktopData.getDesk(deskId)?.rightTiledTaskId
    }

    fun removeLeftTiledTaskFromDesk(displayId: Int, deskId: Int) {
        logD("removeLeftTiledTaskFromDesk for displayId=%d", displayId)
        val desk = desktopData.getDesk(deskId)
        if (desk == null) return
        desk.leftTiledTaskId = null
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
            updatePersistentRepositoryForDesk(deskId)
        }
    }

    fun removeRightTiledTaskFromDesk(displayId: Int, deskId: Int) {
        logD("removeRightTiledTaskFromDesk for displayId=%d", displayId)
        val desk = desktopData.getDesk(deskId)
        if (desk == null) return
        desk.rightTiledTaskId = null
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
            updatePersistentRepositoryForDesk(deskId)
        }
    }

    /** Returns the id of the active desk in the given display, if any. */
    fun getActiveDeskId(displayId: Int): Int? = desktopData.getActiveDesk(displayId)?.deskId

    /** Returns the id of the desk to which this task belongs. */
    fun getDeskIdForTask(taskId: Int): Int? =
        desktopData.desksSequence().find { desk -> desk.activeTasks.contains(taskId) }?.deskId

    /**
     * Adds task with [taskId] to the list of freeform tasks on [displayId]'s active desk.
     *
     * TODO: b/389960283 - add explicit [deskId] argument.
     */
    fun addTask(displayId: Int, taskId: Int, isVisible: Boolean, taskBounds: Rect) {
        logD(
            "addTask for displayId=%d, taskId=%d, isVisible=%b," + "taskBounds=%s",
            displayId,
            taskId,
            isVisible,
            taskBounds,
        )
        val activeDesk =
            checkNotNull(desktopData.getDefaultDesk(displayId)) {
                "Expected desk in display: $displayId"
            }
        addTaskToDesk(
            displayId = displayId,
            deskId = activeDesk.deskId,
            taskId = taskId,
            isVisible = isVisible,
            taskBounds = taskBounds,
        )
    }

    fun addTaskToDesk(
        displayId: Int,
        deskId: Int,
        taskId: Int,
        isVisible: Boolean,
        taskBounds: Rect?,
    ) {
        logD(
            "addTaskToDesk for displayId=%d, deskId=%d, taskId=%d, isVisible=%b, taskBounds=%s",
            displayId,
            deskId,
            taskId,
            isVisible,
            taskBounds,
        )
        if (deskId == taskId) {
            Slog.e(TAG, "Adding desk to itself: deskId=$deskId", Exception())
        }
        addOrMoveTaskToTopOfDesk(displayId = displayId, deskId = deskId, taskId = taskId)
        addActiveTaskToDesk(displayId = displayId, deskId = deskId, taskId = taskId)
        updateTaskInDesk(
            displayId = displayId,
            deskId = deskId,
            taskId = taskId,
            isVisible = isVisible,
            taskBounds = taskBounds,
        )
    }

    private fun addActiveTaskToDesk(displayId: Int, deskId: Int, taskId: Int) {
        val desk = checkNotNull(desktopData.getDesk(deskId)) { "Did not find desk: $deskId" }

        // Removes task if it is active on another desk excluding this desk.
        removeActiveTask(taskId, excludedDeskId = deskId)

        if (desk.activeTasks.add(taskId)) {
            updateActiveTasksListeners(displayId)
        } else {
            logD("Active task=%d already added, displayId=%d, deskId=%d", taskId, displayId, deskId)
        }
    }

    /** Removes task from active task list of desks excluding the [excludedDeskId]. */
    @VisibleForTesting
    fun removeActiveTask(taskId: Int, excludedDeskId: Int? = null) {
        val affectedDisplays = mutableSetOf<Int>()
        desktopData
            .desksSequence()
            .filter { desk -> desk.deskId != excludedDeskId }
            .forEach { desk ->
                val removed = removeActiveTaskFromDesk(desk.deskId, taskId, notifyListeners = false)
                if (removed) {
                    logD(
                        "Removed active task=%d displayId=%d deskId=%d",
                        taskId,
                        desk.displayId,
                        desk.deskId,
                    )
                    affectedDisplays.add(desk.displayId)
                }
            }
        affectedDisplays.forEach { displayId -> updateActiveTasksListeners(displayId) }
    }

    private fun removeActiveTaskFromDesk(
        deskId: Int,
        taskId: Int,
        notifyListeners: Boolean = true,
    ): Boolean {
        logD("removeActiveTaskFromDesk for deskId=%d, taskId=%d", deskId, taskId)
        val desk = desktopData.getDesk(deskId) ?: return false
        if (desk.activeTasks.remove(taskId)) {
            logD("Removed active task=%d from deskId=%d", taskId, desk.deskId)
            if (notifyListeners) {
                updateActiveTasksListeners(desk.displayId)
            }
            return true
        }
        return false
    }

    /** Adds given task to the closing task list of its desk. */
    fun addClosingTask(displayId: Int, deskId: Int?, taskId: Int) {
        val desk =
            deskId?.let { desktopData.getDesk(it) }
                ?: checkNotNull(desktopData.getActiveDesk(displayId)) {
                    "Expected active desk in display: $displayId"
                }
        if (desk.closingTasks.add(taskId)) {
            logD("Added closing task=%d displayId=%d deskId=%d", taskId, displayId, desk.deskId)
        } else {
            // If the task hasn't been removed from closing list after it disappeared.
            logW(
                "Task with taskId=%d displayId=%d deskId=%d is already closing",
                taskId,
                displayId,
                desk.deskId,
            )
        }
    }

    /** Removes task from the list of closing tasks for all desks. */
    fun removeClosingTask(taskId: Int) {
        desktopData.forAllDesks { desk ->
            if (desk.closingTasks.remove(taskId)) {
                logD("Removed closing task=%d deskId=%d", taskId, desk.deskId)
            }
        }
    }

    fun isActiveTask(taskId: Int) = desksSequence().any { taskId in it.activeTasks }

    @VisibleForTesting
    fun isActiveTaskInDesk(taskId: Int, deskId: Int): Boolean {
        val desk = desktopData.getDesk(deskId) ?: return false
        return taskId in desk.activeTasks
    }

    fun isClosingTask(taskId: Int) = desksSequence().any { taskId in it.closingTasks }

    fun isVisibleTask(taskId: Int) = desksSequence().any { taskId in it.visibleTasks }

    @VisibleForTesting
    fun isVisibleTaskInDesk(taskId: Int, deskId: Int): Boolean {
        val desk = desktopData.getDesk(deskId) ?: return false
        return taskId in desk.visibleTasks
    }

    fun isMinimizedTask(taskId: Int) = desksSequence().any { taskId in it.minimizedTasks }

    /**
     * Checks if a task is the only visible, non-closing, non-minimized task on the active desk of
     * the given display, or any display's active desk if [displayId] is [INVALID_DISPLAY].
     *
     * TODO: b/389960283 - consider forcing callers to use [isOnlyVisibleNonClosingTaskInDesk] with
     *   an explicit desk id instead of using this function and defaulting to the active one.
     */
    fun isOnlyVisibleNonClosingTask(taskId: Int, displayId: Int = INVALID_DISPLAY): Boolean {
        val activeDesks =
            if (displayId != INVALID_DISPLAY) {
                setOfNotNull(desktopData.getActiveDesk(displayId))
            } else {
                desktopData.getAllActiveDesks()
            }
        return activeDesks.any { desk ->
            isOnlyVisibleNonClosingTaskInDesk(
                taskId = taskId,
                deskId = desk.deskId,
                displayId = desk.displayId,
            )
        }
    }

    /**
     * Checks if a task is the only visible, non-closing, non-minimized task on the given desk of
     * the given display.
     */
    fun isOnlyVisibleNonClosingTaskInDesk(taskId: Int, deskId: Int, displayId: Int): Boolean {
        val desk = desktopData.getDesk(deskId) ?: return false
        return desk.visibleTasks
            .subtract(desk.closingTasks)
            .subtract(desk.minimizedTasks)
            .singleOrNull() == taskId
    }

    /** Whether the task is the only visible desktop task in the display. */
    fun isOnlyVisibleTask(taskId: Int, displayId: Int): Boolean {
        val desk = desktopData.getActiveDesk(displayId) ?: return false
        return isOnlyVisibleTaskInDesk(taskId = taskId, deskId = desk.deskId)
    }

    /** Whether the task is the only visible task in the desk. */
    fun isOnlyVisibleTaskInDesk(taskId: Int, deskId: Int): Boolean {
        val desk = desktopData.getDesk(deskId) ?: return false
        return desk.visibleTasks.size == 1 && desk.visibleTasks.single() == taskId
    }

    /** Whether the display has only one visible desktop task. */
    fun hasOnlyOneVisibleTask(displayId: Int): Boolean = getVisibleTaskCount(displayId) == 1

    /** Get all taskIds of the active desktop tasks in the given display. */
    fun getActiveTasks(displayId: Int): ArraySet<Int> =
        ArraySet(desktopData.getActiveDesk(displayId)?.activeTasks)

    /**
     * Returns the minimized tasks in the given display's active desk.
     *
     * TODO: b/389960283 - migrate callers to [getMinimizedTaskIdsInDesk].
     */
    fun getMinimizedTasks(displayId: Int): ArraySet<Int> =
        ArraySet(desktopData.getActiveDesk(displayId)?.minimizedTasks)

    @VisibleForTesting
    fun getMinimizedTaskIdsInDesk(deskId: Int): ArraySet<Int> =
        ArraySet(desktopData.getDesk(deskId)?.minimizedTasks)

    /**
     * Returns all active non-minimized tasks for [displayId] ordered from top to bottom.
     *
     * TODO: b/389960283 - migrate callers to [getExpandedTasksIdsInDeskOrdered].
     */
    fun getExpandedTasksOrdered(displayId: Int): List<Int> =
        getFreeformTasksInZOrder(displayId).filter { !isMinimizedTask(it) }

    /** Returns all active non-minimized tasks for [deskId] ordered from top to bottom. */
    fun getExpandedTasksIdsInDeskOrdered(deskId: Int): List<Int> =
        getFreeformTasksIdsInDeskInZOrder(deskId).filter { !isMinimizedTask(it) }

    /**
     * Returns the count of active non-minimized tasks for [displayId].
     *
     * TODO: b/389960283 - add explicit [deskId] argument.
     */
    fun getExpandedTaskCount(displayId: Int): Int {
        return getActiveTasks(displayId).count { !isMinimizedTask(it) }
    }

    /**
     * Returns a list of freeform tasks, ordered from top-bottom (top at index 0).
     *
     * TODO: b/389960283 - migrate callers to [getFreeformTasksIdsInDeskInZOrder].
     */
    @VisibleForTesting
    fun getFreeformTasksInZOrder(displayId: Int): ArrayList<Int> =
        ArrayList(desktopData.getDefaultDesk(displayId)?.freeformTasksInZOrder ?: emptyList())

    @VisibleForTesting
    fun getFreeformTasksIdsInDeskInZOrder(deskId: Int): ArrayList<Int> =
        ArrayList(desktopData.getDesk(deskId)?.freeformTasksInZOrder ?: emptyList())

    /** Returns the tasks inside the given desk. */
    fun getActiveTaskIdsInDesk(deskId: Int): Set<Int> =
        desktopData.getDesk(deskId)?.activeTasks?.toSet()
            ?: run {
                logW("getTasksInDesk: could not find desk: deskId=%d", deskId)
                emptySet()
            }

    /** Removes task from visible tasks of all desks except [excludedDeskId]. */
    private fun removeVisibleTask(taskId: Int, excludedDeskId: Int? = null) {
        desktopData.forAllDesks { _, desk ->
            if (desk.deskId != excludedDeskId) {
                removeVisibleTaskFromDesk(deskId = desk.deskId, taskId = taskId)
            }
        }
    }

    private fun removeVisibleTaskFromDesk(deskId: Int, taskId: Int) {
        val desk = desktopData.getDesk(deskId) ?: return
        if (desk.visibleTasks.remove(taskId)) {
            notifyVisibleTaskListeners(desk.displayId, desk.visibleTasks.size)
        }
    }

    /**
     * Updates visibility of a freeform task with [taskId] on [displayId] and notifies listeners.
     *
     * If task was visible on a different display with a different [displayId], removes from the set
     * of visible tasks on that display and notifies listeners.
     *
     * TODO: b/389960283 - add explicit [deskId] argument.
     */
    fun updateTask(displayId: Int, taskId: Int, isVisible: Boolean, taskBounds: Rect?) {
        val validDisplayId =
            if (displayId == INVALID_DISPLAY) {
                // When a task vanishes it doesn't have a displayId. Find the display of the task.
                getDisplayIdForTask(taskId)
            } else {
                displayId
            }
        if (validDisplayId == null) {
            logW("No display id found for task: taskId=%d", taskId)
            return
        }
        val desk =
            checkNotNull(desktopData.getDefaultDesk(validDisplayId)) {
                "Expected a desk in display: $validDisplayId"
            }
        updateTaskInDesk(
            displayId = validDisplayId,
            deskId = desk.deskId,
            taskId = taskId,
            isVisible = isVisible,
            taskBounds = taskBounds,
        )
    }

    private fun updateTaskInDesk(
        displayId: Int,
        deskId: Int,
        taskId: Int,
        isVisible: Boolean,
        taskBounds: Rect?,
    ) {
        check(displayId != INVALID_DISPLAY) { "Display must be valid" }
        logD(
            "updateTaskInDesk taskId=%d, deskId=%d, displayId=%d, isVisible=%b, taskBounds=%s",
            taskId,
            deskId,
            displayId,
            isVisible,
            taskBounds,
        )

        if (isVisible) {
            // If task is visible, remove it from any other desk besides [deskId].
            removeVisibleTask(taskId, excludedDeskId = deskId)
        }
        val desk = checkNotNull(desktopData.getDesk(deskId)) { "Did not find desk: $deskId" }
        val prevCount = getVisibleTaskCountInDesk(deskId)
        if (isVisible) {
            desk.visibleTasks.add(taskId)
            unminimizeTask(displayId, taskId)
        } else {
            desk.visibleTasks.remove(taskId)
        }
        taskBounds?.let { desk.boundsByTaskId[taskId] = it }
        val newCount = getVisibleTaskCountInDesk(deskId)
        if (prevCount != newCount) {
            logD(
                "VisibleTaskCount in deskId=%d has changed from %d to %d, visible tasks=%s",
                deskId,
                prevCount,
                newCount,
                desk.visibleTasks,
            )
            notifyVisibleTaskListeners(displayId, newCount)
            if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
                updatePersistentRepository(displayId)
            }
        }
    }

    /**
     * Set whether the given task is the full-immersive task in this display's active desk.
     *
     * TODO: b/389960283 - consider forcing callers to use [setTaskInFullImmersiveStateInDesk] with
     *   an explicit desk id instead of using this function and defaulting to the active one.
     */
    fun setTaskInFullImmersiveState(displayId: Int, taskId: Int, immersive: Boolean) {
        val activeDesk = desktopData.getActiveDesk(displayId) ?: return
        setTaskInFullImmersiveStateInDesk(
            deskId = activeDesk.deskId,
            taskId = taskId,
            immersive = immersive,
        )
    }

    /** Sets whether the given task is the full-immersive task in the given desk. */
    fun setTaskInFullImmersiveStateInDesk(deskId: Int, taskId: Int, immersive: Boolean) {
        val desk = desktopData.getDesk(deskId) ?: return
        if (immersive) {
            desk.fullImmersiveTaskId = taskId
        } else {
            if (desk.fullImmersiveTaskId == taskId) {
                desk.fullImmersiveTaskId = null
            }
        }
    }

    /* Whether the task is in full-immersive state. */
    fun isTaskInFullImmersiveState(taskId: Int): Boolean {
        return desksSequence().any { taskId == it.fullImmersiveTaskId }
    }

    /**
     * Returns the task that is currently in immersive mode in this display, or null.
     *
     * TODO: b/389960283 - add explicit [deskId] argument.
     */
    fun getTaskInFullImmersiveState(displayId: Int): Int? =
        desktopData.getActiveDesk(displayId)?.fullImmersiveTaskId

    /** Sets the top transparent fullscreen task data for a given desk. */
    fun setTopTransparentFullscreenTaskData(deskId: Int, task: ActivityManager.RunningTaskInfo) {
        logD(
            "Top transparent fullscreen task set for desk: taskId=%d, deskId=%d",
            task.taskId,
            deskId,
        )
        desktopData.getDesk(deskId)?.topTransparentFullscreenTaskData =
            TopTransparentFullscreenTaskData(task.taskId, task.token)
    }

    /** Returns the top transparent fullscreen task data for a given desk, or null. */
    fun getTopTransparentFullscreenTaskData(deskId: Int): TopTransparentFullscreenTaskData? =
        desktopData.getDesk(deskId)?.topTransparentFullscreenTaskData

    /** Clears the top transparent fullscreen task data for a given desk. */
    fun clearTopTransparentFullscreenTaskData(deskId: Int) {
        logD(
            "Top transparent fullscreen task cleared for desk: taskId=%d, deskId=%d",
            desktopData.getDesk(deskId)?.topTransparentFullscreenTaskData?.taskId,
            deskId,
        )
        desktopData.getDesk(deskId)?.topTransparentFullscreenTaskData = null
    }

    @VisibleForTesting
    public fun notifyVisibleTaskListeners(displayId: Int, visibleTasksCount: Int) {
        visibleTasksListeners.forEach { (listener, executor) ->
            executor.execute { listener.onTasksVisibilityChanged(displayId, visibleTasksCount) }
        }
    }

    /** Whether the display is currently showing any desk. */
    fun isAnyDeskActive(displayId: Int): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) {
            val desk = desktopData.getDefaultDesk(displayId)
            if (desk == null) {
                logE("Could not find default desk for display: $displayId")
                return false
            }
            val hasVisibleTasks = desk.visibleTasks.isNotEmpty()
            val hasTopTransparentFullscreenTask =
                getTopTransparentFullscreenTaskData(desk.deskId) != null
            if (
                DesktopModeFlags.INCLUDE_TOP_TRANSPARENT_FULLSCREEN_TASK_IN_DESKTOP_HEURISTIC
                    .isTrue && DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_MODALS_POLICY.isTrue
            ) {
                logD(
                    "isAnyDeskActive: hasVisibleTasks=%s hasTopTransparentFullscreenTask=%s",
                    hasVisibleTasks,
                    hasTopTransparentFullscreenTask,
                )
                return hasVisibleTasks || hasTopTransparentFullscreenTask
            }
            logD("isAnyDeskActive: hasVisibleTasks=%s", hasVisibleTasks)
            return hasVisibleTasks
        }
        return desktopData.getActiveDesk(displayId) != null
    }

    /** Gets number of visible freeform tasks on given [displayId]'s active desk. */
    @Deprecated("Use isAnyDeskActive() instead.", ReplaceWith("isAnyDeskActive()"))
    @VisibleForTesting
    fun getVisibleTaskCount(displayId: Int): Int =
        (desktopData.getActiveDesk(displayId)?.visibleTasks?.size ?: 0).also {
            logD("getVisibleTaskCount=$it")
        }

    /** Gets the number of visible tasks on the given desk. */
    private fun getVisibleTaskCountInDesk(deskId: Int): Int =
        desktopData.getDesk(deskId)?.visibleTasks?.size ?: 0

    /**
     * Adds task (or moves if it already exists) to the top of the ordered list.
     *
     * Unminimizes the task if it is minimized.
     */
    private fun addOrMoveTaskToTopOfDesk(displayId: Int, deskId: Int, taskId: Int) {
        val desk = desktopData.getDesk(deskId) ?: error("Could not find desk: $deskId")
        val bounds = Rect()
        desktopData.forAllDesks { _, desk1 ->
            desk1.freeformTasksInZOrder.remove(taskId)
            desk1.boundsByTaskId[taskId]?.let { bounds.set(it) }
        }
        desk.freeformTasksInZOrder.add(0, taskId)
        if (!bounds.isEmpty) desk.boundsByTaskId[taskId] = bounds
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
            // TODO: can probably just update the desk.
            updatePersistentRepository(displayId)
        }
    }

    /**
     * Minimizes the task for [taskId] and [displayId]'s active display.
     *
     * TODO: b/389960283 - consider forcing callers to use [minimizeTaskInDesk] with an explicit
     *   desk id instead of using this function and defaulting to the active one.
     */
    fun minimizeTask(displayId: Int, taskId: Int) {
        if (displayId == INVALID_DISPLAY) {
            // When a task vanishes it doesn't have a displayId. Find the display of the task and
            // mark it as minimized.
            getDisplayIdForTask(taskId)?.let { minimizeTask(it, taskId) }
                ?: logW("Minimize task: No display id found for task: taskId=%d", taskId)
            return
        }
        val deskId = desktopData.getActiveDesk(displayId)?.deskId
        if (deskId == null) {
            logD("Minimize task: No active desk found for task: taskId=%d", taskId)
            return
        }
        minimizeTaskInDesk(displayId, deskId, taskId)
    }

    /** Minimizes the task in its desk. */
    fun minimizeTaskInDesk(displayId: Int, deskId: Int, taskId: Int) {
        logD("MinimizeTaskInDesk: displayId=%d deskId=%d, task=%d", displayId, deskId, taskId)
        desktopData.getDesk(deskId)?.minimizedTasks?.add(taskId)
            ?: logD("Minimize task: No active desk found for task: taskId=%d", taskId)
        updateTaskInDesk(displayId, deskId, taskId, isVisible = false, taskBounds = null)
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue()) {
            updatePersistentRepositoryForDesk(deskId)
        }
    }

    /**
     * Unminimizes the task for [taskId] and [displayId].
     *
     * TODO: b/389960283 - consider using [unminimizeTaskFromDesk] instead.
     */
    fun unminimizeTask(displayId: Int, taskId: Int) {
        desktopData.forAllDesks(displayId) { desk -> unminimizeTaskFromDesk(desk.deskId, taskId) }
    }

    private fun unminimizeTaskFromDesk(deskId: Int, taskId: Int) {
        logD("Unminimize Task from desk: deskId=%d, taskId=%d", deskId, taskId)
        if (desktopData.getDesk(deskId)?.minimizedTasks?.remove(taskId) != true) {
            logW("Unminimize Task: deskId=%d, taskId=%d, no task data", deskId, taskId)
        }
    }

    private fun getDisplayIdForTask(taskId: Int): Int? {
        var displayForTask: Int? = null
        desktopData.forAllDesks { displayId, desk ->
            if (taskId in desk.activeTasks) {
                displayForTask = displayId
            }
        }
        if (displayForTask == null) {
            logW("No display id found for task: taskId=%d", taskId)
        }
        return displayForTask
    }

    /**
     * Removes [taskId] from the respective display. If [INVALID_DISPLAY], the original display id
     * will be looked up from the task id.
     *
     * TODO: b/389960283 - consider using [removeTaskFromDesk] instead.
     */
    fun removeTask(taskId: Int) {
        logD("Removes freeform task: taskId=%d", taskId)
        // Removes the original display id of the task.
        getDisplayIdForTask(taskId)?.let { removeTaskFromDisplay(it, taskId) }
    }

    /** Removes given task from a valid [displayId] and updates the repository state. */
    private fun removeTaskFromDisplay(displayId: Int, taskId: Int) {
        logD("Removes freeform task: taskId=%d, displayId=%d", taskId, displayId)
        desktopData.forAllDesks(displayId) { desk ->
            removeTaskFromDesk(deskId = desk.deskId, taskId = taskId)
        }
    }

    /** Removes the given task from the given desk. */
    fun removeTaskFromDesk(deskId: Int, taskId: Int) {
        logD("removeTaskFromDesk: deskId=%d, taskId=%d", deskId, taskId)
        // TODO: b/362720497 - consider not clearing bounds on any removal, such as when moving
        //  it between desks. It might be better to allow restoring to the previous bounds as long
        //  as they're valid (probably valid if in the same display).
        boundsBeforeMaximizeByTaskId.remove(taskId)
        boundsBeforeFullImmersiveByTaskId.remove(taskId)
        val desk = desktopData.getDesk(deskId) ?: return
        if (desk.freeformTasksInZOrder.remove(taskId)) {
            desk.boundsByTaskId.remove(taskId)
            logD(
                "Remaining freeform tasks in desk: %d, tasks: %s",
                desk.deskId,
                desk.freeformTasksInZOrder.toDumpString(),
            )
        }
        unminimizeTaskFromDesk(deskId, taskId)
        // Mark task as not in immersive if it was immersive.
        setTaskInFullImmersiveStateInDesk(deskId = deskId, taskId = taskId, immersive = false)
        removeActiveTaskFromDesk(deskId = deskId, taskId = taskId)
        removeVisibleTaskFromDesk(deskId = deskId, taskId = taskId)
        if (DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue) {
            updatePersistentRepositoryForDesk(desk.deskId)
        }
    }

    /** Removes the given desk and returns the active tasks in that desk. */
    fun removeDesk(deskId: Int): Set<Int> {
        logD("removeDesk %d", deskId)
        val couldCreateDesks = canCreateDesks()
        val desk =
            desktopData.getDesk(deskId)
                ?: return emptySet<Int>().also {
                    logW("Could not find desk to remove: deskId=%d", deskId)
                }
        val wasActive = desktopData.getActiveDesk(desk.displayId)?.deskId == desk.deskId
        val activeTasks = ArraySet(desk.activeTasks)
        desktopData.remove(desk.deskId)
        val canCreateDesks = canCreateDesks()
        notifyVisibleTaskListeners(desk.displayId, getVisibleTaskCount(displayId = desk.displayId))
        deskChangeListeners.forEach { (listener, executor) ->
            executor.execute {
                if (wasActive) {
                    listener.onActiveDeskChanged(
                        displayId = desk.displayId,
                        newActiveDeskId = INVALID_DESK_ID,
                        oldActiveDeskId = desk.deskId,
                    )
                }
                listener.onDeskRemoved(displayId = desk.displayId, deskId = desk.deskId)
                if (couldCreateDesks != canCreateDesks) {
                    listener.onCanCreateDesksChanged(canCreateDesks)
                }
            }
        }
        if (
            DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_PERSISTENCE.isTrue &&
                DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue
        ) {
            removeDeskFromPersistentRepository(desk)
        }
        return activeTasks
    }

    /**
     * Updates active desktop gesture exclusion regions.
     *
     * If [desktopExclusionRegions] is accepted by [desktopGestureExclusionListener], updates it in
     * appropriate classes.
     */
    fun updateTaskExclusionRegions(taskId: Int, taskExclusionRegions: Region) {
        desktopExclusionRegions.put(taskId, taskExclusionRegions)
        desktopGestureExclusionExecutor?.execute {
            desktopGestureExclusionListener?.accept(calculateDesktopExclusionRegion())
        }
    }

    /**
     * Removes desktop gesture exclusion region for the specified task.
     *
     * If [desktopExclusionRegions] is accepted by [desktopGestureExclusionListener], updates it in
     * appropriate classes.
     */
    fun removeExclusionRegion(taskId: Int) {
        desktopExclusionRegions.delete(taskId)
        desktopGestureExclusionExecutor?.execute {
            desktopGestureExclusionListener?.accept(calculateDesktopExclusionRegion())
        }
    }

    /** Removes and returns the bounds saved before maximizing the given task. */
    fun removeBoundsBeforeMaximize(taskId: Int): Rect? =
        boundsBeforeMaximizeByTaskId.removeReturnOld(taskId)

    /** Saves the bounds of the given task before maximizing. */
    fun saveBoundsBeforeMaximize(taskId: Int, bounds: Rect) =
        boundsBeforeMaximizeByTaskId.set(taskId, Rect(bounds))

    /** Removes and returns the bounds saved before minimizing the given task. */
    fun removeBoundsBeforeMinimize(taskId: Int): Rect? =
        boundsBeforeMinimizeByTaskId.removeReturnOld(taskId)

    /** Saves the bounds of the given task before minimizing. */
    fun saveBoundsBeforeMinimize(taskId: Int, bounds: Rect?) =
        boundsBeforeMinimizeByTaskId.set(taskId, Rect(bounds))

    /** Removes and returns the bounds saved before entering immersive with the given task. */
    fun removeBoundsBeforeFullImmersive(taskId: Int): Rect? =
        boundsBeforeFullImmersiveByTaskId.removeReturnOld(taskId)

    /** Saves the bounds of the given task before entering immersive. */
    fun saveBoundsBeforeFullImmersive(taskId: Int, bounds: Rect) =
        boundsBeforeFullImmersiveByTaskId.set(taskId, Rect(bounds))

    /** Returns the current state of the desktop, formatted for usage by remote clients. */
    fun getDeskDisplayStateForRemote(): Array<DisplayDeskState> =
        desktopData
            .desksSequence()
            .groupBy { it.displayId }
            .map { (displayId, desks) ->
                val activeDeskId = desktopData.getActiveDesk(displayId)?.deskId
                DisplayDeskState().apply {
                    this.displayId = displayId
                    this.activeDeskId = activeDeskId ?: INVALID_DESK_ID
                    this.deskIds = desks.map { it.deskId }.toIntArray()
                }
            }
            .toTypedArray()

    /** TODO: b/389960283 - consider updating only the changing desks. */
    private fun updatePersistentRepository(displayId: Int) {
        val desks = desktopData.desksSequence(displayId).map { desk -> desk.deepCopy() }.toList()
        mainCoroutineScope.launch {
            desks.forEach { desk -> updatePersistentRepositoryForDesk(desk) }
        }
    }

    private fun updatePersistentRepositoryForDesk(deskId: Int) {
        val desk = desktopData.getDesk(deskId)?.deepCopy() ?: return
        mainCoroutineScope.launch { updatePersistentRepositoryForDesk(desk) }
    }

    private suspend fun updatePersistentRepositoryForDesk(desk: Desk) {
        try {
            persistentRepository.addOrUpdateDesktop(
                userId = userId,
                desktopId = desk.deskId,
                visibleTasks = desk.visibleTasks,
                minimizedTasks = desk.minimizedTasks,
                freeformTasksInZOrder = desk.freeformTasksInZOrder,
                leftTiledTask = desk.leftTiledTaskId,
                rightTiledTask = desk.rightTiledTaskId,
            )
        } catch (exception: Exception) {
            logE(
                "An exception occurred while updating the persistent repository \n%s",
                exception.stackTrace,
            )
        }
    }

    private fun removeDeskFromPersistentRepository(desk: Desk) {
        mainCoroutineScope.launch {
            try {
                logD(
                    "updatePersistentRepositoryForRemovedDesk user=%d desk=%d",
                    userId,
                    desk.deskId,
                )
                persistentRepository.removeDesktop(userId = userId, desktopId = desk.deskId)
            } catch (throwable: Throwable) {
                logE(
                    "An exception occurred while updating the persistent repository \n%s",
                    throwable.stackTrace,
                )
            }
        }
    }

    private fun canCreateDesks(): Boolean {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return false
        val deskLimit = desktopConfig.maxDeskLimit
        if (deskLimit == 0) return true
        return deskLimit > desktopData.getNumberOfDesks()
    }

    internal fun dump(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        pw.println("${prefix}DesktopRepository")
        pw.println("${innerPrefix}userId=$userId")
        dumpDesktopTaskData(pw, innerPrefix)
        pw.println("${innerPrefix}activeTasksListeners=${activeTasksListeners.size}")
        pw.println("${innerPrefix}visibleTasksListeners=${visibleTasksListeners.size}")
    }

    private fun dumpDesktopTaskData(pw: PrintWriter, prefix: String) {
        val innerPrefix = "$prefix  "
        desktopData
            .desksSequence()
            .groupBy { it.displayId }
            .map { (displayId, desks) ->
                Triple(displayId, desktopData.getActiveDesk(displayId)?.deskId, desks)
            }
            .forEach { (displayId, activeDeskId, desks) ->
                pw.println("${prefix}Display #$displayId:")
                pw.println("${innerPrefix}numOfDesks=${desks.size}")
                pw.println("${innerPrefix}activeDesk=$activeDeskId")
                pw.println("${innerPrefix}desks:")
                val desksPrefix = "$innerPrefix  "
                desks.forEach { desk ->
                    pw.println("${desksPrefix}Desk #${desk.deskId}:")
                    pw.print("$desksPrefix  activeTasks=")
                    pw.println(desk.activeTasks.toDumpString())
                    pw.print("$desksPrefix  visibleTasks=")
                    pw.println(desk.visibleTasks.toDumpString())
                    pw.print("$desksPrefix  freeformTasksInZOrder=")
                    pw.println(desk.freeformTasksInZOrder.toDumpString())
                    pw.print("$desksPrefix  minimizedTasks=")
                    pw.println(desk.minimizedTasks.toDumpString())
                    pw.print("$desksPrefix  fullImmersiveTaskId=")
                    pw.println(desk.fullImmersiveTaskId)
                    pw.print("$desksPrefix  topTransparentFullscreenTaskData=")
                    pw.println(desk.topTransparentFullscreenTaskData)
                }
            }
    }

    /** Listens to changes of desks state. */
    interface DeskChangeListener {
        /** Called when a new desk is added to a display. */
        fun onDeskAdded(displayId: Int, deskId: Int)

        /** Called when a desk is removed from a display. */
        fun onDeskRemoved(displayId: Int, deskId: Int)

        /** Called when the active desk in a display has changed. */
        fun onActiveDeskChanged(displayId: Int, newActiveDeskId: Int, oldActiveDeskId: Int)

        /** Called when the conditions that allow the creation of a new desk change. */
        fun onCanCreateDesksChanged(canCreateDesks: Boolean)
    }

    /** Listens to changes for active tasks in desktop mode. */
    interface ActiveTasksListener {
        fun onActiveTasksChanged(displayId: Int) {}
    }

    /** Listens to changes for visible tasks in desktop mode. */
    interface VisibleTasksListener {
        fun onTasksVisibilityChanged(displayId: Int, visibleTasksCount: Int) {}
    }

    /** An interface for the desktop hierarchy's data managed by this repository. */
    private interface DesktopData {
        /** Creates a desk record. */
        fun createDesk(displayId: Int, deskId: Int)

        /** Adds an existing desk to a specific display */
        fun addDesk(displayId: Int, desk: Desk)

        /** Returns the desk with the given id, or null if it does not exist. */
        fun getDesk(deskId: Int): Desk?

        /** Returns the active desk in this display, or null if none are active. */
        fun getActiveDesk(displayId: Int): Desk?

        /** Sets the given desk as the active desk in the given display. */
        fun setActiveDesk(displayId: Int, deskId: Int)

        /** Sets the desk as inactive if it was active. */
        fun setDeskInactive(deskId: Int)

        /**
         * Returns the default desk in the given display. Useful when the system wants to activate a
         * desk but doesn't care about which one it activates (e.g. when putting a window into a
         * desk using the App Handle). May return null if the display does not support desks.
         *
         * TODO: 389787966 - consider removing or renaming. In practice, this is needed for
         *   soon-to-be deprecated IDesktopMode APIs, adb commands or entry-points into the only
         *   desk (single-desk devices) or the most-recent desk (multi-desk devices).
         */
        fun getDefaultDesk(displayId: Int): Desk?

        /** Returns all the active desks of all displays. */
        fun getAllActiveDesks(): Set<Desk>

        /** Returns the number of desks across all displays. */
        fun getNumberOfDesks(): Int

        /** Returns the number of desks in the given display. */
        fun getNumberOfDesks(displayId: Int): Int

        /** Returns a list of ordered desks in a given display. */
        fun getOrderedDesks(displayId: Int): List<Desk>

        /** Applies a function to all desks. */
        fun forAllDesks(consumer: (Desk) -> Unit)

        /** Applies a function to all desks. */
        fun forAllDesks(consumer: (displayId: Int, Desk) -> Unit)

        /** Applies a function to all desks under the given display. */
        fun forAllDesks(displayId: Int, consumer: (Desk) -> Unit)

        /** Returns a sequence of all desks. */
        fun desksSequence(): Sequence<Desk>

        /** Returns a sequence of all desks under the given display. */
        fun desksSequence(displayId: Int): Sequence<Desk>

        /** Remove an existing desk if it exists. */
        fun remove(deskId: Int)

        /** Returns the id of the display where the given desk is located. */
        fun getDisplayForDesk(deskId: Int): Int

        /** Perform needed cleanup when a display is removed. */
        fun removeDisplay(displayId: Int)
    }

    /**
     * A [DesktopData] implementation that only supports one desk per display.
     *
     * Internally, it reuses the displayId as that display's single desk's id. It also never truly
     * "removes" a desk, it just clears its content.
     */
    private class SingleDesktopData : DesktopData {
        private val deskByDisplayId =
            object : SparseArray<Desk>() {
                /** Gets [Desk] for existing [displayId] or creates a new one. */
                fun getOrCreate(displayId: Int): Desk =
                    this[displayId]
                        ?: Desk(deskId = displayId, displayId = displayId).also {
                            this[displayId] = it
                        }
            }

        override fun createDesk(displayId: Int, deskId: Int) {
            check(displayId == deskId) { "Display and desk ids must match" }
            deskByDisplayId.getOrCreate(displayId)
        }

        override fun addDesk(displayId: Int, desk: Desk) {
            // No-op, not supported
        }

        override fun getDesk(deskId: Int): Desk =
            // TODO: b/362720497 - consider enforcing that the desk has been created before trying
            //  to use it. As of now, there are cases where a task may be created faster than a
            //  desk is, so just create it here if needed. See b/391984373.
            deskByDisplayId.getOrCreate(deskId)

        override fun getActiveDesk(displayId: Int): Desk {
            // TODO: 389787966 - consider migrating to an "active" state instead of checking the
            //   number of visible active tasks, PIP in desktop, and empty desktop logic. In
            //   practice, existing single-desktop devices are ok with this function returning the
            //   only desktop, even if it's not active.
            return deskByDisplayId.getOrCreate(displayId)
        }

        override fun setActiveDesk(displayId: Int, deskId: Int) {
            // No-op, in single-desk setups, which desktop is "active" is determined by the
            // existence of visible desktop windows, among other factors.
        }

        override fun setDeskInactive(deskId: Int) {
            // No-op, in single-desk setups, which desktop is "active" is determined by the
            // existence of visible desktop windows, among other factors.
        }

        override fun getDefaultDesk(displayId: Int): Desk = getDesk(deskId = displayId)

        override fun getAllActiveDesks(): Set<Desk> =
            deskByDisplayId.valueIterator().asSequence().toSet()

        override fun getNumberOfDesks(displayId: Int): Int = 1

        override fun getNumberOfDesks(): Int = 1

        override fun getOrderedDesks(displayId: Int): List<Desk> =
            listOf(getDesk(deskId = displayId))

        override fun forAllDesks(consumer: (Desk) -> Unit) {
            deskByDisplayId.forEach { _, desk -> consumer(desk) }
        }

        override fun forAllDesks(consumer: (Int, Desk) -> Unit) {
            deskByDisplayId.forEach { displayId, desk -> consumer(displayId, desk) }
        }

        override fun forAllDesks(displayId: Int, consumer: (Desk) -> Unit) {
            consumer(getDesk(deskId = displayId))
        }

        override fun desksSequence(): Sequence<Desk> = deskByDisplayId.valueIterator().asSequence()

        override fun desksSequence(displayId: Int): Sequence<Desk> =
            deskByDisplayId[displayId]?.let { sequenceOf(it) } ?: emptySequence()

        override fun remove(deskId: Int) {
            setDeskInactive(deskId)
            deskByDisplayId[deskId]?.clear()
        }

        override fun getDisplayForDesk(deskId: Int): Int = deskId

        override fun removeDisplay(displayId: Int) {
            deskByDisplayId.remove(displayId)
        }
    }

    /** A [DesktopData] implementation that supports multiple desks. */
    private class MultiDesktopData : DesktopData {
        private val desktopDisplays = SparseArray<DesktopDisplay>()

        override fun createDesk(displayId: Int, deskId: Int) {
            val display =
                desktopDisplays[displayId]
                    ?: DesktopDisplay(displayId).also { desktopDisplays[displayId] = it }
            check(display.orderedDesks.none { desk -> desk.deskId == deskId }) {
                "Attempting to create desk#$deskId that already exists in display#$displayId"
            }
            display.orderedDesks.add(Desk(deskId = deskId, displayId = displayId))
        }

        override fun addDesk(displayId: Int, desk: Desk) {
            val display =
                desktopDisplays[displayId]
                    ?: DesktopDisplay(displayId).also { desktopDisplays[displayId] = it }
            check(
                display.orderedDesks.none { existingDesk -> desk.deskId == existingDesk.deskId }
            ) {
                "Attempting to add desk#${desk.deskId} that already exists in display#$displayId"
            }
            display.orderedDesks.add(desk)
        }

        override fun getDesk(deskId: Int): Desk? {
            desktopDisplays.forEach { _, display ->
                val desk = display.orderedDesks.find { desk -> desk.deskId == deskId }
                if (desk != null) {
                    return desk
                }
            }
            return null
        }

        override fun getActiveDesk(displayId: Int): Desk? {
            val display = desktopDisplays[displayId] ?: return null
            if (display.activeDeskId == null) return null
            return display.orderedDesks.find { it.deskId == display.activeDeskId }
        }

        override fun setActiveDesk(displayId: Int, deskId: Int) {
            val display =
                desktopDisplays[displayId] ?: error("Expected display#$displayId to exist")
            val desk = display.orderedDesks.single { it.deskId == deskId }
            display.activeDeskId = desk.deskId
        }

        override fun setDeskInactive(deskId: Int) {
            desktopDisplays.forEach { id, display ->
                if (display.activeDeskId == deskId) {
                    display.activeDeskId = null
                }
            }
        }

        override fun getDefaultDesk(displayId: Int): Desk? {
            val display = desktopDisplays[displayId] ?: return null
            return display.orderedDesks.find { it.deskId == display.activeDeskId }
                ?: display.orderedDesks.firstOrNull()
        }

        override fun getOrderedDesks(displayId: Int): List<Desk> =
            desktopDisplays[displayId]?.orderedDesks?.toList() ?: emptyList()

        override fun getAllActiveDesks(): Set<Desk> {
            return desktopDisplays
                .valueIterator()
                .asSequence()
                .filter { display -> display.activeDeskId != null }
                .map { display ->
                    display.orderedDesks.single { it.deskId == display.activeDeskId }
                }
                .toSet()
        }

        override fun getNumberOfDesks(): Int =
            desktopDisplays.valueIterator().asSequence().sumOf { display ->
                display.orderedDesks.size
            }

        override fun getNumberOfDesks(displayId: Int): Int =
            desktopDisplays[displayId]?.orderedDesks?.size ?: 0

        override fun forAllDesks(consumer: (Desk) -> Unit) {
            desktopDisplays.forEach { _, display -> display.orderedDesks.forEach { consumer(it) } }
        }

        override fun forAllDesks(consumer: (Int, Desk) -> Unit) {
            desktopDisplays.forEach { _, display ->
                display.orderedDesks.forEach { consumer(display.displayId, it) }
            }
        }

        override fun forAllDesks(displayId: Int, consumer: (Desk) -> Unit) {
            desktopDisplays
                .valueIterator()
                .asSequence()
                .filter { display -> display.displayId == displayId }
                .flatMap { display -> display.orderedDesks.asSequence() }
                .forEach { desk -> consumer(desk) }
        }

        override fun desksSequence(): Sequence<Desk> =
            desktopDisplays.valueIterator().asSequence().flatMap { display ->
                display.orderedDesks.asSequence()
            }

        override fun desksSequence(displayId: Int): Sequence<Desk> =
            desktopDisplays[displayId]?.orderedDesks?.asSequence() ?: emptySequence()

        override fun remove(deskId: Int) {
            setDeskInactive(deskId)
            desktopDisplays.forEach { _, display ->
                display.orderedDesks.removeIf { it.deskId == deskId }
            }
        }

        override fun getDisplayForDesk(deskId: Int): Int =
            desksSequence().find { it.deskId == deskId }?.displayId
                ?: error("Display for desk=$deskId not found")

        override fun removeDisplay(displayId: Int) {
            desktopDisplays.remove(displayId)
        }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logW(msg: String, vararg arguments: Any?) {
        ProtoLog.w(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private fun logE(msg: String, vararg arguments: Any?) {
        ProtoLog.e(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopRepository"

        @VisibleForTesting const val INVALID_DESK_ID = -1
    }
}

private fun <T> Iterable<T>.toDumpString(): String =
    joinToString(separator = ", ", prefix = "[", postfix = "]")
