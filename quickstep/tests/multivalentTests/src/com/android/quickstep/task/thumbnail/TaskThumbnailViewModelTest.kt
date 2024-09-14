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

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.quickstep.recents.viewmodel.RecentsViewData
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.BackgroundOnly
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.LiveTile
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Snapshot
import com.android.quickstep.task.thumbnail.TaskThumbnailUiState.Uninitialized
import com.android.quickstep.task.viewmodel.TaskViewData
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TaskThumbnailViewModelTest {
    private val recentsViewData = RecentsViewData()
    private val taskViewData = TaskViewData()
    private val tasksRepository = FakeTasksRepository()
    private val systemUnderTest =
        TaskThumbnailViewModel(recentsViewData, taskViewData, tasksRepository)

    private val tasks = (0..5).map(::createTaskWithId)

    @Test
    fun initialStateIsUninitialized() = runTest {
        assertThat(systemUnderTest.uiState.first()).isEqualTo(Uninitialized)
    }

    @Test
    fun bindRunningTask_thenStateIs_LiveTile() = runTest {
        tasksRepository.seedTasks(tasks)
        val taskThumbnail = TaskThumbnail(taskId = 1, isRunning = true)
        systemUnderTest.bind(taskThumbnail)

        assertThat(systemUnderTest.uiState.first()).isEqualTo(LiveTile)
    }

    @Test
    fun setRecentsFullscreenProgress_thenProgressIsPassedThrough() = runTest {
        recentsViewData.fullscreenProgress.value = 0.5f

        assertThat(systemUnderTest.recentsFullscreenProgress.first()).isEqualTo(0.5f)

        recentsViewData.fullscreenProgress.value = 0.6f

        assertThat(systemUnderTest.recentsFullscreenProgress.first()).isEqualTo(0.6f)
    }

    @Test
    fun setAncestorScales_thenScaleIsCalculated() = runTest {
        recentsViewData.scale.value = 0.5f
        taskViewData.scale.value = 0.6f

        assertThat(systemUnderTest.inheritedScale.first()).isEqualTo(0.3f)
    }

    @Test
    fun bindRunningTaskThenStoppedTaskWithoutThumbnail_thenStateChangesToBackgroundOnly() =
        runTest {
            tasksRepository.seedTasks(tasks)
            val runningTask = TaskThumbnail(taskId = 1, isRunning = true)
            val stoppedTask = TaskThumbnail(taskId = 2, isRunning = false)
            systemUnderTest.bind(runningTask)
            assertThat(systemUnderTest.uiState.first()).isEqualTo(LiveTile)

            systemUnderTest.bind(stoppedTask)
            assertThat(systemUnderTest.uiState.first())
                .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
        }

    @Test
    fun bindStoppedTaskWithoutThumbnail_thenStateIs_BackgroundOnly_withAlphaRemoved() = runTest {
        tasksRepository.seedTasks(tasks)
        val stoppedTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(stoppedTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
    }

    @Test
    fun bindLockedTaskWithThumbnail_thenStateIs_BackgroundOnly() = runTest {
        tasksRepository.seedThumbnailData(mapOf(2 to createThumbnailData()))
        tasks[2].isLocked = true
        tasksRepository.seedTasks(tasks)
        val recentTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
    }

    @Test
    fun bindStoppedTaskWithThumbnail_thenStateIs_Snapshot_withAlphaRemoved() = runTest {
        val expectedThumbnailData = createThumbnailData()
        tasksRepository.seedThumbnailData(mapOf(2 to expectedThumbnailData))
        tasksRepository.seedTasks(tasks)
        tasksRepository.setVisibleTasks(listOf(2))
        val recentTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(
                Snapshot(
                    backgroundColor = Color.rgb(2, 2, 2),
                    bitmap = expectedThumbnailData.thumbnail!!,
                    drawnRect = Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                )
            )
    }

    @Test
    fun bindNonVisibleStoppedTask_whenMadeVisible_thenStateIsSnapshot() = runTest {
        val expectedThumbnailData = createThumbnailData()
        tasksRepository.seedThumbnailData(mapOf(2 to expectedThumbnailData))
        tasksRepository.seedTasks(tasks)
        val recentTask = TaskThumbnail(taskId = 2, isRunning = false)

        systemUnderTest.bind(recentTask)
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(BackgroundOnly(backgroundColor = Color.rgb(2, 2, 2)))
        tasksRepository.setVisibleTasks(listOf(2))
        assertThat(systemUnderTest.uiState.first())
            .isEqualTo(
                Snapshot(
                    backgroundColor = Color.rgb(2, 2, 2),
                    bitmap = expectedThumbnailData.thumbnail!!,
                    drawnRect = Rect(0, 0, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT)
                )
            )
    }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
            colorBackground = Color.argb(taskId, taskId, taskId, taskId)
        }

    private fun createThumbnailData(): ThumbnailData {
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(THUMBNAIL_WIDTH)
        whenever(bitmap.height).thenReturn(THUMBNAIL_HEIGHT)

        return ThumbnailData(thumbnail = bitmap)
    }

    companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
    }
}
