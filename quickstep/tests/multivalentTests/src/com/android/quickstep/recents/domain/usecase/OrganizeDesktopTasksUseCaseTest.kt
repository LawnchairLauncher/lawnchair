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
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.HiddenDesktopTaskBoundsData
import com.android.quickstep.recents.domain.model.DesktopTaskBoundsData.RenderedDesktopTaskBoundsData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Test for [OrganizeDesktopTasksUseCase] */
@RunWith(AndroidJUnit4::class)
class OrganizeDesktopTasksUseCaseTest {

    private val useCase: OrganizeDesktopTasksUseCase = OrganizeDesktopTasksUseCase()

    @Test
    fun test_emptyTaskBounds_returnsEmptyList() {
        val taskBounds = emptyList<RenderedDesktopTaskBoundsData>()

        val result = useCase.invoke(taskBounds, TEST_LAYOUT_CONFIG)

        assertThat(result).isEmpty()
    }

    @Test
    fun test_emptyDesktopBounds_returnsHiddenTaskData() {
        val desktopBounds = Rect(0, 0, 0, 0)
        val taskBounds = listOf(RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 100)))
        val expected = listOf(HiddenDesktopTaskBoundsData(1))
        val layoutConfig = TEST_LAYOUT_CONFIG.copy(desktopBounds = desktopBounds)

        val result = useCase.invoke(taskBounds, layoutConfig)

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun test_singleTask_isCenteredAndScaled() {
        val originalAppRect = Rect(0, 0, 800, 1200)
        val taskBounds = listOf(RenderedDesktopTaskBoundsData(1, originalAppRect))

        val result = useCase.invoke(taskBounds, TEST_LAYOUT_CONFIG)

        assertThat(result).hasSize(1)
        val renderedTask = result[0] as RenderedDesktopTaskBoundsData
        val resultBounds = renderedTask.bounds
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
            .isEqualTo(listOf(RenderedDesktopTaskBoundsData(taskId = 1, bounds = expectedTaskRect)))
    }

    @Test
    fun test_multiTasks_formRows() {
        // Make tasks wide enough so they likely won't all fit in one row
        val taskRect = Rect(0, 0, 600, 400)
        val taskBounds =
            listOf(
                RenderedDesktopTaskBoundsData(1, taskRect),
                RenderedDesktopTaskBoundsData(2, taskRect),
                RenderedDesktopTaskBoundsData(3, taskRect),
            )

        val result = useCase.invoke(taskBounds, TEST_LAYOUT_CONFIG)
        assertThat(result).hasSize(3)
        val bounds1 = (result[0] as RenderedDesktopTaskBoundsData).bounds

        // Basic checks: positive dimensions, aspect ratio
        result.forEachIndexed { index, data ->
            val renderedData = data as RenderedDesktopTaskBoundsData
            assertThat(renderedData.bounds.width()).isGreaterThan(0)
            assertThat(renderedData.bounds.height()).isGreaterThan(0)
            val originalAspectRatio = taskRect.width().toFloat() / taskRect.height()
            val resultAspectRatio =
                renderedData.bounds.width().toFloat() / renderedData.bounds.height()
            assertThat(resultAspectRatio).isWithin(0.1f).of(originalAspectRatio)
        }

        // Expected bounds, based on the current implementation.
        // The tasks are expected to be arranged in 3 rows.
        val expectedTask1Bounds = Rect(20, 30, 980, 670)
        val expectedTask2Bounds = Rect(20, 680, 980, 1320)
        val expectedTask3Bounds = Rect(20, 1330, 980, 1970)
        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, expectedTask1Bounds),
                RenderedDesktopTaskBoundsData(2, expectedTask2Bounds),
                RenderedDesktopTaskBoundsData(3, expectedTask3Bounds),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun test_maxRows_limitsNumberOfRowsEffectively() {
        val desktopBounds = Rect(0, 0, 1000, 550) // Height is somewhat constrained
        val taskRect = Rect(0, 0, 200, 100) // Aspect ratio 2:1
        val tasks =
            listOf(
                RenderedDesktopTaskBoundsData(1, taskRect),
                RenderedDesktopTaskBoundsData(2, taskRect),
                RenderedDesktopTaskBoundsData(3, taskRect),
            )

        // For simplicity, configure maxRows = 1.
        // Effective layout height for multi-row (or single-row if margins are same):
        // 550 - 20 (topMargin) - 20 (bottomMargin) = 510
        // Effective layout width for multi-row:
        // 1000 - 20 (leftMargin) - (20-10) (rightNetMargin from leftRightMarginMultiRows -
        // horizontalPadding) = 970
        // availableLayoutBounds for the main layout logic will be Rect(20, 20, 990, 530)
        //
        // With maxRows = 1, verticalPaddingBetweenTasks = 10:
        // getMinTaskHeightGivenMaxRows = ((510 - 1*10) / (1+1)) + 1 = (500/2) + 1 = 251.
        // This acts as a lower bound for the optimalHeight bisection.
        //
        // If optimalHeight is determined to be 251 (original task aspect ratio 200:100):
        //   Scaled task width = 251 * (200/100) = 502.
        //   Horizontally, in fitWindowRectsInBounds, for the first task:
        //     left (20) + width (502) + horizontalPadding (10) = 532. This fits within
        // layoutBounds.right (990).
        //   For the second task on the same row:
        //     new_left (532) + width (502) + horizontalPadding (10) = 1044. This exceeds
        // layoutBounds.right (990).
        //   So, second task attempts to move to a new row.
        //   New row top = old_top (20) + optimalWindowHeight (251) + verticalPadding (10) = 281.
        //   Check if new row fits vertically: (new_row_top + optimalWindowHeight) >
        // layoutBounds.bottom
        //     (281 + 251) > 530  => 532 > 530. It does NOT fit.
        //   Thus, fitWindowRectsInBounds sets allWindowsFit = false and only Task 1 gets bounds.
        //
        // Expected: Task 1 gets bounds and centered, Tasks 2 and 3 get a small bounds in the center
        // of the screen.
        // Expected Rect for Task 1: Rect(25, 37 - 975, 513) after relayout to calculate the optimal
        // height to fit one window.
        val config =
            TEST_LAYOUT_CONFIG.copy(
                desktopBounds = desktopBounds,
                // testLayoutConfig has topBottomMarginOneRow = 20
                maxRows = 1,
                minTaskWidth = 50, // Low enough not to dominate height calculation
                verticalPaddingBetweenTasks = 10,
                topMarginMultiRows = 20,
                bottomMarginMultiRows = 20,
                leftRightMarginMultiRows = 20,
                horizontalPaddingBetweenTasks = 10,
            )

        val result = useCase.invoke(tasks, config)

        val expected =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(25, 37, 975, 513)),
                HiddenDesktopTaskBoundsData(2),
                HiddenDesktopTaskBoundsData(3),
            )
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun removeTask_fromEmptyLayout_returnsEmptyList_merged() {
        val currentLayout = emptyList<RenderedDesktopTaskBoundsData>()
        val taskIdToRemove = 1

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = null,
                dismissedTaskId = taskIdToRemove,
            )
        assertThat(result).isEmpty()
    }

    @Test
    fun removeTask_whenTaskNotFoundInSingleItemLayout_returnsOriginalLayout_merged() {
        val currentLayout = listOf(RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)))
        val taskIdToRemove = 2 // Task not in currentLayout

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                dismissedTaskId = taskIdToRemove,
            )
        // The new invoke logic might return a full re-organization if task not found for reflow.
        // If task to remove is not found, performReflowRebalance returns currentLayout.
        // The orchestrator invoke will call performFullOrganization on currentLayout (all tasks).
        // So, the result should be the organized version of currentLayout.
        val expectedResult =
            useCase.invoke(currentLayout, DEFAULT_LAYOUT_CONFIG, dismissedTaskId = null)
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ]
    // [ T2 ]
    // [ T3 ]
    // After (removing T1):
    // [ T2 ]
    // [ T3 ]
    @Test
    fun removeFirstRow_fromLayoutWithThreeRowsSingleColumn_rebalancesRemainingTwo_merged() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 420, 100, 620)),
            )
        val taskIdToRemove = 1

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = currentLayout,
                dismissedTaskId = taskIdToRemove,
            )

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(2, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 315, 100, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ]
    // [ T2 ]
    // [ T3 ]
    // After (removing T2):
    // [ T1 ]
    // [ T3 ]
    @Test
    fun removeMiddleRow_fromLayoutWithThreeRowsSingleColumn_rebalancesRemainingTwo_merged() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 420, 100, 620)),
            )
        val taskIdToRemove = 2

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = currentLayout,
                dismissedTaskId = taskIdToRemove,
            )

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 315, 100, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ]
    // [ T2 ]
    // [ T3 ]
    // After (removing T3):
    // [ T1 ]
    // [ T2 ]
    @Test
    fun removeLastRow_fromLayoutWithThreeRowsSingleColumn_rebalancesRemainingTwo_merged() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 420, 100, 620)),
            )
        val taskIdToRemove = 3

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = currentLayout,
                dismissedTaskId = taskIdToRemove,
            )

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(2, Rect(0, 315, 100, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ]
    // [ T3 ]
    // [ T4 ] [ T5 ]
    // After (removing T3):
    // [ T1 ] [ T2 ]
    // [ T4 ] [ T5 ]
    @Test
    fun removeMiddleRowWithSingleTask_fromLayoutWithThreeRowsMixedColumns_rebalancesRemainingTwoRows_merged() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(0, 210, 100, 410)),
                RenderedDesktopTaskBoundsData(4, Rect(0, 420, 100, 620)),
                RenderedDesktopTaskBoundsData(5, Rect(110, 420, 210, 620)),
            )
        val taskIdToRemove = 3

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = currentLayout,
                dismissedTaskId = taskIdToRemove,
            )

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 105, 100, 305)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 105, 210, 305)),
                RenderedDesktopTaskBoundsData(4, Rect(0, 315, 100, 515)),
                RenderedDesktopTaskBoundsData(5, Rect(110, 315, 210, 515)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ] [ T3 ]
    // After (removing T1):
    // [ T2 ] [ T3 ]
    @Test
    fun removeFirstTask_fromLayoutWithSingleRowThreeColumns_rebalancesRemainingTwo_merged() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(220, 0, 320, 200)),
            )
        val taskIdToRemove = 1

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = currentLayout,
                dismissedTaskId = taskIdToRemove,
            )

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(2, Rect(55, 0, 155, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(165, 0, 265, 200)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ] [ T3 ]
    // After (removing T2):
    // [ T1 ] [ T3 ]
    @Test
    fun removeMiddleTask_fromLayoutWithSingleRowThreeColumns_rebalancesRemainingTwo_merged() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(220, 0, 320, 200)),
            )
        val taskIdToRemove = 2

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = currentLayout,
                dismissedTaskId = taskIdToRemove,
            )

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(55, 0, 155, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(165, 0, 265, 200)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Before:
    // [ T1 ] [ T2 ] [ T3 ]
    // After (removing T3):
    // [ T1 ] [ T2 ]
    @Test
    fun removeLastTask_fromLayoutWithSingleRowThreeColumns_rebalancesRemainingTwo_merged() {
        val currentLayout =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(0, 0, 100, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(110, 0, 210, 200)),
                RenderedDesktopTaskBoundsData(3, Rect(220, 0, 320, 200)),
            )
        val taskIdToRemove = 3

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = currentLayout,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = currentLayout,
                dismissedTaskId = taskIdToRemove,
            )

        val expectedResult =
            listOf(
                RenderedDesktopTaskBoundsData(1, Rect(55, 0, 155, 200)),
                RenderedDesktopTaskBoundsData(2, Rect(165, 0, 265, 200)),
            )
        assertThat(result).isEqualTo(expectedResult)
    }

    // Helper for creating RenderedDesktopTaskBoundsData
    private fun createRenderedData(taskId: Int, l: Int, t: Int, r: Int, b: Int) =
        RenderedDesktopTaskBoundsData(taskId, Rect(l, t, r, b))

    // Helper for creating HiddenDesktopTaskBoundsData
    private fun createHiddenData(taskId: Int) = HiddenDesktopTaskBoundsData(taskId)

    @Test
    fun dismissOnlyTask_resultsInEmptyLayout() {
        val task1Orig = createRenderedData(1, 0, 0, 100, 100)
        val allOriginalTasks = listOf(task1Orig)
        val previousLayout =
            listOf(createRenderedData(1, 10, 10, 110, 110)) // Organized version of task1Orig
        val dismissedTaskId = 1

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = allOriginalTasks,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = previousLayout,
                dismissedTaskId = dismissedTaskId,
            )

        assertThat(result).isEmpty()
    }

    @Test
    fun dismissTask_whenPreviousLayoutIsEmpty_performsFullOrganizationOnRemaining() {
        val task1Orig = createRenderedData(1, 0, 0, 100, 100)
        val task2Orig = createRenderedData(2, 100, 0, 200, 100)
        val allOriginalTasks = listOf(task1Orig, task2Orig)
        val dismissedTaskId = 1

        val remainingOriginalTaskBounds = listOf(task2Orig)

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = allOriginalTasks,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = null,
                dismissedTaskId = dismissedTaskId,
            )

        // Expected: performFullOrganization on remainingOriginalTaskBounds ([task2Orig])
        val expectedResultByFullOrganization =
            useCase.invoke(
                allCurrentOriginalTaskBounds = remainingOriginalTaskBounds,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = null,
                dismissedTaskId = null,
            )
        assertThat(result).isEqualTo(expectedResultByFullOrganization)
    }

    @Test
    fun dismissHiddenTask_returnsRemainingPreviousLayoutWithoutRelayout() {
        val task1Orig = createRenderedData(1, 0, 0, 200, 200) // Will be rendered
        val task2Orig = createRenderedData(2, 0, 0, 100, 100) // Will be hidden in previousLayout

        val allOriginalTasks = listOf(task1Orig, task2Orig)
        // Assume task2 was hidden in the previous layout
        val previousLayout = listOf(createRenderedData(1, 10, 10, 510, 510), createHiddenData(2))
        val dismissedTaskId = 2 // Dismissing the hidden task

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = allOriginalTasks,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = previousLayout,
                dismissedTaskId = dismissedTaskId,
            )

        // Expected: remainingPreviousOrganizedTaskPosition, which is [r(1, 10, 10, 510, 510)]
        assertThat(result).containsExactly(createRenderedData(1, 10, 10, 510, 510))
    }

    @Test
    fun dismissRenderedTask_previousHadHidden_relayoutChangesVisibility_performsFullOrganization() {
        // T1 (small), T2 (small), T3 (large enough to be hidden with T1, T2 present)
        // Previous: R_T1, R_T2, H_T3. Dismiss T1.
        // Remaining original: T2, T3. Full org on (T2, T3) should render both.
        val task1Orig = createRenderedData(1, 0, 0, 100, 100)
        val task2Orig = createRenderedData(2, 100, 0, 200, 100)
        val task3Orig = createRenderedData(3, 0, 100, 300, 400) // Large task

        val allOriginalTasks = listOf(task1Orig, task2Orig, task3Orig)

        // Setup previousLayout: T1, T2 are rendered, T3 is hidden.
        // This requires a desktopBound that would actually hide T3 if T1,T2 are present.
        // For simplicity, we manually construct previousOrganizedDesktopTaskPositions.
        // Let's assume T1 and T2 took up all space, forcing T3 to be hidden.
        val prevRenderedT1 = createRenderedData(1, 10, 10, 110, 110)
        val prevRenderedT2 = createRenderedData(2, 120, 10, 220, 110)
        val prevHiddenT3 = createHiddenData(3)
        val previousLayout = listOf(prevRenderedT1, prevRenderedT2, prevHiddenT3)

        val dismissedTaskId = 1
        val remainingOriginalTasks = listOf(task2Orig, task3Orig)

        // Mocking the behavior of performFullOrganization for this specific scenario:
        // Assume that when only T2 and T3 are present, they can both be rendered.
        // This means the set of rendered tasks changes from {T2} (in remainingPrevious) to {T2,
        // T3}.
        // So, the use case should return the result of full organization on remainingOriginalTasks.

        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = allOriginalTasks,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = previousLayout,
                dismissedTaskId = dismissedTaskId,
            )

        // Expected: result of performFullOrganization on remainingOriginalTasks ([task2Orig,
        // task3Orig])
        val expectedResultByFullOrganization =
            useCase.invoke(
                allCurrentOriginalTaskBounds = remainingOriginalTasks,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = null, // Not used for this call path
                dismissedTaskId = null,
            )

        assertThat(result).isEqualTo(expectedResultByFullOrganization)
        // Additionally, verify that T3 is now rendered.
        assertThat(result.any { it.taskId == 3 && it is RenderedDesktopTaskBoundsData }).isTrue()
    }

    @Test
    fun dismissRenderedTask_previousHadHidden_relayoutKeepsSameVisibility_performsReflowAndPreservesHidden() {
        // T1 (small), T2 (small), T3 (very large, will stay hidden even if T1 is dismissed)
        // Previous: R_T1, R_T2, H_T3. Dismiss T1.
        // Remaining original: T2, T3. Full org on (T2, T3) should be R_T2, H_T3.
        // Rendered set {T2} is same as in remainingPrevious. So, reflow R_T1, R_T2, removing T1.
        // H_T3 should now be preserved.

        val task1Orig = createRenderedData(1, 0, 0, 100, 100) // Small, will be rendered
        val task2Orig = createRenderedData(2, 100, 0, 200, 100) // Small, will be rendered
        // Task3 is very large, assume it will be hidden if T2 is present, even if T1 is gone.
        val task3Orig = createRenderedData(3, 0, 0, 0, 0) // Force hidden by empty bounds

        val allOriginalTasks = listOf(task1Orig, task2Orig, task3Orig)

        val prevRenderedT1 = createRenderedData(1, 10, 10, 110, 110) // Example organized bounds
        val prevRenderedT2 = createRenderedData(2, 120, 10, 220, 110) // Example organized bounds
        val prevHiddenT3 = createHiddenData(3)
        val previousLayout = listOf(prevRenderedT1, prevRenderedT2, prevHiddenT3)

        val dismissedTaskId = 1
        val result =
            useCase.invoke(
                allCurrentOriginalTaskBounds = allOriginalTasks,
                layoutConfig = DEFAULT_LAYOUT_CONFIG,
                taskPositionsHint = previousLayout,
                dismissedTaskId = dismissedTaskId,
            )

        // Expected: Result of performReflowRebalance on [prevRenderedT1, prevRenderedT2] removing
        // T1.
        // This would be prevRenderedT2, possibly re-centered.
        // For this setup (T1 and T2 on same row), T2 should be re-centered.
        // Original prevRenderedT2: Rect(120, 10, 220, 110) (width 100, height 100)
        // If T1 (width 100) is removed, T2 (width 100) should be centered in the space previously
        // occupied by T1 and T2.
        // Overall bounds of [prevRenderedT1, prevRenderedT2] before removal: union is
        // Rect(10,10,220,110)
        // CenterX of this is (10+220)/2 = 115.
        // T2 width is 100. So new T2 bounds: left = 115 - 100/2 = 65. right = 65 + 100 = 165.
        // Top, height remain same. So, Rect(65, 10, 165, 110).
        val expectedReflowedT2 = createRenderedData(2, 65, 10, 165, 110)

        // Verify that the result contains the reflowed T2 and the preserved hidden T3.
        assertThat(result).hasSize(2)
        assertThat(result).contains(expectedReflowedT2)
        assertThat(result).contains(prevHiddenT3)
    }

    @Test
    fun layoutWithHiddenTasks_relayoutsVisibleTasksOptimally() {
        val taskRect = Rect(0, 0, 200, 100) // Same size for all tasks

        val tasks =
            listOf(
                RenderedDesktopTaskBoundsData(1, taskRect),
                RenderedDesktopTaskBoundsData(2, taskRect),
                RenderedDesktopTaskBoundsData(3, taskRect),
            )

        val constrainedConfig =
            TEST_LAYOUT_CONFIG.copy(desktopBounds = Rect(0, 0, 1000, 400), maxRows = 1)

        // With the constrained config, only 2 tasks should fit, so task 3 will be hidden.
        val visibleTasks = tasks.take(2)

        // 1. Get layout for all tasks (where one should be hidden due to constraints)
        val resultWithHidden = useCase.invoke(tasks, constrainedConfig)

        // 2. Get layout for only the tasks that are expected to be visible
        val resultVisibleOnly = useCase.invoke(visibleTasks, constrainedConfig)

        // 3. Filter out the hidden task from the first result
        val renderedTasksFromResultWithHidden =
            resultWithHidden.filterIsInstance<RenderedDesktopTaskBoundsData>()

        // 4. Assert that the layouts are the same
        assertThat(renderedTasksFromResultWithHidden).isEqualTo(resultVisibleOnly)
        // Also assert that task 3 was indeed hidden
        assertThat(resultWithHidden.find { it.taskId == 3 })
            .isInstanceOf(HiddenDesktopTaskBoundsData::class.java)
    }

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
        private val DEFAULT_DESKTOP_BOUNDS = Rect(0, 0, 10000, 10000)
        private val DEFAULT_LAYOUT_CONFIG =
            TEST_LAYOUT_CONFIG.copy(desktopBounds = DEFAULT_DESKTOP_BOUNDS)
    }
}
