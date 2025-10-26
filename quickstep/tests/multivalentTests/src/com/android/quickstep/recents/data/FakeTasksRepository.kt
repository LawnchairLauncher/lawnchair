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
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class FakeTasksRepository : RecentTasksRepository {
    private var thumbnailDataMap: Map<Int, ThumbnailData> = emptyMap()
    private var taskIconDataMap: Map<Int, FakeIconData> = emptyMap()
    private var tasks: MutableStateFlow<List<Task>> = MutableStateFlow(emptyList())
    private var visibleTasks: MutableStateFlow<Set<Int>> = MutableStateFlow(emptySet())

    override fun getAllTaskData(forceRefresh: Boolean): Flow<List<Task>> = tasks

    override fun getTaskDataById(taskId: Int): Flow<Task?> =
        combine(getAllTaskData(), visibleTasks) { taskList, visibleTasks ->
                taskList.filter { visibleTasks.contains(it.key.id) }
            }
            .map { taskList ->
                val task = taskList.firstOrNull { it.key.id == taskId } ?: return@map null
                Task(task).apply {
                    thumbnail = task.thumbnail
                    icon = task.icon
                    titleDescription = task.titleDescription
                    title = task.title
                }
            }

    override fun getThumbnailById(taskId: Int): Flow<ThumbnailData?> =
        getTaskDataById(taskId).map { it?.thumbnail }

    override fun getCurrentThumbnailById(taskId: Int): ThumbnailData? =
        tasks.value.firstOrNull { it.key.id == taskId }?.thumbnail

    override fun setVisibleTasks(visibleTaskIdList: Set<Int>) {
        visibleTasks.value = visibleTaskIdList
        tasks.value =
            tasks.value.map {
                it.apply {
                    thumbnail = thumbnailDataMap[it.key.id]
                    taskIconDataMap[it.key.id]?.let { data ->
                        title = data.title
                        titleDescription = data.titleDescription
                        icon = data.icon
                    }
                }
            }
    }

    fun seedTasks(tasks: List<Task>) {
        this.tasks.value = tasks
    }

    fun seedThumbnailData(thumbnailDataMap: Map<Int, ThumbnailData>) {
        this.thumbnailDataMap = thumbnailDataMap
    }

    fun seedIconData(id: Int, title: String, contentDescription: String, icon: Drawable) {
        val iconData = FakeIconData(icon, contentDescription, title)
        this.taskIconDataMap = mapOf(id to iconData)
    }

    private data class FakeIconData(
        val icon: Drawable,
        val titleDescription: String,
        val title: String,
    )
}
