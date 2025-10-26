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
package com.android.launcher3.taskbar

import android.content.Context
import android.window.DesktopModeFlags
import androidx.annotation.VisibleForTesting
import com.android.launcher3.BubbleTextView.RunningAppState
import com.android.launcher3.Flags
import com.android.launcher3.Flags.enableRecentsInTaskbar
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.TaskItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController
import com.android.launcher3.util.CancellableTask
import com.android.quickstep.RecentsFilterState
import com.android.quickstep.RecentsModel
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.quickstep.util.SingleTask
import com.android.wm.shell.shared.desktopmode.DesktopModeStatus
import java.io.PrintWriter

/**
 * Provides recent apps functionality, when the Taskbar Recent Apps section is enabled. Behavior:
 * - When in Fullscreen mode: show the N most recent Tasks
 * - When in Desktop Mode: show the currently running (open) Tasks
 */
class TaskbarRecentAppsController(context: Context, private val recentsModel: RecentsModel) :
    LoggableTaskbarController {

    var canShowRunningApps =
        DesktopModeStatus.canEnterDesktopMode(context) &&
            DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS.isTrue
        @VisibleForTesting
        set(isEnabledFromTest) {
            field = isEnabledFromTest
            if (!field && !canShowRecentApps) {
                recentsModel.unregisterRecentTasksChangedListener()
            }
        }

    // TODO(b/343532825): Add a setting to disable Recents even when the flag is on.
    var canShowRecentApps = enableRecentsInTaskbar()
        @VisibleForTesting
        set(isEnabledFromTest) {
            field = isEnabledFromTest
            if (!field && !canShowRunningApps) {
                recentsModel.unregisterRecentTasksChangedListener()
            }
        }

    // Initialized in init.
    private lateinit var controllers: TaskbarControllers

    var shownHotseatItems: List<ItemInfo> = emptyList()
        private set

    private var allRecentTasks: List<GroupTask> = emptyList()
    private var desktopTask: DesktopTask? = null
    // Keeps track of the order in which running tasks appear.
    private var orderedRunningTaskIds = emptyList<Int>()
    var shownTasks: List<GroupTask> = emptyList()
        private set

    val shownTaskIds: List<Int>
        get() = shownTasks.flatMap { shownTask -> shownTask.tasks }.map { it.key.id }

    /**
     * The task-state of an app, i.e. whether the app has a task and what state that task is in.
     *
     * @property taskId The ID of the task if one exists (i.e. if the state is RUNNING or
     *   MINIMIZED), null otherwise (NOT_RUNNING).
     */
    data class TaskState(val runningAppState: RunningAppState, val taskId: Int? = null)

    /**
     * Returns the state of the most active Desktop task represented by the given [ItemInfo].
     *
     * If there are several tasks represented by the same [ItemInfo] we return the most active one,
     * i.e. we return [DesktopAppState.RUNNING] over [DesktopAppState.MINIMIZED], and
     * [DesktopAppState.MINIMIZED] over [DesktopAppState.NOT_RUNNING].
     */
    fun getDesktopItemState(itemInfo: ItemInfo?): TaskState {
        val packageName =
            itemInfo?.getTargetPackage() ?: return TaskState(RunningAppState.NOT_RUNNING)
        return getDesktopTaskState(packageName, itemInfo.user.identifier)
    }

    private fun getDesktopTaskState(packageName: String, userId: Int): TaskState {
        val tasks = desktopTask?.tasks ?: return TaskState(RunningAppState.NOT_RUNNING)
        val appTasks =
            tasks.filter { task ->
                packageName == task.key.packageName && task.key.userId == userId
            }
        val runningTask = appTasks.find { getRunningAppState(it.key.id) == RunningAppState.RUNNING }
        if (runningTask != null) {
            return TaskState(RunningAppState.RUNNING, runningTask.key.id)
        }
        val minimizedTask =
            appTasks.find { getRunningAppState(it.key.id) == RunningAppState.MINIMIZED }
        if (minimizedTask != null) {
            return TaskState(RunningAppState.MINIMIZED, taskId = minimizedTask.key.id)
        }
        return TaskState(RunningAppState.NOT_RUNNING)
    }

    /** Get the [RunningAppState] for the given task. */
    fun getRunningAppState(taskId: Int): RunningAppState {
        return when (taskId) {
            in minimizedTaskIds -> RunningAppState.MINIMIZED
            in runningTaskIds -> RunningAppState.RUNNING
            else -> RunningAppState.NOT_RUNNING
        }
    }

    @VisibleForTesting
    val runningTaskIds: Set<Int>
        /**
         * Returns the task IDs of apps that should be indicated as "running" to the user.
         * Specifically, we return all the open tasks if we are in Desktop mode, else emptySet().
         */
        get() {
            if (
                !canShowRunningApps ||
                    !controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()
            ) {
                return emptySet()
            }
            val tasks = desktopTask?.tasks ?: return emptySet()
            return tasks.map { task -> task.key.id }.toSet()
        }

    @VisibleForTesting
    val minimizedTaskIds: Set<Int>
        /**
         * Returns the task IDs for the tasks that should be indicated as "minimized" to the user.
         */
        get() {
            if (
                !canShowRunningApps ||
                    !controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()
            ) {
                return emptySet()
            }
            val desktopTasks = desktopTask?.tasks ?: return emptySet()
            return desktopTasks.filter { !it.isVisible }.map { task -> task.key.id }.toSet()
        }

    private val recentTasksChangedListener =
        RecentsModel.RecentTasksChangedListener { reloadRecentTasksIfNeeded() }

    private val iconLoadRequests: MutableSet<CancellableTask<*>> = HashSet()

    // TODO(b/343291428): add TaskVisualsChangListener as well (for calendar/clock?)

    // Used to keep track of the last requested task list ID, so that we do not request to load the
    // tasks again if we have already requested it and the task list has not changed
    private var taskListChangeId = -1

    fun init(taskbarControllers: TaskbarControllers) {
        controllers = taskbarControllers
        if (canShowRunningApps || canShowRecentApps) {
            recentsModel.registerRecentTasksChangedListener(recentTasksChangedListener)
            controllers.runAfterInit { reloadRecentTasksIfNeeded() }
        }
    }

    fun onDestroy() {
        recentsModel.unregisterRecentTasksChangedListener()
        iconLoadRequests.forEach { it.cancel() }
        iconLoadRequests.clear()
    }

    /** Called to update hotseatItems, in order to de-dupe them from Recent/Running tasks later. */
    fun updateHotseatItemInfos(hotseatItems: Array<ItemInfo?>): Array<ItemInfo?> {
        // Ignore predicted apps - we show running or recent apps instead.
        val showDesktopTasks =
            controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()
        val removePredictions =
            (showDesktopTasks && canShowRunningApps) || (!showDesktopTasks && canShowRecentApps)
        if (!removePredictions) {
            shownHotseatItems = hotseatItems.filterNotNull()
            onRecentsOrHotseatChanged()
            return hotseatItems
        }
        shownHotseatItems =
            hotseatItems
                .filterNotNull()
                .filter { itemInfo -> !itemInfo.isPredictedItem }
                .toMutableList()

        if (showDesktopTasks && canShowRunningApps) {
            shownHotseatItems =
                updateHotseatItemsFromRunningTasks(
                    getOrderedAndWrappedDesktopTasks(),
                    shownHotseatItems,
                )
        }

        onRecentsOrHotseatChanged()

        return shownHotseatItems.toTypedArray()
    }

    private fun getOrderedAndWrappedDesktopTasks(): List<SingleTask> {
        val tasks = desktopTask?.tasks ?: emptyList()
        // We wrap each task in the Desktop as a `SingleTask`.
        val orderFromId = orderedRunningTaskIds.withIndex().associate { (index, id) -> id to index }
        val sortedTasks = tasks.sortedWith(compareBy(nullsLast()) { orderFromId[it.key.id] })
        return sortedTasks.map { SingleTask(it) }
    }

    private fun reloadRecentTasksIfNeeded() {
        if (!recentsModel.isTaskListValid(taskListChangeId)) {
            taskListChangeId =
                recentsModel.getTasks(
                    { tasks ->
                        allRecentTasks = tasks
                        val oldRunningTaskdIds = runningTaskIds
                        val oldMinimizedTaskIds = minimizedTaskIds
                        desktopTask = allRecentTasks.filterIsInstance<DesktopTask>().firstOrNull()
                        val runningTasksChanged = oldRunningTaskdIds != runningTaskIds
                        val minimizedTasksChanged = oldMinimizedTaskIds != minimizedTaskIds
                        if (
                            onRecentsOrHotseatChanged() ||
                                runningTasksChanged ||
                                minimizedTasksChanged
                        ) {
                            controllers.taskbarViewController.commitRunningAppsToUI()
                        }
                    },
                    RecentsFilterState.EMPTY_FILTER,
                )
        }
    }

    /**
     * Updates [shownTasks] when Recents or Hotseat changes.
     *
     * @return Whether [shownTasks] changed.
     */
    private fun onRecentsOrHotseatChanged(): Boolean {
        val oldShownTasks = shownTasks
        orderedRunningTaskIds = updateOrderedRunningTaskIds()
        shownTasks =
            if (controllers.taskbarDesktopModeController.shouldShowDesktopTasksInTaskbar()) {
                computeShownRunningTasks()
            } else {
                computeShownRecentTasks()
            }
        val shownTasksChanged = oldShownTasks != shownTasks
        if (!shownTasksChanged) {
            return shownTasksChanged
        }

        for (groupTask in shownTasks) {
            for (task in groupTask.tasks) {
                val cancellableTask =
                    recentsModel.iconCache.getIconInBackground(task) {
                        icon,
                        contentDescription,
                        title ->
                        task.icon = icon
                        task.titleDescription = contentDescription
                        task.title = title
                        controllers.taskbarViewController.onTaskUpdated(task)
                    }
                if (cancellableTask != null) {
                    iconLoadRequests.add(cancellableTask)
                }
            }
        }
        return shownTasksChanged
    }

    private fun updateOrderedRunningTaskIds(): MutableList<Int> {
        val desktopTasksAsList = getOrderedAndWrappedDesktopTasks().map { it.task }
        val desktopTaskIds = desktopTasksAsList.map { it.key.id }
        var newOrder =
            orderedRunningTaskIds
                .filter { it in desktopTaskIds } // Only keep the tasks that are still running
                .toMutableList()
        // Add new tasks not already listed
        newOrder.addAll(desktopTaskIds.filter { it !in newOrder })
        return newOrder
    }

    /**
     * Computes the list of running tasks to be shown in the recent apps section of the taskbar in
     * desktop mode, taking into account deduplication against hotseat items and existing tasks.
     */
    private fun computeShownRunningTasks(): List<GroupTask> {
        if (!canShowRunningApps) {
            return emptyList()
        }

        val desktopTasks = getOrderedAndWrappedDesktopTasks()

        val newShownTasks =
            if (Flags.enableMultiInstanceMenuTaskbar()) {
                val deduplicatedDesktopTasks =
                    desktopTasks.distinctBy { Pair(it.task.key.packageName, it.task.key.userId) }

                shownTasks
                    .filter {
                        it is SingleTask &&
                            it.task.key.id in deduplicatedDesktopTasks.map { it.task.key.id }
                    }
                    .toMutableList()
                    .apply {
                        addAll(
                            deduplicatedDesktopTasks.filter { currentTask ->
                                val currentTaskKey = currentTask.task.key
                                currentTaskKey.id !in shownTaskIds &&
                                    shownHotseatItems.none { hotseatItem ->
                                        currentTask.containsPackage(
                                            hotseatItem.targetPackage,
                                            hotseatItem.user.identifier,
                                        )
                                    }
                            }
                        )
                    }
            } else {
                val desktopTaskIds = desktopTasks.map { it.task.key.id }
                val shownHotseatItemTaskIds =
                    shownHotseatItems.mapNotNull { it as? TaskItemInfo }.map { it.taskId }

                shownTasks
                    .filter { it is SingleTask && it.task.key.id in desktopTaskIds }
                    .toMutableList()
                    .apply {
                        addAll(
                            desktopTasks.filter { desktopTask ->
                                desktopTask.task.key.id !in shownTaskIds
                            }
                        )
                        removeAll { it is SingleTask && it.task.key.id in shownHotseatItemTaskIds }
                    }
            }

        return newShownTasks
    }

    private fun computeShownRecentTasks(): List<GroupTask> {
        if (!canShowRecentApps || allRecentTasks.isEmpty()) {
            return emptyList()
        }
        // Remove the current task.
        val allRecentTasks = allRecentTasks.subList(0, allRecentTasks.size - 1)
        var shownTasks = dedupeHotseatTasks(allRecentTasks, shownHotseatItems)
        if (shownTasks.size > MAX_RECENT_TASKS) {
            // Remove any tasks older than MAX_RECENT_TASKS.
            shownTasks = shownTasks.subList(shownTasks.size - MAX_RECENT_TASKS, shownTasks.size)
        }
        return shownTasks
    }

    private fun dedupeHotseatTasks(
        groupTasks: List<GroupTask>,
        shownHotseatItems: List<ItemInfo>,
    ): List<GroupTask> {
        // TODO: b/393476333 - Check the behavior of the Taskbar recents section when empty desks
        // become supported.
        return if (Flags.enableMultiInstanceMenuTaskbar()) {
            groupTasks.filter { groupTask ->
                // Keep tasks that are group tasks or unique package name/user combinations
                when (groupTask) {
                    is SingleTask ->
                        shownHotseatItems.none {
                            groupTask.containsPackage(it.targetPackage, it.user.identifier)
                        }

                    else -> true
                }
            }
        } else {
            val hotseatPackages = shownHotseatItems.map { it.targetPackage }
            groupTasks.filter { groupTask ->
                when (groupTask) {
                    is SingleTask -> hotseatPackages.none { groupTask.containsPackage(it) }

                    else -> true
                }
            }
        }
    }

    /**
     * Returns the hotseat items updated so that any item that points to a package+user with a
     * running task also references that task.
     */
    private fun updateHotseatItemsFromRunningTasks(
        groupTasks: List<GroupTask>,
        shownHotseatItems: List<ItemInfo>,
    ): List<ItemInfo> =
        shownHotseatItems.map { itemInfo ->
            if (itemInfo is TaskItemInfo) {
                itemInfo
            } else {
                val foundTask =
                    groupTasks
                        .flatMap { it.tasks }
                        .find { task ->
                            task.key.packageName == itemInfo.targetPackage &&
                                task.key.userId == itemInfo.user.identifier
                        } ?: return@map itemInfo
                TaskItemInfo(foundTask.key.id, itemInfo as WorkspaceItemInfo)
            }
        }

    override fun dumpLogs(prefix: String, pw: PrintWriter) {
        pw.println("$prefix TaskbarRecentAppsController:")
        pw.println("$prefix\tcanShowRunningApps=$canShowRunningApps")
        pw.println("$prefix\tcanShowRecentApps=$canShowRecentApps")
        pw.println("$prefix\tshownHotseatItems=${shownHotseatItems.map{item->item.targetPackage}}")
        pw.println("$prefix\tallRecentTasks=${allRecentTasks.map { it.packageNames }}")
        pw.println("$prefix\tdesktopTask=${desktopTask?.packageNames}")
        pw.println("$prefix\tshownTasks=${shownTasks.map { it.packageNames }}")
        pw.println("$prefix\trunningTaskIds=$runningTaskIds")
        pw.println("$prefix\tminimizedTaskIds=$minimizedTaskIds")
    }

    private val GroupTask.packageNames: List<String>
        get() = tasks.map { task -> task.key.packageName }

    private companion object {
        const val MAX_RECENT_TASKS = 2
    }
}
