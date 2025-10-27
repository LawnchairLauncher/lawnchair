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
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.launcher3.util.TestDispatcherProvider
import com.android.quickstep.util.DesktopTask
import com.android.quickstep.util.SingleTask
import com.android.quickstep.util.SplitTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.ThumbnailData
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class TasksRepositoryTest {
    private val tasks = (0..5).map(::createTaskWithId)
    private val defaultTaskList =
        listOf(
            SingleTask(tasks[0]),
            SplitTask(
                tasks[1],
                tasks[2],
                SplitConfigurationOptions.SplitBounds(
                    /* leftTopBounds = */ Rect(),
                    /* rightBottomBounds = */ Rect(),
                    /* leftTopTaskId = */ -1,
                    /* rightBottomTaskId = */ -1,
                    /* snapPosition = */ SNAP_TO_2_50_50,
                ),
            ),
            DesktopTask(deskId = 0, DEFAULT_DISPLAY, tasks.subList(3, 6)),
        )
    private val recentsModel = FakeRecentTasksDataSource()
    private val taskThumbnailDataSource = FakeTaskThumbnailDataSource()
    private val taskIconDataSource = FakeTaskIconDataSource()
    private val taskVisualsChangeNotifier = FakeTaskVisualsChangeNotifier()
    private val highResLoadingStateNotifier = FakeHighResLoadingStateNotifier()
    private val taskVisualsChangedDelegate =
        spy(TaskVisualsChangedDelegateImpl(taskVisualsChangeNotifier, highResLoadingStateNotifier))

    private val dispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(dispatcher)
    private val systemUnderTest =
        TasksRepository(
            recentsModel,
            taskThumbnailDataSource,
            taskIconDataSource,
            taskVisualsChangedDelegate,
            testScope.backgroundScope,
            TestDispatcherProvider(dispatcher),
        )

    @Test
    fun getAllTaskDataReturnsFlattenedListOfTasks() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            assertThat(systemUnderTest.getAllTaskData(forceRefresh = true).first()).isEqualTo(tasks)
        }

    @Test
    fun getTaskDataByIdReturnsSpecificTask() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            assertThat(systemUnderTest.getTaskDataById(2).first()).isEqualTo(tasks[2])
        }

    @Test
    fun getThumbnailByIdReturnsNullWithNoLoadedThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            assertThat(systemUnderTest.getThumbnailById(1).first()).isNull()
        }

    @Test
    fun getCurrentThumbnailByIdReturnsNullWithNoLoadedThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            assertThat(systemUnderTest.getCurrentThumbnailById(1)).isNull()
        }

    @Test
    fun getThumbnailByIdReturnsThumbnailWithLoadedThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)
            val bitmap1 = taskThumbnailDataSource.taskIdToBitmap[1]

            systemUnderTest.setVisibleTasks(setOf(1))

            assertThat(systemUnderTest.getThumbnailById(1).first()!!.thumbnail).isEqualTo(bitmap1)
        }

    @Test
    fun whenThumbnailIsLoaded_getAllTaskData_usesPreviousLoadedThumbnailAndIcon() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)
            val bitmap1 = taskThumbnailDataSource.taskIdToBitmap[1]

            systemUnderTest.setVisibleTasks(setOf(1))
            assertThat(systemUnderTest.getThumbnailById(1).first()!!.thumbnail).isEqualTo(bitmap1)

            systemUnderTest.getAllTaskData(forceRefresh = true)
            assertThat(systemUnderTest.getThumbnailById(1).first()!!.thumbnail).isEqualTo(bitmap1)
        }

    @Test
    fun getAllTaskData_clearsPreviouslyLoadedImagesForRemovedTasks() =
        testScope.runTest {
            // Setup data
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)
            val bitmap1 = taskThumbnailDataSource.taskIdToBitmap[1]

            // Load images for task 1
            systemUnderTest.setVisibleTasks(setOf(1))
            assertThat(systemUnderTest.getThumbnailById(1).first()!!.thumbnail).isEqualTo(bitmap1)

            // Remove task 1 from "all data"
            recentsModel.seedTasks(
                defaultTaskList.filterNot { groupTask -> groupTask.tasks.any { it.key.id == 1 } }
            )
            systemUnderTest.getAllTaskData(forceRefresh = true)

            // Assert task 1 was fully removed
            assertThat(systemUnderTest.getThumbnailById(1).first()?.thumbnail).isNull()
            verify(taskVisualsChangedDelegate).unregisterTaskThumbnailChangedCallback(tasks[1].key)
        }

    @Test
    fun getCurrentThumbnailByIdReturnsThumbnailWithLoadedThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)
            val bitmap1 = taskThumbnailDataSource.taskIdToBitmap[1]

            systemUnderTest.setVisibleTasks(setOf(1))

            assertThat(systemUnderTest.getCurrentThumbnailById(1)?.thumbnail).isEqualTo(bitmap1)
        }

    @Test
    fun setVisibleTasksPopulatesThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            val bitmap1 = taskThumbnailDataSource.taskIdToBitmap[1]
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1, 2))

            assertThat(systemUnderTest.getTaskDataById(1).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap1)
            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap2)
        }

    @Test
    fun setVisibleTasksPopulatesIcons() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1, 2))

            systemUnderTest
                .getTaskDataById(1)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)
            systemUnderTest
                .getTaskDataById(2)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)
        }

    @Test
    fun changingVisibleTasksContainsAlreadyPopulatedThumbnails() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1, 2))

            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap2)

            // Prevent new loading of Bitmaps
            taskThumbnailDataSource.preventThumbnailLoad(2)
            systemUnderTest.setVisibleTasks(setOf(2, 3))

            assertThat(systemUnderTest.getTaskDataById(2).first()!!.thumbnail!!.thumbnail)
                .isEqualTo(bitmap2)
        }

    @Test
    fun changingVisibleTasksContainsAlreadyPopulatedIcons() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1, 2))

            systemUnderTest
                .getTaskDataById(2)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)

            // Prevent new loading of Drawables
            taskIconDataSource.preventIconLoad(2)
            systemUnderTest.setVisibleTasks(setOf(2, 3))

            systemUnderTest
                .getTaskDataById(2)
                .first()!!
                .assertHasIconDataFromSource(taskIconDataSource)
        }

    @Test
    fun retrievedImagesAreDiscardedWhenTaskBecomesInvisible() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1, 2))

            val task2 = systemUnderTest.getTaskDataById(2).first()!!
            assertThat(task2.thumbnail!!.thumbnail).isEqualTo(bitmap2)
            task2.assertHasIconDataFromSource(taskIconDataSource)

            systemUnderTest.setVisibleTasks(setOf(0, 1))

            val task2AfterVisibleTasksChanged = systemUnderTest.getTaskDataById(2).first()!!
            assertThat(task2AfterVisibleTasksChanged.thumbnail).isNull()
            assertThat(task2AfterVisibleTasksChanged.icon).isNull()
            assertThat(task2AfterVisibleTasksChanged.titleDescription).isNull()
            assertThat(task2AfterVisibleTasksChanged.title).isNull()
        }

    @Test
    fun retrievedThumbnailsCauseEmissionOnTaskDataFlow() =
        testScope.runTest {
            // Setup fakes
            recentsModel.seedTasks(defaultTaskList)
            val bitmap2 = taskThumbnailDataSource.taskIdToBitmap[2]

            // Setup TasksRepository
            systemUnderTest.getAllTaskData(forceRefresh = true)

            val task2DataFlow = systemUnderTest.getTaskDataById(2)
            val task2BitmapValues = mutableListOf<Bitmap?>()
            testScope.backgroundScope.launch {
                task2DataFlow.map { it?.thumbnail?.thumbnail }.toList(task2BitmapValues)
            }

            // Check for first emission
            assertThat(task2BitmapValues.single()).isNull()

            systemUnderTest.setVisibleTasks(setOf(2))
            // Check for second emission
            assertThat(task2BitmapValues).isEqualTo(listOf(null, bitmap2))
        }

    @Test
    fun onTaskThumbnailChanged_setsNewThumbnailDataOnTask() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)
            systemUnderTest.setVisibleTasks(setOf(1))

            val expectedThumbnailData = createThumbnailData()
            val expectedPreviousBitmap = taskThumbnailDataSource.taskIdToBitmap[1]
            val taskDataFlow = systemUnderTest.getTaskDataById(1)

            val task1ThumbnailValues = mutableListOf<ThumbnailData?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.thumbnail }.toList(task1ThumbnailValues)
            }
            taskVisualsChangedDelegate.onTaskThumbnailChanged(1, expectedThumbnailData)

            assertThat(task1ThumbnailValues.first()!!.thumbnail).isEqualTo(expectedPreviousBitmap)
            assertThat(task1ThumbnailValues.last()).isEqualTo(expectedThumbnailData)
        }

    @Test
    fun onHighResLoadingStateChanged_highResReplacesLowResThumbnail() =
        testScope.runTest {
            taskThumbnailDataSource.highResEnabled = false
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1))

            val expectedBitmap = mock<Bitmap>()
            val expectedPreviousBitmap = taskThumbnailDataSource.taskIdToBitmap[1]
            val taskDataFlow = systemUnderTest.getTaskDataById(1)

            val task1ThumbnailValues = mutableListOf<ThumbnailData?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.thumbnail }.toList(task1ThumbnailValues)
            }

            taskThumbnailDataSource.taskIdToBitmap[1] = expectedBitmap
            taskThumbnailDataSource.highResEnabled = true
            taskVisualsChangedDelegate.onHighResLoadingStateChanged(true)

            val firstThumbnailValue = task1ThumbnailValues.first()!!
            assertThat(firstThumbnailValue.thumbnail).isEqualTo(expectedPreviousBitmap)
            assertThat(firstThumbnailValue.reducedResolution).isTrue()

            val lastThumbnailValue = task1ThumbnailValues.last()!!
            assertThat(lastThumbnailValue.thumbnail).isEqualTo(expectedBitmap)
            assertThat(lastThumbnailValue.reducedResolution).isFalse()
        }

    @Test
    fun onHighResLoadingStateChanged_invisibleTaskIgnored() =
        testScope.runTest {
            taskThumbnailDataSource.highResEnabled = false
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1))

            val invisibleTaskId = 2
            val taskDataFlow = systemUnderTest.getTaskDataById(invisibleTaskId)

            val task2ThumbnailValues = mutableListOf<ThumbnailData?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.thumbnail }.toList(task2ThumbnailValues)
            }

            taskThumbnailDataSource.highResEnabled = true
            taskVisualsChangedDelegate.onHighResLoadingStateChanged(true)

            assertThat(task2ThumbnailValues.filterNotNull()).isEmpty()
            assertThat(taskThumbnailDataSource.getNumberOfGetThumbnailCalls(2)).isEqualTo(0)
        }

    @Test
    fun onHighResLoadingStateChanged_lowResDoesNotReplaceHighResThumbnail() =
        testScope.runTest {
            taskThumbnailDataSource.highResEnabled = true
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1))

            val expectedBitmap = mock<Bitmap>()
            val expectedPreviousBitmap = taskThumbnailDataSource.taskIdToBitmap[1]
            val taskDataFlow = systemUnderTest.getTaskDataById(1)

            val task1ThumbnailValues = mutableListOf<ThumbnailData?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.thumbnail }.toList(task1ThumbnailValues)
            }

            taskThumbnailDataSource.taskIdToBitmap[1] = expectedBitmap
            taskThumbnailDataSource.highResEnabled = false
            taskVisualsChangedDelegate.onHighResLoadingStateChanged(false)

            val firstThumbnailValue = task1ThumbnailValues.first()!!
            assertThat(firstThumbnailValue.thumbnail).isEqualTo(expectedPreviousBitmap)
            assertThat(firstThumbnailValue.reducedResolution).isFalse()

            val lastThumbnailValue = task1ThumbnailValues.last()!!
            assertThat(lastThumbnailValue.thumbnail).isEqualTo(expectedPreviousBitmap)
            assertThat(lastThumbnailValue.reducedResolution).isFalse()
        }

    @Test
    fun onTaskIconChanged_setsNewIconOnTask() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            systemUnderTest.setVisibleTasks(setOf(1))

            val expectedIcon = FakeTaskIconDataSource.mockCopyableDrawable()
            val expectedPreviousIcon = taskIconDataSource.taskIdToDrawable[1]
            val taskDataFlow = systemUnderTest.getTaskDataById(1)

            val task1IconValues = mutableListOf<Drawable?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.icon }.toList(task1IconValues)
            }
            taskIconDataSource.taskIdToDrawable[1] = expectedIcon
            taskVisualsChangedDelegate.onTaskIconChanged(1)

            assertThat(task1IconValues.first()).isEqualTo(expectedPreviousIcon)
            assertThat(task1IconValues.last()).isEqualTo(expectedIcon)
        }

    @Test
    fun setVisibleTasks_multipleTimesWithDifferentTasks_reusesThumbnailRequests() =
        testScope.runTest {
            recentsModel.seedTasks(defaultTaskList)
            systemUnderTest.getAllTaskData(forceRefresh = true)

            val taskDataFlow = systemUnderTest.getTaskDataById(1)
            val task1IconValues = mutableListOf<Drawable?>()
            testScope.backgroundScope.launch {
                taskDataFlow.map { it?.icon }.toList(task1IconValues)
            }

            systemUnderTest.setVisibleTasks(setOf(1))
            assertThat(taskThumbnailDataSource.getNumberOfGetThumbnailCalls(1)).isEqualTo(1)

            systemUnderTest.setVisibleTasks(setOf(1, 2))
            assertThat(taskThumbnailDataSource.getNumberOfGetThumbnailCalls(1)).isEqualTo(1)
        }

    private fun createTaskWithId(taskId: Int) =
        Task(Task.TaskKey(taskId, 0, Intent(), ComponentName("", ""), 0, 2000))

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
