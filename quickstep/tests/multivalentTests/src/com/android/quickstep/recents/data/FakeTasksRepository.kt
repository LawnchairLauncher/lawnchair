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

import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTasksRepository : RecentTasksRepository {
    private var thumbnailDataMap: Map<Int, ThumbnailData> = emptyMap()
    private var tasks: MutableStateFlow<List<Task>> = MutableStateFlow(emptyList())
    private var visibleTasks: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())

    override fun getAllTaskData(forceRefresh: Boolean): Flow<List<Task>> = tasks

    override fun getTaskDataById(taskId: Int): Flow<Task?> =
        getAllTaskData().map { taskList -> taskList.firstOrNull { it.key.id == taskId } }

    override fun setVisibleTasks(visibleTaskIdList: List<Int>) {
        visibleTasks.value = visibleTaskIdList
        tasks.value = tasks.value.map { it.apply { thumbnail = thumbnailDataMap[it.key.id] } }
    }

    fun seedTasks(tasks: List<Task>) {
        this.tasks.value = tasks
    }

    fun seedThumbnailData(thumbnailDataMap: Map<Int, ThumbnailData>) {
        this.thumbnailDataMap = thumbnailDataMap
    }
}
