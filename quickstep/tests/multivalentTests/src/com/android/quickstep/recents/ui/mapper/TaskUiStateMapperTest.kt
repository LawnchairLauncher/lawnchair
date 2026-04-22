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
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.view.Surface
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_TOP_OR_LEFT
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.thumbnail.TaskHeaderUiState
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskUiStateMapperTest {

    @get:Rule val mSetFlagsRule = SetFlagsRule()

    /** TaskHeaderUiState */
    @Test
    fun taskData_isNull_returns_HideHeader() {
        val result =
            TaskUiStateMapper.toTaskHeaderState(
                taskData = null,
                hasHeader = false,
                clickCloseListener = null,
            )
        assertThat(result).isEqualTo(TaskHeaderUiState.HideHeader)
    }

    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun explodedFlagDisabled_returnsHideHeader() {
        val inputs =
            listOf(
                TASK_DATA,
                TASK_DATA.copy(thumbnailData = null),
                TASK_DATA.copy(isLocked = true),
                TASK_DATA.copy(title = null),
            )
        val closeCallback = View.OnClickListener {}
        val expected = TaskHeaderUiState.HideHeader
        inputs.forEach { taskData ->
            val result =
                TaskUiStateMapper.toTaskHeaderState(
                    taskData = taskData,
                    hasHeader = true,
                    clickCloseListener = closeCallback,
                )
            assertThat(result).isEqualTo(expected)
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun taskData_hasHeader_and_taskData_returnsShowHeader() {
        val inputs =
            listOf(
                TASK_DATA.copy(isLiveTile = true),
                TASK_DATA.copy(isLiveTile = true, thumbnailData = null),
                TASK_DATA.copy(isLiveTile = true, isLocked = true),
                TASK_DATA.copy(isLiveTile = true, title = null),
            )
        val closeCallback = View.OnClickListener {}
        val expected =
            TaskHeaderUiState.ShowHeader(
                header =
                    TaskHeaderUiState.ThumbnailHeader(
                        TASK_ICON,
                        TASK_TITLE_DESCRIPTION,
                        closeCallback,
                    )
            )
        inputs.forEach { taskData ->
            val result =
                TaskUiStateMapper.toTaskHeaderState(
                    taskData = taskData,
                    hasHeader = true,
                    clickCloseListener = closeCallback,
                )
            assertThat(result).isEqualTo(expected)
        }
    }

    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_EXPLODED_VIEW)
    @Test
    fun taskData_hasHeader_emptyTaskData_returns_HideHeader() {
        val inputs =
            listOf(
                TASK_DATA.copy(isLiveTile = true, icon = null),
                TASK_DATA.copy(isLiveTile = true, titleDescription = null),
                TASK_DATA.copy(isLiveTile = true, icon = null, titleDescription = null),
            )

        inputs.forEach { taskData ->
            val result =
                TaskUiStateMapper.toTaskHeaderState(
                    taskData = taskData,
                    hasHeader = true,
                    clickCloseListener = {},
                )
            assertThat(result).isEqualTo(TaskHeaderUiState.HideHeader)
        }
    }

    /** TaskThumbnailUiState */
    @Test
    fun taskData_isNull_returns_Uninitialized() {
        val result = TaskUiStateMapper.toTaskThumbnailUiState(taskData = null)
        assertThat(result).isEqualTo(TaskThumbnailUiState.Uninitialized)
    }

    @Test
    fun taskData_isLiveTile_returns_LiveTile() {
        val inputs =
            listOf(
                TASK_DATA.copy(isLiveTile = true),
                TASK_DATA.copy(isLiveTile = true, thumbnailData = null),
                TASK_DATA.copy(isLiveTile = true, isLocked = true),
            )
        inputs.forEach { input ->
            val result = TaskUiStateMapper.toTaskThumbnailUiState(taskData = input)
            assertThat(result).isEqualTo(LiveTile)
        }
    }

    @Test
    fun taskData_isStaticTile_returns_SnapshotSplash() {
        val result = TaskUiStateMapper.toTaskThumbnailUiState(taskData = TASK_DATA)

        val expected =
            TaskThumbnailUiState.SnapshotSplash(
                snapshot =
                    Snapshot(
                        backgroundColor = TASK_BACKGROUND_COLOR,
                        bitmap = TASK_THUMBNAIL,
                        thumbnailRotation = Surface.ROTATION_0,
                    ),
                splash = TASK_ICON,
            )

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun taskData_thumbnailIsNull_returns_BackgroundOnly() {
        val result =
            TaskUiStateMapper.toTaskThumbnailUiState(
                taskData = TASK_DATA.copy(thumbnailData = null)
            )

        val expected = TaskThumbnailUiState.BackgroundOnly(TASK_BACKGROUND_COLOR)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun taskData_isLocked_returns_BackgroundOnly() {
        val result =
            TaskUiStateMapper.toTaskThumbnailUiState(taskData = TASK_DATA.copy(isLocked = true))

        val expected = TaskThumbnailUiState.BackgroundOnly(TASK_BACKGROUND_COLOR)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun toTaskAppTimer_nullTaskData_returnsUninitialized() {
        val result =
            TaskUiStateMapper.toTaskAppTimerUiState(
                canShowAppTimer = true,
                stagePosition = STAGE_POSITION_DEFAULT,
                taskData = null,
            )

        val expected = TaskAppTimerUiState.Uninitialized
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun toTaskAppTimer_noTaskData_returnsUninitialized() {
        val result =
            TaskUiStateMapper.toTaskAppTimerUiState(
                canShowAppTimer = true,
                stagePosition = STAGE_POSITION_DEFAULT,
                taskData = TaskData.NoData(TASK_ID),
            )

        val expected = TaskAppTimerUiState.Uninitialized
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun toTaskAppTimer_canShowAppTimerFalse_returnsNoTimer() {
        val result =
            TaskUiStateMapper.toTaskAppTimerUiState(
                canShowAppTimer = false,
                stagePosition = STAGE_POSITION_DEFAULT,
                taskData = TASK_DATA,
            )

        val expected = TaskAppTimerUiState.NoTimer(taskDescription = TASK_TITLE_DESCRIPTION)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun toTaskAppTimer_timerNullAndCanShow_returnsNoTimer() {
        val result =
            TaskUiStateMapper.toTaskAppTimerUiState(
                canShowAppTimer = false,
                stagePosition = STAGE_POSITION_DEFAULT,
                taskData = TASK_DATA.copy(remainingAppTimerDuration = null),
            )

        val expected = TaskAppTimerUiState.NoTimer(taskDescription = TASK_TITLE_DESCRIPTION)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun toTaskAppTimer_timerPresentAndCanShow_returnsTimer() {
        val result =
            TaskUiStateMapper.toTaskAppTimerUiState(
                canShowAppTimer = true,
                stagePosition = STAGE_POSITION_DEFAULT,
                taskData = TASK_DATA.copy(remainingAppTimerDuration = TASK_APP_TIMER_DURATION),
            )

        val expected =
            TaskAppTimerUiState.Timer(
                timeRemaining = TASK_APP_TIMER_DURATION,
                taskDescription = TASK_DATA.titleDescription,
                taskPackageName = TASK_DATA.packageName,
                accessibilityActionId = R.id.action_digital_wellbeing_top_left,
            )
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun toTaskAppTimer_stagePositionBottomOrRight_returnsTimerWithCorrectActionId() {
        val result =
            TaskUiStateMapper.toTaskAppTimerUiState(
                canShowAppTimer = true,
                stagePosition = STAGE_POSITION_BOTTOM_OR_RIGHT,
                taskData = TASK_DATA.copy(remainingAppTimerDuration = TASK_APP_TIMER_DURATION),
            )

        val expected =
            TaskAppTimerUiState.Timer(
                timeRemaining = TASK_APP_TIMER_DURATION,
                taskDescription = TASK_DATA.titleDescription,
                taskPackageName = TASK_DATA.packageName,
                accessibilityActionId = R.id.action_digital_wellbeing_bottom_right,
            )
        assertThat(result).isEqualTo(expected)
    }

    private companion object {
        const val TASK_TITLE_DESCRIPTION = "Title Description 1"
        var TASK_ID = 1
        var PACKAGE_NAME = "com.test"
        val TASK_ICON = ShapeDrawable()
        val TASK_THUMBNAIL = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val TASK_THUMBNAIL_DATA =
            ThumbnailData(thumbnail = TASK_THUMBNAIL, rotation = Surface.ROTATION_0)
        val TASK_BACKGROUND_COLOR = Color.rgb(1, 2, 3)
        val TASK_APP_TIMER_DURATION: Duration = Duration.ofMillis(30)
        val STAGE_POSITION_DEFAULT = STAGE_POSITION_TOP_OR_LEFT
        val TASK_DATA =
            TaskData.Data(
                TASK_ID,
                packageName = PACKAGE_NAME,
                title = "Task 1",
                titleDescription = TASK_TITLE_DESCRIPTION,
                icon = TASK_ICON,
                thumbnailData = TASK_THUMBNAIL_DATA,
                backgroundColor = TASK_BACKGROUND_COLOR,
                isLocked = false,
                isLiveTile = false,
                remainingAppTimerDuration = TASK_APP_TIMER_DURATION,
            )
    }
}
