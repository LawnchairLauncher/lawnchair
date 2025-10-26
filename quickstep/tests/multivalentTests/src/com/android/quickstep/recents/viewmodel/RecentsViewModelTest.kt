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

package com.android.quickstep.recents.viewmodel

import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.Surface
import com.android.quickstep.recents.data.FakeTasksRepository
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RecentsViewModelTest {
    private val tasksRepository = FakeTasksRepository()
    private val recentsViewData = RecentsViewData()
    private val systemUnderTest = RecentsViewModel(tasksRepository, recentsViewData)

    private val tasks = (0..5).map(::createTaskWithId)

    @Test
    fun taskVisibilityControlThumbnailsAvailability() = runTest {
        val thumbnailData1 = createThumbnailData()
        val thumbnailData2 = createThumbnailData()
        tasksRepository.seedTasks(tasks)
        tasksRepository.seedThumbnailData(mapOf(1 to thumbnailData1, 2 to thumbnailData2))

        val thumbnailDataFlow1 = tasksRepository.getThumbnailById(1)
        val thumbnailDataFlow2 = tasksRepository.getThumbnailById(2)

        systemUnderTest.refreshAllTaskData()

        assertThat(thumbnailDataFlow1.first()).isNull()
        assertThat(thumbnailDataFlow2.first()).isNull()

        systemUnderTest.updateVisibleTasks(listOf(1, 2))

        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()).isEqualTo(thumbnailData2)

        systemUnderTest.updateVisibleTasks(listOf(1))

        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()).isNull()

        systemUnderTest.onReset()

        assertThat(thumbnailDataFlow1.first()).isNull()
        assertThat(thumbnailDataFlow2.first()).isNull()
    }

    @Test
    fun updatesRunningTaskShowScreenshot() = runTest {
        systemUnderTest.setRunningTaskShowScreenshot(true)
        systemUnderTest.waitForRunningTaskShowScreenshotToUpdate()
    }

    @Test
    fun waitForThumbnailsToUpdate() = runTest {
        // Given taskRepository with visible 2 tasks containing thumbnailData
        val thumbnailData1 = createThumbnailData().apply { snapshotId = 1 }
        val thumbnailData2 = createThumbnailData().apply { snapshotId = 2 }
        tasksRepository.seedTasks(tasks)
        tasksRepository.seedThumbnailData(mapOf(1 to thumbnailData1, 2 to thumbnailData2))
        systemUnderTest.updateVisibleTasks(listOf(1, 2))

        val thumbnailDataFlow1 = tasksRepository.getThumbnailById(1)
        val thumbnailDataFlow2 = tasksRepository.getThumbnailById(2)

        // Then getThumbnailById should initially contains correct thumbnailData
        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()).isEqualTo(thumbnailData2)

        // When thumbnailData is updated in taskRepository
        tasksRepository.seedThumbnailData(
            mapOf(1 to thumbnailData1, 2 to createThumbnailData().apply { snapshotId = 3 })
        )
        // setVisibleTasks forces FakeTasksRepository to update the flows returned by
        // getThumbnailById
        tasksRepository.setVisibleTasks(setOf(1, 2))

        // Then wait for thumbnailData should complete, and the previous getThumbnailById flow
        // should return updated values
        systemUnderTest.waitForThumbnailsToUpdate(
            mapOf(2 to createThumbnailData().apply { snapshotId = 3 })
        )
        assertThat(thumbnailDataFlow1.first()).isEqualTo(thumbnailData1)
        assertThat(thumbnailDataFlow2.first()?.snapshotId).isEqualTo(3)
    }

    @Test
    fun waitForThumbnailsToUpdate_emptyMap() = runTest {
        systemUnderTest.waitForThumbnailsToUpdate(emptyMap())
    }

    @Test
    fun waitForThumbnailsToUpdate_null() = runTest {
        systemUnderTest.waitForThumbnailsToUpdate(null)
    }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000)).apply {
            colorBackground = Color.argb(taskId, taskId, taskId, taskId)
        }

    private fun createThumbnailData(rotation: Int = Surface.ROTATION_0): ThumbnailData {
        val bitmap = mock<Bitmap>()
        whenever(bitmap.width).thenReturn(THUMBNAIL_WIDTH)
        whenever(bitmap.height).thenReturn(THUMBNAIL_HEIGHT)

        return ThumbnailData(thumbnail = bitmap, rotation = rotation)
    }

    companion object {
        const val THUMBNAIL_WIDTH = 100
        const val THUMBNAIL_HEIGHT = 200
    }
}
