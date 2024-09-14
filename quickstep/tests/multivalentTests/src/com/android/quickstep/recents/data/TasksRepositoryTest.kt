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

package com.android.quickstep.recents.data

import android.content.ComponentName
import android.content.Intent
import com.android.quickstep.TaskIconCache
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.GroupTask
import com.android.systemui.shared.recents.model.Task
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class TasksRepositoryTest {
    private val tasks = (0..5).map(::createTaskWithId)
    private val defaultTaskList =
        listOf(
            GroupTask(tasks[0]),
            GroupTask(tasks[1], tasks[2], null),
            DesktopTask(tasks.subList(3, 6))
        )
    private val recentsModel = FakeRecentTasksDataSource()
    private val taskThumbnailDataSource = FakeTaskThumbnailDataSource()
    private val taskIconCache = mock<TaskIconCache>()

    private val systemUnderTest =
        TasksRepository(recentsModel, taskThumbnailDataSource, taskIconCache)

    @Test
    fun getAllTaskDataReturnsFlattenedListOfTasks() = runTest {
        recentsModel.seedTasks(defaultTaskList)

        assertThat(systemUnderTest.getAllTaskData(forceRefresh = true).first()).isEqualTo(tasks)
    }

    @Test
    fun getTaskDataByIdReturnsSpecificTask() = runTest {
        recentsModel.seedTasks(defaultTaskList)
        systemUnderTest.getAllTaskData(forceRefresh = true)

        assertThat(systemUnderTest.getTaskDataById(2).first()).isEqualTo(tasks[2])
    }

    @Test
    fun setVisibleTasksPopulatesThumbnails() = runTest {
        recentsModel.seedTasks(defaultTaskList)
        val bitmap1 = taskThumbnailDataSource.taskIdToBitmap[1]
        val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
        systemUnderTest.getAllTaskData(forceRefresh = true)

        systemUnderTest.setVisibleTasks(listOf(1, 2))

        // .drop(1) to ignore initial null content before from thumbnail was loaded.
        assertThat(systemUnderTest.getTaskDataById(1).drop(1).first()!!.thumbnail!!.thumbnail)
            .isEqualTo(bitmap1)
        assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
            .isEqualTo(bitmap2)
    }

    @Test
    fun changingVisibleTasksContainsAlreadyPopulatedThumbnails() = runTest {
        recentsModel.seedTasks(defaultTaskList)
        val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
        systemUnderTest.getAllTaskData(forceRefresh = true)

        systemUnderTest.setVisibleTasks(listOf(1, 2))

        // .drop(1) to ignore initial null content before from thumbnail was loaded.
        assertThat(systemUnderTest.getTaskDataById(2).drop(1).first()!!.thumbnail!!.thumbnail)
            .isEqualTo(bitmap2)

        // Prevent new loading of Bitmaps
        taskThumbnailDataSource.shouldLoadSynchronously = false
        systemUnderTest.setVisibleTasks(listOf(2, 3))

        assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
            .isEqualTo(bitmap2)
    }

    @Test
    fun retrievedThumbnailsAreDiscardedWhenTaskBecomesInvisible() = runTest {
        recentsModel.seedTasks(defaultTaskList)
        val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
        systemUnderTest.getAllTaskData(forceRefresh = true)

        systemUnderTest.setVisibleTasks(listOf(1, 2))

        // .drop(1) to ignore initial null content before from thumbnail was loaded.
        assertThat(systemUnderTest.getTaskDataById(2).drop(1).first()!!.thumbnail!!.thumbnail)
            .isEqualTo(bitmap2)

        // Prevent new loading of Bitmaps
        taskThumbnailDataSource.shouldLoadSynchronously = false
        systemUnderTest.setVisibleTasks(listOf(0, 1))

        assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail).isNull()
    }

    @Test
    fun retrievedThumbnailsCauseEmissionOnTaskDataFlow() = runTest {
        // Setup fakes
        recentsModel.seedTasks(defaultTaskList)
        val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
        taskThumbnailDataSource.shouldLoadSynchronously = false

        // Setup TasksRepository
        systemUnderTest.getAllTaskData(forceRefresh = true)
        systemUnderTest.setVisibleTasks(listOf(1, 2))

        // Assert there is no bitmap in first emission
        val taskFlow = systemUnderTest.getTaskDataById(2)
        val taskFlowValuesList = mutableListOf<Task?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            taskFlow.toList(taskFlowValuesList)
        }
        assertThat(taskFlowValuesList[0]!!.thumbnail).isNull()

        // Simulate bitmap loading after first emission
        taskThumbnailDataSource.taskIdToUpdatingTask.getValue(2).invoke()

        // Check for second emission
        assertThat(taskFlowValuesList[1]!!.thumbnail!!.thumbnail).isEqualTo(bitmap2)
    }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000))
}
