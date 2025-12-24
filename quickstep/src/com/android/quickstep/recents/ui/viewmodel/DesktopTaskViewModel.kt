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

import android.graphics.Rect
import androidx.annotation.VisibleForTesting
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.data.DesktopBackgroundResult
import com.android.quickstep.recents.data.DesktopTileBackgroundRepository
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.HiddenDesktopTaskBoundsData
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.RenderedDesktopTaskBoundsData
import com.android.quickstep.recents.domain.model.DesktopTaskVisibilityData
import com.android.quickstep.recents.domain.model.DesktopTaskVisibilityData.HiddenDesktopTaskVisibilityData
import com.android.quickstep.recents.domain.model.DesktopTaskVisibilityData.RenderedDesktopTaskVisibilityData
import com.android.quickstep.recents.domain.usecase.GetObscuredDesktopTaskIdsUseCase
import com.android.quickstep.recents.domain.usecase.OrganizeDesktopTasksUseCase
import com.android.quickstep.util.DesktopTask
import kotlinx.coroutines.withContext

/** ViewModel used for [com.android.quickstep.views.DesktopTaskView]. */
class DesktopTaskViewModel(
    private val organizeDesktopTasksUseCase: OrganizeDesktopTasksUseCase,
    private val getObscuredDesktopTaskIdsUseCase: GetObscuredDesktopTaskIdsUseCase,
    private val desktopTileBackgroundRepository: DesktopTileBackgroundRepository,
    private val dispatcherProvider: DispatcherProvider,
) {
    data class TaskPosition(val taskId: Int, val isMinimized: Boolean, val bounds: Rect)

    /** Map of desktop task IDs to positions and obscurity states. */
    var organizedDesktopTaskVisibilityDataMap = emptyMap<Int, DesktopTaskVisibilityData>()
        @VisibleForTesting set

    private var desktopTask: DesktopTask? = null

    /** Holds the default (user placed) positions of task windows. */
    var fullscreenTaskPositions: List<TaskPosition> = emptyList()
        private set

    fun bind(desktopTask: DesktopTask?) {
        this.desktopTask = desktopTask
        fullscreenTaskPositions =
            desktopTask?.tasks?.mapNotNull { task ->
                task.appBounds?.let { appBounds ->
                    TaskPosition(
                        taskId = task.key.id,
                        isMinimized = task.isMinimized,
                        bounds = appBounds,
                    )
                }
            } ?: emptyList()
    }

    /**
     * Computes new task positions using [organizeDesktopTasksUseCase] and obscured states using
     * [getObscuredDesktopTaskIdsUseCase]. The result is stored in
     * [organizedDesktopTaskVisibilityDataMap]. This is used for the exploded desktop view where the
     * use case will scale and translate tasks so that they don't overlap.
     *
     * @param layoutConfig the pre-scaled dimension configuration for the desktop layout.
     * @param dismissedTaskId Optional ID of a task being dismissed. If provided, the use case will
     *   decide whether to reflow or fully reorganize.
     */
    fun organizeDesktopTasks(layoutConfig: DesktopLayoutConfig, dismissedTaskId: Int? = null) {
        val transparentTaskIds =
            desktopTask
                ?.tasks
                ?.filter { it.key.isTopActivityTransparent && it.key.isActivityStackTransparent }
                ?.map { it.key.id } ?: emptyList()
        val defaultPositions = fullscreenTaskPositions.filterNot { it.taskId in transparentTaskIds }
        val obscuredWindowIds =
            getObscuredDesktopTaskIdsUseCase(
                defaultPositions.filterNot { it.taskId == dismissedTaskId }
            )

        // Map task IDs and DesktopTaskVisibilityData to DesktopTaskBoundsData, as the
        // organizeDesktopTasksUseCase does not need the obscured state information.
        val oldOrganizedDesktopTaskBoundsData =
            organizedDesktopTaskVisibilityDataMap.toDesktopTaskBoundsData()

        val newOrganizedDesktopTaskBoundsData =
            organizeDesktopTasksUseCase(
                allCurrentOriginalTaskBounds =
                    defaultPositions.map { RenderedDesktopTaskBoundsData(it.taskId, it.bounds) },
                layoutConfig = layoutConfig,
                taskPositionsHint = oldOrganizedDesktopTaskBoundsData,
                dismissedTaskId = dismissedTaskId,
            ) + transparentTaskIds.map { HiddenDesktopTaskBoundsData(taskId = it) }

        // Convert DesktopTaskBoundsData back to pairs of task IDs and DesktopTaskVisibilityData by
        // adding the new obscured state information to the new task bounds data.
        organizedDesktopTaskVisibilityDataMap =
            newOrganizedDesktopTaskBoundsData.toDesktopTaskVisibilityDataMap(obscuredWindowIds)
    }

    suspend fun getWallpaperBackground(forceRefresh: Boolean): DesktopBackgroundResult =
        withContext(dispatcherProvider.ioBackground) {
            desktopTileBackgroundRepository.getWallpaperBackground(forceRefresh)
        }

    private fun Map<Int, DesktopTaskVisibilityData>.toDesktopTaskBoundsData():
        List<DesktopTaskBoundsData> = map {
        when (val visibilityData = it.value) {
            is RenderedDesktopTaskVisibilityData ->
                RenderedDesktopTaskBoundsData(taskId = it.key, bounds = visibilityData.bounds)
            is HiddenDesktopTaskVisibilityData -> HiddenDesktopTaskBoundsData(it.key)
        }
    }

    private fun List<DesktopTaskBoundsData>.toDesktopTaskVisibilityDataMap(
        obscuredWindowIds: Set<Int>
    ): Map<Int, DesktopTaskVisibilityData> = associate {
        it.taskId to
            when (it) {
                is RenderedDesktopTaskBoundsData ->
                    RenderedDesktopTaskVisibilityData(
                        isObscured = obscuredWindowIds.contains(it.taskId),
                        bounds = it.bounds,
                    )
                is HiddenDesktopTaskBoundsData ->
                    HiddenDesktopTaskVisibilityData(
                        isObscured = obscuredWindowIds.contains(it.taskId)
                    )
            }
    }
}
