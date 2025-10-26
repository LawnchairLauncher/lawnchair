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

import android.annotation.ColorInt
import android.util.Log
import androidx.core.graphics.ColorUtils
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.domain.model.TaskId
import com.android.quickstep.recents.domain.model.TaskModel
import com.android.quickstep.recents.domain.usecase.GetSysUiStatusNavFlagsUseCase
import com.android.quickstep.recents.domain.usecase.GetTaskUseCase
import com.android.quickstep.recents.domain.usecase.GetThumbnailPositionUseCase
import com.android.quickstep.recents.domain.usecase.IsThumbnailValidUseCase
import com.android.quickstep.recents.domain.usecase.ThumbnailPosition
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.ThumbnailData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * ViewModel used for [com.android.quickstep.views.TaskView],
 * [com.android.quickstep.views.DesktopTaskView] and [com.android.quickstep.views.GroupedTaskView].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskViewModel(
    private val taskViewType: TaskViewType,
    recentsViewData: RecentsViewData,
    private val getTaskUseCase: GetTaskUseCase,
    private val getSysUiStatusNavFlagsUseCase: GetSysUiStatusNavFlagsUseCase,
    private val isThumbnailValidUseCase: IsThumbnailValidUseCase,
    private val getThumbnailPositionUseCase: GetThumbnailPositionUseCase,
    dispatcherProvider: DispatcherProvider,
) {
    private var taskIds = MutableStateFlow(emptySet<Int>())

    private val isLiveTile =
        combine(
                taskIds,
                recentsViewData.runningTaskIds,
                recentsViewData.runningTaskShowScreenshot,
            ) { taskIds, runningTaskIds, runningTaskShowScreenshot ->
                runningTaskIds == taskIds && !runningTaskShowScreenshot
            }
            .distinctUntilChanged()

    private val isCentralTask =
        combine(taskIds, recentsViewData.centralTaskIds) { taskIds, centralTaskIds ->
                taskIds == centralTaskIds
            }
            .distinctUntilChanged()

    private val taskData =
        taskIds.flatMapLatest { ids ->
            // Combine Tasks requests
            combine(
                ids.map { id -> getTaskUseCase(id).map { taskModel -> id to taskModel } },
                ::mapToTaskData,
            )
        }

    private val overlayEnabled =
        combine(recentsViewData.overlayEnabled, recentsViewData.settledFullyVisibleTaskIds) {
                isOverlayEnabled,
                settledFullyVisibleTaskIds ->
                isOverlayEnabled && settledFullyVisibleTaskIds.any { it in taskIds.value }
            }
            .distinctUntilChanged()

    val state: Flow<TaskTileUiState> =
        combine(taskData, isLiveTile, overlayEnabled, isCentralTask, ::mapToTaskTile)
            .distinctUntilChanged()
            .flowOn(dispatcherProvider.background)

    fun bind(vararg taskId: TaskId) {
        taskIds.value = taskId.toSet().also { Log.d(TAG, "bind: $it") }
    }

    fun isThumbnailValid(thumbnail: ThumbnailData?, width: Int, height: Int): Boolean =
        isThumbnailValidUseCase(thumbnail, width, height)

    fun getThumbnailPosition(
        thumbnail: ThumbnailData?,
        width: Int,
        height: Int,
        isRtl: Boolean,
    ): ThumbnailPosition =
        getThumbnailPositionUseCase(
            thumbnailData = thumbnail,
            width = width,
            height = height,
            isRtl = isRtl,
        )

    private fun mapToTaskTile(
        tasks: List<TaskData>,
        isLiveTile: Boolean,
        overlayEnabled: Boolean,
        isCentralTask: Boolean,
    ): TaskTileUiState {
        val firstThumbnailData = (tasks.firstOrNull() as? TaskData.Data)?.thumbnailData
        return TaskTileUiState(
            tasks = tasks,
            isLiveTile = isLiveTile,
            hasHeader = taskViewType == TaskViewType.DESKTOP,
            sysUiStatusNavFlags = getSysUiStatusNavFlagsUseCase(firstThumbnailData),
            taskOverlayEnabled = overlayEnabled,
            isCentralTask = isCentralTask,
        )
    }

    private fun mapToTaskData(result: Array<Pair<TaskId, TaskModel?>>): List<TaskData> =
        result.map { mapToTaskData(it.first, it.second) }

    private fun mapToTaskData(taskId: TaskId, result: TaskModel?): TaskData =
        result?.let {
            TaskData.Data(
                taskId = taskId,
                title = result.title,
                titleDescription = result.titleDescription,
                icon = result.icon,
                thumbnailData = result.thumbnail,
                backgroundColor = result.backgroundColor.removeAlpha(),
                isLocked = result.isLocked,
            )
        } ?: TaskData.NoData(taskId)

    @ColorInt private fun Int.removeAlpha(): Int = ColorUtils.setAlphaComponent(this, 0xff)

    private companion object {
        const val TAG = "TaskViewModel"
    }
}
