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

package com.android.quickstep.task.thumbnail

import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.task.viewmodel.TaskViewData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TaskThumbnailViewModel(recentsViewData: RecentsViewData, taskViewData: TaskViewData) {
    private val task = MutableStateFlow<TaskThumbnail?>(null)

    val recentsFullscreenProgress = recentsViewData.fullscreenProgress
    val inheritedScale =
        combine(recentsViewData.scale, taskViewData.scale) { recentsScale, taskScale ->
            recentsScale * taskScale
        }
    val uiState =
        task.map { taskVal ->
            when {
                taskVal == null -> Uninitialized
                taskVal.isRunning -> LiveTile
                else -> Uninitialized
            }
        }

    fun bind(task: TaskThumbnail) {
        this.task.value = task
    }
}
