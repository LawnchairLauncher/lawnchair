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
import android.view.View.OnClickListener
import androidx.annotation.ColorInt

sealed class TaskThumbnailUiState {
    data object Uninitialized : TaskThumbnailUiState()

    data class BackgroundOnly(@ColorInt val backgroundColor: Int) : TaskThumbnailUiState()

    data class SnapshotSplash(val snapshot: Snapshot, val splash: Drawable?) :
        TaskThumbnailUiState()

    sealed class LiveTile : TaskThumbnailUiState() {
        data class WithHeader(val header: ThumbnailHeader) : LiveTile()

        data object WithoutHeader : LiveTile()
    }

    sealed class Snapshot {
        abstract val bitmap: Bitmap
        abstract val thumbnailRotation: Int
        abstract val backgroundColor: Int

        data class WithHeader(
            override val bitmap: Bitmap,
            @Surface.Rotation override val thumbnailRotation: Int,
            @ColorInt override val backgroundColor: Int,
            val header: ThumbnailHeader,
        ) : Snapshot()

        data class WithoutHeader(
            override val bitmap: Bitmap,
            @Surface.Rotation override val thumbnailRotation: Int,
            @ColorInt override val backgroundColor: Int,
        ) : Snapshot()
    }

    data class ThumbnailHeader(
        val icon: Drawable,
        val title: String,
        val clickCloseListener: OnClickListener,
    )
}
