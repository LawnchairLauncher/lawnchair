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

import android.view.View.OnClickListener
import com.android.launcher3.Flags.enableDesktopExplodedView
import com.android.launcher3.R
import com.android.launcher3.util.SplitConfigurationOptions.STAGE_POSITION_BOTTOM_OR_RIGHT
import com.android.quickstep.recents.ui.viewmodel.TaskData
import com.android.quickstep.task.apptimer.TaskAppTimerUiState
import com.android.quickstep.task.thumbnail.TaskHeaderUiState
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.SnapshotSplash
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized

object TaskUiStateMapper {

    /**
     * Converts a [TaskData] object into a [TaskHeaderUiState] for display in the UI.
     *
     * This function handles different types of [TaskData] and determines the appropriate UI state
     * based on the data and provided flags.
     *
     * @param taskData The [TaskData] to convert. Can be null or a specific subclass.
     * @param hasHeader A flag indicating whether the UI should display a header.
     * @param clickCloseListener A callback when the close button in the UI is clicked.
     * @return A [TaskHeaderUiState] representing the UI state for the given task data.
     */
    fun toTaskHeaderState(
        taskData: TaskData?,
        hasHeader: Boolean,
        clickCloseListener: OnClickListener?,
    ): TaskHeaderUiState =
        when {
            taskData !is TaskData.Data -> TaskHeaderUiState.HideHeader
            canHeaderBeCreated(taskData, hasHeader, clickCloseListener) -> {
                TaskHeaderUiState.ShowHeader(
                    TaskHeaderUiState.ThumbnailHeader(
                        // TODO(http://b/353965691): figure out what to do when `icon` or
                        // `titleDescription` is null.
                        taskData.icon!!,
                        taskData.titleDescription!!,
                        clickCloseListener!!,
                    )
                )
            }
            else -> TaskHeaderUiState.HideHeader
        }

    /**
     * Converts a [TaskData] object into a [TaskThumbnailUiState] for display in the UI.
     *
     * This function handles different types of [TaskData] and determines the appropriate UI state
     * based on the data and provided flags.
     *
     * @param taskData The [TaskData] to convert. Can be null or a specific subclass.
     * @param isLiveTile A flag indicating whether the task data represents live tile.
     * @return A [TaskThumbnailUiState] representing the UI state for the given task data.
     */
    fun toTaskThumbnailUiState(taskData: TaskData?): TaskThumbnailUiState =
        when {
            taskData !is TaskData.Data -> Uninitialized
            taskData.isLiveTile -> LiveTile
            isBackgroundOnly(taskData) -> BackgroundOnly(taskData.backgroundColor)
            isSnapshotSplash(taskData) ->
                SnapshotSplash(
                    Snapshot(
                        taskData.thumbnailData?.thumbnail!!,
                        taskData.thumbnailData.rotation,
                        taskData.backgroundColor,
                    ),
                    taskData.icon,
                )

            else -> Uninitialized
        }

    private fun isBackgroundOnly(taskData: TaskData.Data) =
        taskData.isLocked || taskData.thumbnailData == null

    private fun isSnapshotSplash(taskData: TaskData.Data) =
        taskData.thumbnailData?.thumbnail != null && !taskData.isLocked

    private fun canHeaderBeCreated(
        taskData: TaskData.Data,
        hasHeader: Boolean,
        clickCloseListener: OnClickListener?,
    ) =
        enableDesktopExplodedView() &&
            hasHeader &&
            taskData.icon != null &&
            taskData.titleDescription != null &&
            clickCloseListener != null

    /**
     * Converts a [TaskData] object into a [TaskAppTimerUiState] for displaying an app timer toast
     *
     * @property taskData The [TaskData] to convert. Can be null or a specific sub-class.
     * @property stagePosition the position of this task when shown as a group
     * @return a [TaskAppTimerUiState] representing state for the information displayed in the app
     *   timer toast.
     */
    fun toTaskAppTimerUiState(
        canShowAppTimer: Boolean,
        stagePosition: Int,
        taskData: TaskData?,
    ): TaskAppTimerUiState =
        when {
            taskData !is TaskData.Data -> TaskAppTimerUiState.Uninitialized

            !canShowAppTimer || taskData.remainingAppTimerDuration == null ->
                TaskAppTimerUiState.NoTimer(taskDescription = taskData.titleDescription)

            else ->
                TaskAppTimerUiState.Timer(
                    taskDescription = taskData.titleDescription,
                    timeRemaining = taskData.remainingAppTimerDuration,
                    taskPackageName = taskData.packageName,
                    accessibilityActionId =
                        if (stagePosition == STAGE_POSITION_BOTTOM_OR_RIGHT) {
                            R.id.action_digital_wellbeing_bottom_right
                        } else {
                            R.id.action_digital_wellbeing_top_left
                        },
                )
        }
}
