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

package com.android.quickstep.recents.domain.model

import android.graphics.drawable.Drawable
import com.android.systemui.shared.recents.model.ThumbnailData

/**
 * Data class representing a task in the application.
 *
 * This class holds the essential information about a task, including its unique identifier, display
 * title, associated icon, optional thumbnail data, and background color.
 *
 * @property id The unique identifier for this task. Must be an integer.
 * @property title The display title of the task.
 * @property titleDescription A content description of the task.
 * @property icon An optional drawable resource representing an icon for the task. Can be null if no
 *   icon is required.
 * @property thumbnail An optional [ThumbnailData] object containing thumbnail information. Can be
 *   null if no thumbnail is needed.
 * @property backgroundColor The background color of the task, represented as an integer color
 *   value.
 * @property isLocked Indicates whether the [Task] is locked.
 */
data class TaskModel(
    val id: TaskId,
    val title: String?,
    val titleDescription: String?,
    val icon: Drawable?,
    val thumbnail: ThumbnailData?,
    val backgroundColor: Int,
    val isLocked: Boolean,
)

typealias TaskId = Int
