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

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.Surface
import androidx.annotation.ColorInt

sealed class TaskThumbnailUiState {
    data object Uninitialized : TaskThumbnailUiState()

    data class BackgroundOnly(@ColorInt val backgroundColor: Int) : TaskThumbnailUiState()

    data object LiveTile : TaskThumbnailUiState()

    data class SnapshotSplash(val snapshot: Snapshot, val splash: Drawable?) :
        TaskThumbnailUiState()

    data class Snapshot(
        val bitmap: Bitmap,
        @Surface.Rotation val thumbnailRotation: Int,
        @ColorInt val backgroundColor: Int,
    )
}
