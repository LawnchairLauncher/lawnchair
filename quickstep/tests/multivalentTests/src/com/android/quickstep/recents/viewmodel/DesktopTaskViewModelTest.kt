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

package com.android.quickstep.recents.viewmodel

import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.data.DesktopTileBackgroundRepository
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.HiddenDesktopTaskBoundsData
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.RenderedDesktopTaskBoundsData
import com.android.quickstep.recents.domain.model.DesktopTaskVisibilityData.HiddenDesktopTaskVisibilityData
import com.android.quickstep.recents.domain.model.DesktopTaskVisibilityData.RenderedDesktopTaskVisibilityData
import com.android.quickstep.recents.domain.usecase.GetObscuredDesktopTaskIdsUseCase
import com.android.quickstep.recents.domain.usecase.OrganizeDesktopTasksUseCase
import com.android.quickstep.recents.ui.viewmodel.DesktopTaskViewModel
import com.android.quickstep.recents.ui.viewmodel.DesktopTaskViewModel.TaskPosition
import com.android.quickstep.util.DesktopTask
import com.android.systemui.shared.recents.model.Task
import com.android.systemui.shared.recents.model.Task.TaskKey
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Test for [DesktopTaskViewModel] */
@RunWith(AndroidJUnit4::class)
class DesktopTaskViewModelTest {
    private val organizeDesktopTasksUseCase = mock<OrganizeDesktopTasksUseCase>()
    private val getObscuredDesktopTaskIdsUseCase = mock<GetObscuredDesktopTaskIdsUseCase>()
    private val desktopTileBackgroundRepository = mock<DesktopTileBackgroundRepository>()
    private val dispatcherProvider = mock<DispatcherProvider>()

    private lateinit var systemUnderTest: DesktopTaskViewModel

    @Before
    fun setUp() {
        systemUnderTest =
            DesktopTaskViewModel(
                organizeDesktopTasksUseCase,
                getObscuredDesktopTaskIdsUseCase,
                desktopTileBackgroundRepository,
                dispatcherProvider,
            )
    }

    @Test
    fun emptyTaskPositions() {
        whenever(getObscuredDesktopTaskIdsUseCase.invoke(any())).thenReturn(emptySet())
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(emptyList())

        systemUnderTest.bind(createDesktopTask(emptyList()))
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result).isEmpty()

        verify(getObscuredDesktopTaskIdsUseCase).invoke(emptyList())
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(emptyList()), any(), eq(emptyList()), isNull())
    }

    @Test
    fun singleRenderedAndObscuredTaskPosition() {
        whenever(getObscuredDesktopTaskIdsUseCase.invoke(any())).thenReturn(setOf(NEW_TASK_ID_1))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        systemUnderTest.bind(
            createDesktopTask(listOf(createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)))
        )
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                RenderedDesktopTaskVisibilityData(
                    isObscured = true,
                    bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
                ),
            )

        verify(getObscuredDesktopTaskIdsUseCase).invoke(listOf(NEW_TASK_POSITION_1))

        val allCurrentOriginalTaskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1)
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun singleHiddenAndObscuredTaskPosition() {
        whenever(getObscuredDesktopTaskIdsUseCase.invoke(any())).thenReturn(setOf(NEW_TASK_ID_2))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_HIDDEN_TASK_BOUNDS_DATA))

        systemUnderTest.bind(
            createDesktopTask(listOf(createTask(NEW_TASK_ID_2, NEW_TASK_BOUNDS_2)))
        )
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result)
            .containsExactly(NEW_TASK_ID_2, HiddenDesktopTaskVisibilityData(isObscured = true))

        verify(getObscuredDesktopTaskIdsUseCase).invoke(listOf(NEW_TASK_POSITION_2))

        val allCurrentOriginalTaskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_2, bounds = NEW_TASK_BOUNDS_2)
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun oneRenderedTaskOneHiddenTask() {
        whenever(getObscuredDesktopTaskIdsUseCase.invoke(any())).thenReturn(emptySet())
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(
                listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA, ORGANIZED_HIDDEN_TASK_BOUNDS_DATA)
            )

        systemUnderTest.bind(
            createDesktopTask(
                listOf(
                    createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    createTask(NEW_TASK_ID_2, NEW_TASK_BOUNDS_2),
                )
            )
        )
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                RenderedDesktopTaskVisibilityData(
                    isObscured = false,
                    bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
                ),
                NEW_TASK_ID_2,
                HiddenDesktopTaskVisibilityData(isObscured = false),
            )

        verify(getObscuredDesktopTaskIdsUseCase)
            .invoke(listOf(NEW_TASK_POSITION_1, NEW_TASK_POSITION_2))

        val allCurrentOriginalTaskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1),
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_2, bounds = NEW_TASK_BOUNDS_2),
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun oldOrganizedVisibilityData() {
        whenever(getObscuredDesktopTaskIdsUseCase.invoke(any())).thenReturn(emptySet())
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        // Store data in the system before asking it to organize tasks to see if the data is
        // converted properly.
        systemUnderTest.organizedDesktopTaskVisibilityDataMap =
            mapOf(OLD_RENDERED_TASK_VISIBILITY_DATA_PAIR, OLD_HIDDEN_TASK_VISIBILITY_DATA_PAIR)

        systemUnderTest.bind(
            createDesktopTask(listOf(createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1)))
        )
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                RenderedDesktopTaskVisibilityData(
                    isObscured = false,
                    bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
                ),
            )

        verify(getObscuredDesktopTaskIdsUseCase).invoke(listOf(NEW_TASK_POSITION_1))

        val allCurrentOriginalTaskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1)
            )
        val taskPositionsHint =
            listOf(
                RenderedDesktopTaskBoundsData(
                    taskId = OLD_RENDERED_TASK_ID,
                    bounds = OLD_RENDERED_TASK_BOUNDS,
                ),
                HiddenDesktopTaskBoundsData(taskId = OLD_HIDDEN_TASK_ID),
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(taskPositionsHint), isNull())
    }

    @Test
    fun validDismissTaskId() {
        // NEW_TASK_ID_2 will be used as the dismissed ID, so the GetObscuredDesktopTaskIdsUseCase
        // should only be invoked on the first task.
        whenever(getObscuredDesktopTaskIdsUseCase.invoke(any())).thenReturn(setOf(NEW_TASK_ID_1))
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), any()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        systemUnderTest.bind(
            createDesktopTask(
                listOf(
                    createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    createTask(NEW_TASK_ID_2, NEW_TASK_BOUNDS_2),
                )
            )
        )
        systemUnderTest.organizeDesktopTasks(
            layoutConfig = TEST_LAYOUT_CONFIG,
            dismissedTaskId = NEW_TASK_ID_2,
        )

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                RenderedDesktopTaskVisibilityData(
                    isObscured = true,
                    bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
                ),
            )

        verify(getObscuredDesktopTaskIdsUseCase).invoke(listOf(NEW_TASK_POSITION_1))

        val allCurrentOriginalTaskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1),
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_2, bounds = NEW_TASK_BOUNDS_2),
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), eq(NEW_TASK_ID_2))
    }

    @Test
    fun organizeDesktopTasks_minimizedTask_taskPositionMinimized() {
        whenever(getObscuredDesktopTaskIdsUseCase.invoke(any())).thenReturn(emptySet())
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(emptyList())

        systemUnderTest.bind(
            createDesktopTask(
                listOf(createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1, isMinimized = true))
            )
        )
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        verify(getObscuredDesktopTaskIdsUseCase).invoke(listOf(NEW_TASK_POSITION_1_MINIMIZED))
    }

    @Test
    fun organizeDesktopTasks_transparentActivityTransparentActivityStackTaskInDesktop_isHidden() {
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(listOf(ORGANIZED_RENDERED_TASK_BOUNDS_DATA))

        systemUnderTest.bind(
            createDesktopTask(
                listOf(
                    createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    createTask(
                        TRANSPARENT_TASK_ID,
                        TRANSPARENT_TASK_BOUNDS,
                        isTopActivityTransparent = true,
                        isActivityStackTransparent = true,
                    ),
                )
            )
        )
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                RenderedDesktopTaskVisibilityData(
                    isObscured = false,
                    bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
                ),
                TRANSPARENT_TASK_ID,
                HiddenDesktopTaskVisibilityData(isObscured = false),
            )

        val allCurrentOriginalTaskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1)
            )
        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    @Test
    fun organizeDesktopTasks_transparentActivityNonTransparentActivityStackTaskInDesktop_isShown() {
        whenever(organizeDesktopTasksUseCase.invoke(any(), any(), any(), isNull()))
            .thenReturn(
                listOf(
                    ORGANIZED_RENDERED_TASK_BOUNDS_DATA,
                    ORGANIZED_RENDERED_TASK_BOUNDS_DATA_FOR_SEMITRANSPARENT_TASK,
                )
            )

        systemUnderTest.bind(
            createDesktopTask(
                listOf(
                    createTask(NEW_TASK_ID_1, NEW_TASK_BOUNDS_1),
                    createTask(
                        SEMI_TRANSPARENT_TASK_ID,
                        SEMI_TRANSPARENT_TASK_BOUNDS,
                        isTopActivityTransparent = true,
                    ),
                )
            )
        )
        systemUnderTest.organizeDesktopTasks(layoutConfig = TEST_LAYOUT_CONFIG)

        val result = systemUnderTest.organizedDesktopTaskVisibilityDataMap
        assertThat(result)
            .containsExactly(
                NEW_TASK_ID_1,
                RenderedDesktopTaskVisibilityData(
                    isObscured = false,
                    bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
                ),
                SEMI_TRANSPARENT_TASK_ID,
                RenderedDesktopTaskVisibilityData(
                    isObscured = false,
                    bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
                ),
            )

        val allCurrentOriginalTaskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(taskId = NEW_TASK_ID_1, bounds = NEW_TASK_BOUNDS_1),
                RenderedDesktopTaskBoundsData(
                    taskId = SEMI_TRANSPARENT_TASK_ID,
                    bounds = SEMI_TRANSPARENT_TASK_BOUNDS,
                ),
            )

        verify(organizeDesktopTasksUseCase)
            .invoke(eq(allCurrentOriginalTaskBounds), any(), eq(emptyList()), isNull())
    }

    private fun createTask(
        id: Int,
        appBounds: Rect,
        isTopActivityTransparent: Boolean = false,
        isMinimized: Boolean = false,
        isActivityStackTransparent: Boolean = false,
    ) =
        Task().apply {
            key =
                TaskKey(
                    id,
                    /* windowingMode= */ 0,
                    Intent(),
                    ComponentName("", ""),
                    /* userId */ 0,
                    /* lastActiveTime */ 0,
                )
            this.appBounds = appBounds
            this.key.isTopActivityTransparent = isTopActivityTransparent
            this.key.isActivityStackTransparent = isActivityStackTransparent
            this.isMinimized = isMinimized
        }

    private fun createDesktopTask(tasks: List<Task>) =
        DesktopTask(deskId = 0, desktopDisplayId = 0, tasks = tasks)

    companion object {
        private val TEST_LAYOUT_CONFIG =
            DesktopLayoutConfig(
                desktopBounds = Rect(0, 0, 1000, 2000),
                topBottomMarginOneRow = 20,
                topMarginMultiRows = 20,
                bottomMarginMultiRows = 20,
                leftRightMarginOneRow = 20,
                leftRightMarginMultiRows = 20,
                horizontalPaddingBetweenTasks = 10,
                verticalPaddingBetweenTasks = 10,
                minTaskWidth = 100,
                maxRows = 4,
            )

        val NEW_TASK_ID_1 = 1
        val NEW_TASK_BOUNDS_1 = Rect(0, 0, 1, 1)
        val NEW_TASK_POSITION_1 =
            TaskPosition(taskId = NEW_TASK_ID_1, isMinimized = false, bounds = NEW_TASK_BOUNDS_1)
        val NEW_TASK_POSITION_1_MINIMIZED =
            TaskPosition(taskId = NEW_TASK_ID_1, isMinimized = true, bounds = NEW_TASK_BOUNDS_1)

        val NEW_TASK_ID_2 = 2
        val NEW_TASK_BOUNDS_2 = Rect(0, 0, 2, 2)
        val NEW_TASK_POSITION_2 =
            TaskPosition(taskId = NEW_TASK_ID_2, isMinimized = false, bounds = NEW_TASK_BOUNDS_2)

        val OLD_RENDERED_TASK_ID = -1
        val OLD_RENDERED_TASK_BOUNDS = Rect(-1, -1, 0, 0)
        val OLD_RENDERED_TASK_VISIBILITY_DATA_PAIR =
            Pair(
                OLD_RENDERED_TASK_ID,
                RenderedDesktopTaskVisibilityData(
                    isObscured = false,
                    bounds = OLD_RENDERED_TASK_BOUNDS,
                ),
            )

        val OLD_HIDDEN_TASK_ID = -2
        val OLD_HIDDEN_TASK_VISIBILITY_DATA_PAIR =
            Pair(OLD_HIDDEN_TASK_ID, HiddenDesktopTaskVisibilityData(isObscured = false))

        val ORGANIZED_RENDERED_TASK_BOUNDS = Rect(0, 0, 10, 10)
        val ORGANIZED_RENDERED_TASK_BOUNDS_DATA =
            RenderedDesktopTaskBoundsData(
                taskId = NEW_TASK_ID_1,
                bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
            )
        val ORGANIZED_HIDDEN_TASK_BOUNDS_DATA = HiddenDesktopTaskBoundsData(taskId = NEW_TASK_ID_2)

        val TRANSPARENT_TASK_ID = 3
        val TRANSPARENT_TASK_BOUNDS = Rect(0, 0, 3, 3)

        val SEMI_TRANSPARENT_TASK_ID = 4
        val SEMI_TRANSPARENT_TASK_BOUNDS = Rect(0, 0, 4, 4)
        val ORGANIZED_RENDERED_TASK_BOUNDS_DATA_FOR_SEMITRANSPARENT_TASK =
            RenderedDesktopTaskBoundsData(
                taskId = SEMI_TRANSPARENT_TASK_ID,
                bounds = ORGANIZED_RENDERED_TASK_BOUNDS,
            )
    }
}
