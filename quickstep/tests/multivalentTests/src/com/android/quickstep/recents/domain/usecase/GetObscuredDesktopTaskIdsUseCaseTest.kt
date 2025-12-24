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

package com.android.quickstep.recents.domain.usecase

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.quickstep.recents.ui.viewmodel.DesktopTaskViewModel.TaskPosition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Test for [GetObscuredDesktopTaskIdsUseCase] */
@RunWith(AndroidJUnit4::class)
class GetObscuredDesktopTaskIdsUseCaseTest {

    private val useCase: GetObscuredDesktopTaskIdsUseCase = GetObscuredDesktopTaskIdsUseCase()

    @Test
    fun test_emptyTasks_returnsEmptyList() {
        val taskPositions = emptyList<TaskPosition>()

        val result = useCase.invoke(taskPositions)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_singleTask_returnsEmptyList() {
        val singleTask = TaskPosition(taskId = 1, isMinimized = false, bounds = Rect(0, 0, 10, 10))
        val taskPositions = listOf(singleTask)

        val result = useCase.invoke(taskPositions)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_smallerTaskAboveLargerTask_returnsEmptyList() {
        val task1 = TaskPosition(taskId = 1, isMinimized = false, bounds = Rect(50, 50, 150, 150))
        val task2 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(0, 0, 200, 200))
        // Tasks at the front of the list are higher in the z-order.
        val taskPositions = listOf(task1, task2)

        val result = useCase.invoke(taskPositions)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_largerTaskAboveSmallerTask_returnsSingleId() {
        val task1 = TaskPosition(taskId = 1, isMinimized = false, bounds = Rect(0, 0, 200, 200))
        val task2 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(50, 50, 150, 150))
        // Tasks at the front of the list are higher in the z-order.
        val taskPositions = listOf(task1, task2)

        val result = useCase.invoke(taskPositions)

        assertThat(result).contains(2)
    }

    @Test
    fun test_minimizedTask_returnsEmptyList() {
        // Task 1 completely envelops Task 2, but is minimized.
        val task1 = TaskPosition(taskId = 1, isMinimized = true, bounds = Rect(0, 0, 200, 200))
        val task2 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(50, 50, 150, 150))
        // Tasks at the front of the list are higher in the z-order.
        val taskPositions = listOf(task1, task2)

        val result = useCase.invoke(taskPositions)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_successiveOverlappingTasks_returnsMultipleIds() {
        val task1 = TaskPosition(taskId = 1, isMinimized = false, bounds = Rect(0, 0, 300, 300))
        val task2 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(0, 0, 200, 200))
        val task3 = TaskPosition(taskId = 3, isMinimized = false, bounds = Rect(0, 0, 100, 100))
        // Tasks at the front of the list are higher in the z-order.
        val taskPositions = listOf(task1, task2, task3)

        val result = useCase.invoke(taskPositions)

        assertThat(result).contains(2)
        assertThat(result).contains(3)
    }

    @Test
    fun test_partiallyOverlappingTasks_returnsEmptyList() {
        val task1 = TaskPosition(taskId = 1, isMinimized = false, bounds = Rect(0, 0, 100, 100))
        val task2 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(50, 0, 150, 100))
        // Tasks at the front of the list are higher in the z-order.
        val taskPositions = listOf(task1, task2)

        val result = useCase.invoke(taskPositions)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_complexOverlappingRegionObscuresTask_returnsSingleId() {
        val obscuredTask =
            TaskPosition(taskId = 0, isMinimized = false, bounds = Rect(100, 100, 200, 200))

        // Construct a complex region that completely overlaps the obscuredTask.
        val task1 = TaskPosition(taskId = 1, isMinimized = false, bounds = Rect(90, 90, 160, 210))
        val task2 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(140, 90, 210, 160))
        val task3 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(140, 140, 210, 210))

        // Tasks at the front of the list are higher in the z-order.
        val taskPositions = listOf(task1, task2, task3, obscuredTask)

        val result = useCase.invoke(taskPositions)

        assertThat(result).contains(0)
    }

    @Test
    fun test_cornersObscuredButCenterNotObscured_returnsEmptyList() {
        // Construct a layout where all four corners of a window (task5) are covered, but the center
        // is not.
        val task1 = TaskPosition(taskId = 1, isMinimized = false, bounds = Rect(0, 0, 50, 50))
        val task2 = TaskPosition(taskId = 2, isMinimized = false, bounds = Rect(150, 0, 200, 50))
        val task3 = TaskPosition(taskId = 3, isMinimized = false, bounds = Rect(0, 150, 50, 200))
        val task4 = TaskPosition(taskId = 4, isMinimized = false, bounds = Rect(150, 150, 200, 200))
        val task5 = TaskPosition(taskId = 5, isMinimized = false, bounds = Rect(25, 25, 175, 175))

        // Tasks at the front of the list are higher in the z-order.
        val taskPositions = listOf(task1, task2, task3, task4, task5)

        val result = useCase.invoke(taskPositions)

        assertThat(result).isEmpty()
    }
}
