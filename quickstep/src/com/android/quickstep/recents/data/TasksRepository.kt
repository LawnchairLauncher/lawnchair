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

package com.android.quickstep.recents.data

import android.graphics.drawable.Drawable
import android.util.Log
import android.util.SparseArray
import androidx.core.util.valueIterator
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskIconChangedCallback
import com.android.quickstep.recents.data.TaskVisualsChangedDelegate.TaskThumbnailChangedCallback
import com.android.quickstep.task.thumbnail.data.TaskIconDataSource
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TasksRepository(
    private val recentsModel: RecentTasksDataSource,
    private val taskThumbnailDataSource: TaskThumbnailDataSource,
    private val taskIconDataSource: TaskIconDataSource,
    private val taskVisualsChangedDelegate: TaskVisualsChangedDelegate,
    private val recentsCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : RecentTasksRepository {
    private val tasks = MutableStateFlow(MapForStateFlow<Int, Task>(emptyMap()))
    private var visibleTaskIdsPerDisplay = SparseArray<Set<Int>>()
    private val taskRequests = HashMap<Int, Pair<Task.TaskKey, Job>>()

    override fun getAllTaskData(displayId: Int, forceRefresh: Boolean): Flow<List<Task>> {
        if (!visibleTaskIdsPerDisplay.contains(displayId)) {
            visibleTaskIdsPerDisplay.put(displayId, emptySet())
        }
        if (forceRefresh) {
            recentsModel.getTasks { newTaskList ->
                val recentTasks =
                    newTaskList.flatMap { groupTask -> groupTask.tasks }.associateBy { it.key.id }
                Log.d(
                    TAG,
                    "getAllTaskData: oldTasks ${tasks.value.keys}, newTasks: ${recentTasks.keys}",
                )
                tasks.update { oldTaskList ->
                    // Copy retrieved visuals to new Task objects
                    recentTasks.forEach { (taskId, task) ->
                        task.thumbnail = oldTaskList[taskId]?.thumbnail
                        task.icon = oldTaskList[taskId]?.icon
                        task.title = oldTaskList[taskId]?.title
                        task.titleDescription = oldTaskList[taskId]?.titleDescription
                    }
                    MapForStateFlow(recentTasks)
                }

                updateTaskRequests()
            }
        }
        return tasks.map { it.values.filter { it.key.displayId == displayId }.toList() }
    }

    override fun getTaskDataById(taskId: Int) = tasks.map { it[taskId] }

    override fun getThumbnailById(taskId: Int) =
        getTaskDataById(taskId).map { it?.thumbnail }.distinctUntilChangedBy { it?.snapshotId }

    override fun getCurrentThumbnailById(taskId: Int) = tasks.value[taskId]?.thumbnail

    override fun setVisibleTasks(displayId: Int, visibleTaskIdList: Set<Int>) {
        if (visibleTaskIdList.isEmpty()) {
            visibleTaskIdsPerDisplay.remove(displayId)
        } else {
            visibleTaskIdsPerDisplay.put(displayId, visibleTaskIdList)
        }
        updateTaskRequests()
    }

    @Synchronized
    private fun updateTaskRequests() {
        val allVisibleTaskIds =
            visibleTaskIdsPerDisplay.valueIterator().asSequence().flatMap { it }.toSet()
        val requestsNeeded = allVisibleTaskIds.intersect(tasks.value.keys)

        val taskRequestIds = taskRequests.keys
        val requestsNoLongerNeeded = taskRequestIds.subtract(requestsNeeded)
        val newlyRequestedTasks = requestsNeeded.subtract(taskRequestIds)
        if (requestsNoLongerNeeded.isNotEmpty() || newlyRequestedTasks.isNotEmpty()) {
            Log.d(
                TAG,
                "updateTaskRequests to: $requestsNeeded, " +
                    "removed: $requestsNoLongerNeeded, added: $newlyRequestedTasks",
            )
        }

        // Remove tasks are no longer visible
        removeTasks(requestsNoLongerNeeded)
        // Add new tasks to be requested
        newlyRequestedTasks.forEach { taskId -> requestTaskData(taskId) }
    }

    private fun requestTaskData(taskId: Int) {
        val task = tasks.value[taskId] ?: return
        Log.i(TAG, "requestTaskData: $taskId")
        taskRequests[taskId] =
            Pair(
                task.key,
                recentsCoroutineScope.launch(dispatcherProvider.lightweightBackground) {
                    val thumbnailFetchDeferred = async { fetchThumbnail(task) }
                    val iconFetchDeferred = async { fetchIcon(task) }
                    awaitAll(thumbnailFetchDeferred, iconFetchDeferred)
                },
            )
    }

    private fun removeTasks(tasksToRemove: Set<Int>) {
        if (tasksToRemove.isEmpty()) return

        Log.i(TAG, "removeTasks: $tasksToRemove")
        tasks.update { currentTasks ->
            tasksToRemove.forEach { taskId ->
                val request = taskRequests.remove(taskId) ?: return@forEach
                val (taskKey, job) = request
                job.cancel()

                // un-registering callbacks
                taskVisualsChangedDelegate.unregisterTaskIconChangedCallback(taskKey)
                taskVisualsChangedDelegate.unregisterTaskThumbnailChangedCallback(taskKey)

                // Clearing Task to reduce memory footprint
                currentTasks[taskId]?.apply {
                    thumbnail = null
                    icon = null
                    title = null
                    titleDescription = null
                }
            }
            MapForStateFlow(currentTasks)
        }
    }

    private suspend fun fetchIcon(task: Task) {
        updateIcon(task.key.id, getIconFromDataSource(task))
        taskVisualsChangedDelegate.registerTaskIconChangedCallback(
            task.key,
            object : TaskIconChangedCallback {
                override fun onTaskIconChanged() {
                    recentsCoroutineScope.launch(dispatcherProvider.lightweightBackground) {
                        updateIcon(task.key.id, getIconFromDataSource(task))
                    }
                }
            },
        )
    }

    private suspend fun fetchThumbnail(task: Task) {
        updateThumbnail(task.key.id, getThumbnailFromDataSource(task))
        taskVisualsChangedDelegate.registerTaskThumbnailChangedCallback(
            task.key,
            object : TaskThumbnailChangedCallback {
                override fun onTaskThumbnailChanged(thumbnailData: ThumbnailData?) {
                    updateThumbnail(task.key.id, thumbnailData)
                }

                override fun onHighResLoadingStateChanged(highResEnabled: Boolean) {
                    val isTaskVisible = taskRequests.containsKey(task.key.id)
                    if (!isTaskVisible) return

                    val isCurrentThumbnailLowRes =
                        tasks.value[task.key.id]?.thumbnail?.reducedResolution
                    val isRequestedResHigherThanCurrent =
                        isCurrentThumbnailLowRes == null ||
                            (isCurrentThumbnailLowRes && highResEnabled)
                    if (!isRequestedResHigherThanCurrent) return

                    recentsCoroutineScope.launch(dispatcherProvider.lightweightBackground) {
                        updateThumbnail(task.key.id, getThumbnailFromDataSource(task))
                    }
                }
            },
        )
    }

    private fun updateIcon(taskId: Int, iconData: IconData) {
        tasks.update { currentTasks ->
            currentTasks[taskId]?.apply {
                icon = iconData.icon
                titleDescription = iconData.contentDescription
                title = iconData.title
            }
            MapForStateFlow(currentTasks)
        }
    }

    private fun updateThumbnail(taskId: Int, thumbnail: ThumbnailData?) {
        tasks.update { currentTasks ->
            currentTasks[taskId]?.thumbnail = thumbnail
            MapForStateFlow(currentTasks)
        }
    }

    private suspend fun getThumbnailFromDataSource(task: Task) =
        withContext(dispatcherProvider.lightweightBackground) {
            taskThumbnailDataSource.getThumbnail(task)
        }

    private suspend fun getIconFromDataSource(task: Task) =
        withContext(dispatcherProvider.lightweightBackground) {
            val iconCacheEntry = taskIconDataSource.getIcon(task)
            IconData(iconCacheEntry.icon, iconCacheEntry.contentDescription, iconCacheEntry.title)
        }

    companion object {
        private const val TAG = "TasksRepository"
    }

    /** Helper class to support StateFlow emissions when using a Map with a MutableStateFlow. */
    private data class MapForStateFlow<K, T>(
        private val backingMap: Map<K, T>,
        private val updated: Long = System.nanoTime(),
    ) : Map<K, T> by backingMap

    private data class IconData(
        val icon: Drawable,
        val contentDescription: String,
        val title: String,
    )
}
