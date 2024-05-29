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

import com.android.quickstep.TaskIconCache
import com.android.quickstep.task.thumbnail.data.TaskThumbnailDataSource
import com.android.quickstep.util.GroupTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlin.coroutines.resume
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class TasksRepository(
    private val recentsModel: RecentTasksDataSource,
    private val taskThumbnailDataSource: TaskThumbnailDataSource,
    private val taskIconCache: TaskIconCache,
) : RecentTasksRepository {
    private val groupedTaskData = MutableStateFlow(emptyList<GroupTask>())
    private val _taskData =
        groupedTaskData.map { groupTaskList -> groupTaskList.flatMap { it.tasks } }
    private val visibleTaskIds = MutableStateFlow(emptySet<Int>())

    private val taskData: Flow<List<Task>> =
        combine(_taskData, getThumbnailQueryResults()) { tasks, results ->
            tasks.forEach { task ->
                // Add retrieved thumbnails + remove unnecessary thumbnails
                task.thumbnail = results[task.key.id]
            }
            tasks
        }

    override fun getAllTaskData(forceRefresh: Boolean): Flow<List<Task>> {
        if (forceRefresh) {
            recentsModel.getTasks { groupedTaskData.value = it }
        }
        return taskData
    }

    override fun getTaskDataById(taskId: Int): Flow<Task?> =
        taskData.map { taskList -> taskList.firstOrNull { it.key.id == taskId } }

    override fun setVisibleTasks(visibleTaskIdList: List<Int>) {
        this.visibleTaskIds.value = visibleTaskIdList.toSet()
    }

    /** Flow wrapper for [TaskThumbnailDataSource.updateThumbnailInBackground] api */
    private fun getThumbnailDataRequest(task: Task): ThumbnailDataRequest =
        flow {
                emit(task.key.id to task.thumbnail)
                val thumbnailDataResult: ThumbnailData? =
                    suspendCancellableCoroutine { continuation ->
                        val cancellableTask =
                            taskThumbnailDataSource.updateThumbnailInBackground(task) {
                                continuation.resume(it)
                            }
                        continuation.invokeOnCancellation { cancellableTask?.cancel() }
                    }
                emit(task.key.id to thumbnailDataResult)
            }
            .distinctUntilChanged()

    /**
     * This is a Flow that makes a query for thumbnail data to the [taskThumbnailDataSource] for
     * each visible task. It then collects the responses and returns them in a Map as soon as they
     * are available.
     */
    private fun getThumbnailQueryResults(): Flow<Map<Int, ThumbnailData?>> {
        val visibleTasks =
            combine(_taskData, visibleTaskIds) { tasks, visibleIds ->
                tasks.filter { it.key.id in visibleIds }
            }
        val visibleThumbnailDataRequests: Flow<List<ThumbnailDataRequest>> =
            visibleTasks.map {
                it.map { visibleTask ->
                    val taskCopy = Task(visibleTask).apply { thumbnail = visibleTask.thumbnail }
                    getThumbnailDataRequest(taskCopy)
                }
            }
        return visibleThumbnailDataRequests.flatMapLatest {
            thumbnailRequestFlows: List<ThumbnailDataRequest> ->
            if (thumbnailRequestFlows.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(thumbnailRequestFlows) { it.toMap() }
            }
        }
    }
}

typealias ThumbnailDataRequest = Flow<Pair<Int, ThumbnailData?>>
