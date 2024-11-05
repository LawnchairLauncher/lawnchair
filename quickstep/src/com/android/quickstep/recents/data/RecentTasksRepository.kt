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
import kotlinx.coroutines.flow.Flow

interface RecentTasksRepository {
    /** Gets all the recent tasks, refreshing from data sources if [forceRefresh] is true. */
    fun getAllTaskData(forceRefresh: Boolean = false): Flow<List<Task>>

    /**
     * Gets the data associated with a task that has id [taskId]. Flow will settle on null if the
     * task was not found.
     */
    fun getTaskDataById(taskId: Int): Flow<Task?>

    /**
     * Sets the tasks that are visible, indicating that properties relating to visuals need to be
     * populated e.g. icons/thumbnails etc.
     */
    fun setVisibleTasks(visibleTaskIdList: List<Int>)
}
