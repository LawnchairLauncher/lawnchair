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

package com.android.quickstep.recents.ui.mapper

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ShapeDrawable
import android.platform.test.annotations.EnableFlags
import android.view.Surface
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.ThumbnailHeader
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskUiStateMapperTest {

    @Test
    fun taskData_isNull_returns_Uninitialized() {
        val result =
            TaskUiStateMapper.toTaskThumbnailUiState(
                taskData = null,
                isLiveTile = false,
                hasHeader = false,
                clickCloseListener = null,
            )
        assertThat(result).isEqualTo(TaskThumbnailUiState.Uninitialized)
    }

    @Test
    fun taskData_isLiveTile_returns_LiveTile() {
        val inputs =
            listOf(TASK_DATA, TASK_DATA.copy(thumbnailData = null), TASK_DATA.copy(isLocked = true))
        inputs.forEach { input ->
            val result =
                TaskUiStateMapper.toTaskThumbnailUiState(
                    taskData = input,
                    isLiveTile = true,
                    hasHeader = false,
                    clickCloseListener = null,
                )
            assertThat(result).isEqualTo(LiveTile.WithoutHeader)
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun taskData_isLiveTileWithHeader_returns_LiveTileWithHeader() {
        val inputs =
            listOf(
                TASK_DATA,
                TASK_DATA.copy(thumbnailData = null),
                TASK_DATA.copy(isLocked = true),
                TASK_DATA.copy(title = null),
            )
        val closeCallback = View.OnClickListener {}
        val expected =
            LiveTile.WithHeader(
                header = ThumbnailHeader(TASK_ICON, TASK_TITLE_DESCRIPTION, closeCallback)
            )
        inputs.forEach { taskData ->
            val result =
                TaskUiStateMapper.toTaskThumbnailUiState(
                    taskData = taskData,
                    isLiveTile = true,
                    hasHeader = true,
                    clickCloseListener = closeCallback,
                )
            assertThat(result).isEqualTo(expected)
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun taskData_isLiveTileWithHeader_missingHeaderData_returns_LiveTileWithoutHeader() {
        val inputs =
            listOf(
                TASK_DATA.copy(icon = null),
                TASK_DATA.copy(titleDescription = null),
                TASK_DATA.copy(icon = null, titleDescription = null),
            )

        inputs.forEach { taskData ->
            val result =
                TaskUiStateMapper.toTaskThumbnailUiState(
                    taskData = taskData,
                    isLiveTile = true,
                    hasHeader = true,
                    clickCloseListener = {},
                )
            assertThat(result).isEqualTo(LiveTile.WithoutHeader)
        }
    }

    @Test
    fun taskData_isStaticTile_returns_SnapshotSplash() {
        val result =
            TaskUiStateMapper.toTaskThumbnailUiState(
                taskData = TASK_DATA,
                isLiveTile = false,
                hasHeader = false,
                clickCloseListener = null,
            )

        val expected =
            TaskThumbnailUiState.SnapshotSplash(
                snapshot =
                    Snapshot.WithoutHeader(
                        backgroundColor = TASK_BACKGROUND_COLOR,
                        bitmap = TASK_THUMBNAIL,
                        thumbnailRotation = Surface.ROTATION_0,
                    ),
                splash = TASK_ICON,
            )

        assertThat(result).isEqualTo(expected)
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun taskData_isStaticTile_withHeader_returns_SnapshotSplashWithHeader() {
        val inputs = listOf(TASK_DATA, TASK_DATA.copy(title = null))
        val closeCallback = View.OnClickListener {}
        val expected =
            TaskThumbnailUiState.SnapshotSplash(
                snapshot =
                    Snapshot.WithHeader(
                        backgroundColor = TASK_BACKGROUND_COLOR,
                        bitmap = TASK_THUMBNAIL,
                        thumbnailRotation = Surface.ROTATION_0,
                        header = ThumbnailHeader(TASK_ICON, TASK_TITLE_DESCRIPTION, closeCallback),
                    ),
                splash = TASK_ICON,
            )
        inputs.forEach { taskData ->
            val result =
                TaskUiStateMapper.toTaskThumbnailUiState(
                    taskData = taskData,
                    isLiveTile = false,
                    hasHeader = true,
                    clickCloseListener = closeCallback,
                )
            assertThat(result).isEqualTo(expected)
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun taskData_isStaticTile_missingHeaderData_returns_SnapshotSplashWithoutHeader() {
        val inputs =
            listOf(
                TASK_DATA.copy(titleDescription = null, icon = null),
                TASK_DATA.copy(titleDescription = null),
                TASK_DATA.copy(icon = null),
            )
        val expected =
            Snapshot.WithoutHeader(
                backgroundColor = TASK_BACKGROUND_COLOR,
                thumbnailRotation = Surface.ROTATION_0,
                bitmap = TASK_THUMBNAIL,
            )
        inputs.forEach { taskData ->
            val result =
                TaskUiStateMapper.toTaskThumbnailUiState(
                    taskData = taskData,
                    isLiveTile = false,
                    hasHeader = true,
                    clickCloseListener = {},
                )

            assertThat(result).isInstanceOf(TaskThumbnailUiState.SnapshotSplash::class.java)
            result as TaskThumbnailUiState.SnapshotSplash
            assertThat(result.snapshot).isEqualTo(expected)
        }
    }

    @Test
    fun taskData_thumbnailIsNull_returns_BackgroundOnly() {
        val result =
            TaskUiStateMapper.toTaskThumbnailUiState(
                taskData = TASK_DATA.copy(thumbnailData = null),
                isLiveTile = false,
                hasHeader = false,
                clickCloseListener = null,
            )

        val expected = TaskThumbnailUiState.BackgroundOnly(TASK_BACKGROUND_COLOR)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun taskData_isLocked_returns_BackgroundOnly() {
        val result =
            TaskUiStateMapper.toTaskThumbnailUiState(
                taskData = TASK_DATA.copy(isLocked = true),
                isLiveTile = false,
                hasHeader = false,
                clickCloseListener = null,
            )

        val expected = TaskThumbnailUiState.BackgroundOnly(TASK_BACKGROUND_COLOR)
        assertThat(result).isEqualTo(expected)
    }

    private companion object {
        const val TASK_TITLE_DESCRIPTION = "Title Description 1"
        var TASK_ID = 1
        val TASK_ICON = ShapeDrawable()
        val TASK_THUMBNAIL = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val TASK_THUMBNAIL_DATA =
            ThumbnailData(thumbnail = TASK_THUMBNAIL, rotation = Surface.ROTATION_0)
        val TASK_BACKGROUND_COLOR = Color.rgb(1, 2, 3)
        val TASK_DATA =
            TaskData.Data(
                TASK_ID,
                title = "Task 1",
                titleDescription = TASK_TITLE_DESCRIPTION,
                icon = TASK_ICON,
                thumbnailData = TASK_THUMBNAIL_DATA,
                backgroundColor = TASK_BACKGROUND_COLOR,
                isLocked = false,
            )
    }
}
