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
import com.android.quickstep.recents.domain.model.DesktopLayoutConfig
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Test for [OrganizeDesktopTasksUseCase] */
@RunWith(AndroidJUnit4::class)
class OrganizeDesktopTasksUseCaseTest {

    private val useCase: OrganizeDesktopTasksUseCase = OrganizeDesktopTasksUseCase()
    private val testLayoutConfig: DesktopLayoutConfig =
        DesktopLayoutConfig(
            topBottomMarginOneRow = 20,
            topMarginMultiRows = 20,
            bottomMarginMultiRows = 20,
            leftRightMarginOneRow = 20,
            leftRightMarginMultiRows = 20,
            horizontalPaddingBetweenTasks = 10,
            verticalPaddingBetweenTasks = 10,
        )

    @Test
    fun test_emptyTaskBounds_returnsEmptyList() {
        val desktopBounds = Rect(0, 0, 1000, 2000)
        val taskBounds = emptyList<DesktopTaskBoundsData>()

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_emptyDesktopBounds_returnsEmptyList() {
        val desktopBounds = Rect(0, 0, 0, 0)
        val taskBounds = listOf(DesktopTaskBoundsData(1, Rect(0, 0, 100, 100)))

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_filtersOutTasksWithEmptyBounds() {
        val desktopBounds = Rect(0, 0, 1000, 2000)
        val taskBounds =
            listOf(
                DesktopTaskBoundsData(1, Rect(0, 0, 100, 100)),
                DesktopTaskBoundsData(2, Rect()), // Empty bounds
                DesktopTaskBoundsData(3, Rect(0, 0, 50, 50)),
            )

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)
        assertThat(result)
            .isEqualTo(
                listOf(
                    DesktopTaskBoundsData(1, Rect(20, 34, 980, 995)),
                    DesktopTaskBoundsData(3, Rect(20, 1005, 980, 1966)),
                )
            )
    }

    @Test
    fun test_singleTask_isCenteredAndScaled() {
        val desktopBounds = Rect(0, 0, 1000, 2000)
        val originalAppRect = Rect(0, 0, 800, 1200)
        val taskBounds = listOf(DesktopTaskBoundsData(1, originalAppRect))

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)

        assertThat(result).hasSize(1)
        val resultBounds = result[0].bounds
        assertThat(resultBounds.width()).isGreaterThan(0)
        assertThat(resultBounds.height()).isGreaterThan(0)

        // Check aspect ratio is roughly preserved
        val originalAspectRatio = originalAppRect.width().toFloat() / originalAppRect.height()
        val resultAspectRatio = resultBounds.width().toFloat() / resultBounds.height()
        assertThat(resultAspectRatio).isWithin(0.1f).of(originalAspectRatio)

        // availableLayoutBounds will be Rect(20, 20, 980, 1980) after subtracting the margins.
        // Check if the task is centered within effective layout bounds
        val expectedTaskRect = Rect(25, 287, 975, 1713)
        assertThat(result)
            .isEqualTo(listOf(DesktopTaskBoundsData(taskId = 1, bounds = expectedTaskRect)))
    }

    @Test
    fun test_multiTasks_formRows() {
        val desktopBounds = Rect(0, 0, 1000, 2000)
        // Make tasks wide enough so they likely won't all fit in one row
        val taskRect = Rect(0, 0, 600, 400)
        val taskBounds =
            listOf(
                DesktopTaskBoundsData(1, taskRect),
                DesktopTaskBoundsData(2, taskRect),
                DesktopTaskBoundsData(3, taskRect),
            )

        val result = useCase.invoke(desktopBounds, taskBounds, testLayoutConfig)
        assertThat(result).hasSize(3)
        val bounds1 = result[0].bounds

        // Basic checks: positive dimensions, aspect ratio
        result.forEachIndexed { index, data ->
            assertThat(data.bounds.width()).isGreaterThan(0)
            assertThat(data.bounds.height()).isGreaterThan(0)
            val originalAspectRatio = taskRect.width().toFloat() / taskRect.height()
            val resultAspectRatio = data.bounds.width().toFloat() / data.bounds.height()
            assertThat(resultAspectRatio).isWithin(0.1f).of(originalAspectRatio)
        }

        // Expected bounds, based on the current implementation.
        // The tasks are expected to be arranged in 3 rows.
        val expectedTask1Bounds = Rect(20, 30, 980, 670)
        val expectedTask2Bounds = Rect(20, 680, 980, 1320)
        val expectedTask3Bounds = Rect(20, 1330, 980, 1970)
        val expectedResult =
            listOf(
                DesktopTaskBoundsData(1, expectedTask1Bounds),
                DesktopTaskBoundsData(2, expectedTask2Bounds),
                DesktopTaskBoundsData(3, expectedTask3Bounds),
            )
        assertThat(result).isEqualTo(expectedResult)
    }
}
