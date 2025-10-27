/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.quickstep.recents.ui.viewmodel

import android.graphics.drawable.Drawable
import com.android.systemui.shared.recents.model.ThumbnailData

/**
 * This class represents the UI state to be consumed by TaskView, GroupTaskView and DesktopTaskView.
 * Data class representing the state of a list of tasks.
 *
 * This class encapsulates a list of [TaskTileUiState] objects, along with a flag indicating whether
 * the data is being used for a live tile display.
 *
 * @property tasks The list of [TaskTileUiState] objects representing the individual tasks.
 * @property isLiveTile Indicates whether this data is intended for a live tile. If `true`, the
 *   running app will be displayed instead of the thumbnail.
 * @property sysUiStatusNavFlags Flags for status bar and navigation bar
 */
data class TaskTileUiState(
    val tasks: List<TaskData>,
    val isLiveTile: Boolean,
    val hasHeader: Boolean,
    val sysUiStatusNavFlags: Int,
    val taskOverlayEnabled: Boolean,
    val isCentralTask: Boolean,
)

sealed class TaskData {
    abstract val taskId: Int

    /** When no data was found for the TaskId provided */
    data class NoData(override val taskId: Int) : TaskData()

    /**
     * This class provides UI information related to a Task (App) to be displayed within a TaskView.
     *
     * @property taskId Identifier of the task
     * @property title App title
     * @property titleDescription App content description
     * @property icon App icon
     * @property thumbnailData Information related to the last snapshot retrieved from the app
     * @property backgroundColor The background color of the task.
     * @property isLocked Indicates whether the task is locked or not.
     */
    data class Data(
        override val taskId: Int,
        val title: String?,
        val titleDescription: String?,
        val icon: Drawable?,
        val thumbnailData: ThumbnailData?,
        val backgroundColor: Int,
        val isLocked: Boolean,
    ) : TaskData()
}
