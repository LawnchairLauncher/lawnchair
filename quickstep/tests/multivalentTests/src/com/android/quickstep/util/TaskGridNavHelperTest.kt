/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.quickstep.util

import com.android.launcher3.util.IntArray
import com.android.quickstep.util.TaskGridNavHelper.Companion.ADD_DESK_PLACEHOLDER_ID
import com.android.quickstep.util.TaskGridNavHelper.Companion.CLEAR_ALL_PLACEHOLDER_ID
import com.android.quickstep.util.TaskGridNavHelper.TaskNavDirection.DOWN
import com.android.quickstep.util.TaskGridNavHelper.TaskNavDirection.LEFT
import com.android.quickstep.util.TaskGridNavHelper.TaskNavDirection.RIGHT
import com.android.quickstep.util.TaskGridNavHelper.TaskNavDirection.TAB
import com.android.quickstep.util.TaskGridNavHelper.TaskNavDirection.UP
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskGridNavHelperTest {

    /*
                    5   3   1
        CLEAR_ALL           ↓
                    6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_pressDown_goesToBottom() {
        assertThat(getNextGridPage(currentPageTaskViewId = 1, DOWN, delta = 1)).isEqualTo(2)
    }

    /*                      ↑----→
                    5   3   1    |
        CLEAR_ALL                |
                    6   4   2←---|
    */
    @Test
    fun equalLengthRows_noFocused_onTop_pressUp_goesToBottom() {
        assertThat(getNextGridPage(currentPageTaskViewId = 1, UP, delta = 1)).isEqualTo(2)
    }

    /*                      ↓----↑
                    5   3   1    |
        CLEAR_ALL                |
                    6   4   2    |
                            ↓----→
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_pressDown_goesToTop() {
        assertThat(getNextGridPage(currentPageTaskViewId = 2, DOWN, delta = 1)).isEqualTo(1)
    }

    /*
                    5   3   1
        CLEAR_ALL           ↑
                    6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_pressUp_goesToTop() {
        assertThat(getNextGridPage(currentPageTaskViewId = 2, UP, delta = 1)).isEqualTo(1)
    }

    /*
                    5   3<--1
        CLEAR_ALL
                    6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_pressLeft_goesLeft() {
        assertThat(getNextGridPage(currentPageTaskViewId = 1, LEFT, delta = 1)).isEqualTo(3)
    }

    /*
                    5   3   1
        CLEAR_ALL
                    6   4<--2
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_pressLeft_goesLeft() {
        assertThat(getNextGridPage(currentPageTaskViewId = 2, LEFT, delta = 1)).isEqualTo(4)
    }

    /*
                    5   3-->1
        CLEAR_ALL
                    6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_secondItem_pressRight_goesRight() {
        assertThat(getNextGridPage(currentPageTaskViewId = 3, RIGHT, delta = -1)).isEqualTo(1)
    }

    /*
                    5   3   1
        CLEAR_ALL
                    6   4-->2
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_secondItem_pressRight_goesRight() {
        assertThat(getNextGridPage(currentPageTaskViewId = 4, RIGHT, delta = -1)).isEqualTo(2)
    }

    /*
             ↓------------------←
             |                  |
             ↓      5   3   1---→
        CLEAR_ALL
                    6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_pressRight_cycleToClearAll() {
        assertThat(getNextGridPage(currentPageTaskViewId = 1, RIGHT, delta = -1))
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
             ↓------------------←
             |                  ↑
             ↓      5   3   1   |
        CLEAR_ALL               ↑
                    6   4   2---→
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_pressRight_cycleToClearAll() {
        assertThat(getNextGridPage(currentPageTaskViewId = 2, RIGHT, delta = -1))
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
              ←----5   3   1
              ↓
        CLEAR_ALL
                   6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_lastItem_pressLeft_toClearAll() {
        assertThat(getNextGridPage(currentPageTaskViewId = 5, LEFT, delta = 1))
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
                   5   3   1
        CLEAR_ALL
               ↑
               ←---6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_lastItem_pressLeft_toClearAll() {
        assertThat(getNextGridPage(currentPageTaskViewId = 6, LEFT, delta = 1))
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
       |→-----------------------|
       |                        ↓
       ↑                5   3   1
       ←------CLEAR_ALL

                        6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onClearAll_pressLeft_cycleToFirst() {
        assertThat(
                getNextGridPage(currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID, LEFT, delta = 1)
            )
            .isEqualTo(1)
    }

    /*
                       5   3   1
        CLEAR_ALL--↓
                   |
                   |--→6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onClearAll_pressRight_toLastInBottom() {
        assertThat(
                getNextGridPage(currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID, RIGHT, delta = -1)
            )
            .isEqualTo(6)
    }

    /*
                    5   3   1←---
                                 ↑
        CLEAR_ALL                ←--FOCUSED_TASK
                    6   4   2
    */
    @Test
    fun equalLengthRows_withFocused_onFocused_pressLeft_toTop() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = FOCUSED_TASK_ID,
                    LEFT,
                    delta = 1,
                    largeTileIds = listOf(FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(1)
    }

    /*
                        5   3   1
                                   ←--↑
        CLEAR_ALL                  ↓-→FOCUSED_TASK
                        6   4   2
    */
    @Test
    fun equalLengthRows_withFocused_onFocused_pressUp_stayOnFocused() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = FOCUSED_TASK_ID,
                    UP,
                    delta = 1,
                    largeTileIds = listOf(FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(FOCUSED_TASK_ID)
    }

    /*
                        5   3   1
        CLEAR_ALL                  ↑--→FOCUSED_TASK
                                   ↑←--↓
                        6   4   2
    */

    @Test
    fun equalLengthRows_withFocused_onFocused_pressDown_stayOnFocused() {

        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = FOCUSED_TASK_ID,
                    DOWN,
                    delta = 1,
                    largeTileIds = listOf(FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(FOCUSED_TASK_ID)
    }

    /*
             ↓-------------------------------←|
             |                                ↑
             ↓      5   3   1                 |
        CLEAR_ALL               FOCUSED_TASK--→
                    6   4   2
    */
    @Test
    fun equalLengthRows_withFocused_onFocused_pressRight_cycleToClearAll() {

        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = FOCUSED_TASK_ID,
                    RIGHT,
                    delta = -1,
                    largeTileIds = listOf(FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
           |→---------------------------|
           |                            |
           ↑                5   3   1   ↓
           ←------CLEAR_ALL            FOCUSED_TASK

                            6   4   2
    */
    @Test
    fun equalLengthRows_withFocused_onClearAll_pressLeft_cycleToFocusedTask() {

        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID,
                    LEFT,
                    delta = 1,
                    largeTileIds = listOf(FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(FOCUSED_TASK_ID)
    }

    /*
                         7←-↑  5   3   1
                         ↓--→
           CLEAR_ALL
                               6   4   2
    */
    @Test
    fun longerTopRow_noFocused_atEndTopBeyondBottom_pressDown_stayTop() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = 7,
                    DOWN,
                    delta = 1,
                    topIds = IntArray.wrap(1, 3, 5, 7),
                )
            )
            .isEqualTo(7)
    }

    /*
                       ←--↑
                       ↓-→7   5   3   1
           CLEAR_ALL
                              6   4   2
    */
    @Test
    fun longerTopRow_noFocused_atEndTopBeyondBottom_pressUp_stayTop() {
        assertThat(
                getNextGridPage(
                    /* topIds = */ currentPageTaskViewId = 7,
                    UP,
                    delta = 1,
                    topIds = IntArray.wrap(1, 3, 5, 7),
                )
            )
            .isEqualTo(7)
    }

    /*
                         7   5   3   1
           CLEAR_ALL     ↑
                         ←----6   4   2
    */
    @Test
    fun longerTopRow_noFocused_atEndBottom_pressLeft_goToTop() {
        assertThat(
                getNextGridPage(
                    /* topIds = */ currentPageTaskViewId = 6,
                    LEFT,
                    delta = 1,
                    topIds = IntArray.wrap(1, 3, 5, 7),
                )
            )
            .isEqualTo(7)
    }

    /*
                         7   5   3   1
                         ↑
           CLEAR_ALL-----→
                             6   4   2
    */
    @Test
    fun longerTopRow_noFocused_atClearAll_pressRight_goToLonger() {
        assertThat(
                getNextGridPage(
                    /* topIds = */ currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID,
                    RIGHT,
                    delta = -1,
                    topIds = IntArray.wrap(1, 3, 5, 7),
                )
            )
            .isEqualTo(7)
    }

    /*
                            5   3   1
           CLEAR_ALL-----→
                         ↓
                         7  6   4   2
    */
    @Test
    fun longerBottomRow_noFocused_atClearAll_pressRight_goToLonger() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID,
                    RIGHT,
                    delta = -1,
                    bottomIds = IntArray.wrap(2, 4, 6, 7),
                )
            )
            .isEqualTo(7)
    }

    /*
                   5   3   1
        CLEAR_ALL          ↓
                   6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_pressTab_goesToBottom() {
        assertThat(getNextGridPage(currentPageTaskViewId = 1, TAB, delta = 1)).isEqualTo(2)
    }

    /*
                   5   3   1
        CLEAR_ALL      ↑
                       ←---↑
                   6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_pressTab_goesToNextTop() {
        assertThat(getNextGridPage(currentPageTaskViewId = 2, TAB, delta = 1)).isEqualTo(3)
    }

    /*
                   5   3   1
        CLEAR_ALL      ↓
                       ----→
                           ↓
                   6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_pressTabWithShift_goesToPreviousBottom() {
        assertThat(getNextGridPage(currentPageTaskViewId = 3, TAB, delta = -1)).isEqualTo(2)
    }

    /*
                   5   3   1
        CLEAR_ALL          ↑
                   6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onBottom_pressTabWithShift_goesToTop() {
        assertThat(getNextGridPage(currentPageTaskViewId = 2, TAB, delta = -1)).isEqualTo(1)
    }

    /*
                   5   3  [1]
        CLEAR_ALL
                   6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onTop_pressTabWithShift_noCycle_staysOnTop() {
        assertThat(getNextGridPage(currentPageTaskViewId = 1, TAB, delta = -1, cycle = false))
            .isEqualTo(1)
    }

    /*
                   5   3   1
       [CLEAR_ALL]
                   6   4   2
    */
    @Test
    fun equalLengthRows_noFocused_onClearAll_pressTab_noCycle_staysOnClearAll() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID,
                    TAB,
                    delta = 1,
                    cycle = false,
                )
            )
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
                        5   3   1
           CLEAR_ALL                FOCUSED_TASK←--DESKTOP
                        6   4   2
    */
    @Test
    fun withLargeTile_pressLeftFromDesktopTask_goesToFocusedTask() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = DESKTOP_TASK_ID,
                    LEFT,
                    delta = 1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(FOCUSED_TASK_ID)
    }

    /*
                        5   3   1
           CLEAR_ALL                FOCUSED_TASK--→DESKTOP
                        6   4   2
    */
    @Test
    fun withLargeTile_pressRightFromFocusedTask_goesToDesktopTask() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = FOCUSED_TASK_ID,
                    RIGHT,
                    delta = -1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(DESKTOP_TASK_ID)
    }

    /*
             ↓-----------------------------------------←|
             |                                          |
             ↓      5   3   1                           ↑
        CLEAR_ALL               FOCUSED_TASK   DESKTOP--→
                    6   4   2
    */
    @Test
    fun withLargeTile_pressRightFromDesktopTask_goesToClearAll() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = DESKTOP_TASK_ID,
                    RIGHT,
                    delta = -1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
           |→-------------------------------------------|
           |                                            |
           ↑                5   3   1                   ↓
           ←------CLEAR_ALL             FOCUSED_TASK   DESKTOP

                            6   4   2
    */
    @Test
    fun withLargeTile_pressLeftFromClearAll_goesToDesktopTask() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID,
                    LEFT,
                    delta = 1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(DESKTOP_TASK_ID)
    }

    /*
                    5   3   1
       CLEAR_ALL                 FOCUSED_TASK   DESKTOP
                                  ↑
                    6   4   2→----↑
    */
    @Test
    fun withLargeTile_pressRightFromBottom_goesToLargeTile() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = 2,
                    RIGHT,
                    delta = -1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(FOCUSED_TASK_ID)
    }

    /*
                        5   3   1→----|
                                      ↓
         CLEAR_ALL                    FOCUSED_TASK   DESKTOP
                        6   4   2
    */
    @Test
    fun withLargeTile_pressRightFromTop_goesToLargeTile() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = 1,
                    RIGHT,
                    delta = -1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(FOCUSED_TASK_ID)
    }

    /*
                        5   3   1

         CLEAR_ALL                FOCUSED_TASK←---DESKTOP
                        6   4   2
    */
    @Test
    fun withLargeTile_pressTabFromDeskTop_goesToFocusedTask() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = DESKTOP_TASK_ID,
                    TAB,
                    delta = 1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(FOCUSED_TASK_ID)
    }

    /*
        CLEAR_ALL         FOCUSED_TASK   DESKTOP
                           ↓
                     2←----↓
    */
    @Test
    fun withLargeTile_pressLeftFromLargeTile_goesToBottom() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = FOCUSED_TASK_ID,
                    LEFT,
                    delta = 1,
                    topIds = IntArray(),
                    bottomIds = IntArray.wrap(2),
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(2)
    }

    /*
             ↓-----------------------------------------←|
             |                                          |
             ↓      5   3   1                           ↑
        CLEAR_ALL               FOCUSED_TASK   DESKTOP--→
                    6   4   2
    */
    @Test
    fun withLargeTile_pressShiftTabFromDeskTop_goesToClearAll() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = DESKTOP_TASK_ID,
                    TAB,
                    delta = -1,
                    largeTileIds = listOf(DESKTOP_TASK_ID, FOCUSED_TASK_ID),
                )
            )
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
                        5   3   1→----|
                                      ↓
         CLEAR_ALL                   ADD_DESKTOP
                        6   4   2
    */
    @Test
    fun withAddDesktopButton_pressRightFromTop_goesToAddDesktopButton() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = 1,
                    RIGHT,
                    delta = -1,
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(ADD_DESK_PLACEHOLDER_ID)
    }

    /*
                    5   3   1
       CLEAR_ALL                 ADD_DESKTOP
                                  ↑
                    6   4   2→----↑
    */
    @Test
    fun withAddDesktopButton_pressRightFromBottom_goesToAddDesktopButton() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = 2,
                    RIGHT,
                    delta = -1,
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(ADD_DESK_PLACEHOLDER_ID)
    }

    /*
         ↓-------------------------------←|
         |                                ↑
         ↓      5   3   1                 |
    CLEAR_ALL               ADD_DESKTOP--→
                6   4   2
    */
    @Test
    fun withAddDesktopButton_pressRightFromAddDesktopButton_goesToClearAllButton() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = ADD_DESK_PLACEHOLDER_ID,
                    RIGHT,
                    delta = -1,
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(CLEAR_ALL_PLACEHOLDER_ID)
    }

    /*
           |→--------------------------------|
           |                                 |
           ↑                5   3   1        ↓
           ←------CLEAR_ALL             ADD_DESKTOP

                            6   4   2
    */
    @Test
    fun withAddDesktopButton_pressLeftFromClearAllButton_goesToAddDesktopButton() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = CLEAR_ALL_PLACEHOLDER_ID,
                    LEFT,
                    delta = 1,
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(ADD_DESK_PLACEHOLDER_ID)
    }

    /*
                        5   3   1
                                   ←--↑
        CLEAR_ALL                  ↓-→ADD_DESKTOP
                        6   4   2
    */
    @Test
    fun withAddDesktopButton_pressUpOnAddDesktop_stayOnAddDesktopButton() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = ADD_DESK_PLACEHOLDER_ID,
                    UP,
                    delta = 1,
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(ADD_DESK_PLACEHOLDER_ID)
    }

    /*
                        5   3   1
        CLEAR_ALL                  ↑--→ADD_DESKTOP
                                   ↑←--↓
                        6   4   2
    */
    @Test
    fun withAddDesktopButton_pressDownOnAddDesktop_stayOnAddDesktopButton() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = ADD_DESK_PLACEHOLDER_ID,
                    DOWN,
                    delta = 1,
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(ADD_DESK_PLACEHOLDER_ID)
    }

    /*
                        5   3   1
           CLEAR_ALL                DESKTOP--→ADD_DESKTOP
                        6   4   2
    */
    @Test
    fun withAddDesktopButton_pressRightFromDesktopTask_goesToAddDesktopButton() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = ADD_DESK_PLACEHOLDER_ID,
                    LEFT,
                    delta = 1,
                    largeTileIds = listOf(DESKTOP_TASK_ID),
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(DESKTOP_TASK_ID)
    }

    /*
                        5   3   1
           CLEAR_ALL                DESKTOP←--ADD_DESKTOP
                        6   4   2
    */
    @Test
    fun withAddDesktopButton_pressLeftFromAddDesktopButton_goesToDesktopTask() {
        assertThat(
                getNextGridPage(
                    currentPageTaskViewId = DESKTOP_TASK_ID,
                    RIGHT,
                    delta = -1,
                    largeTileIds = listOf(DESKTOP_TASK_ID),
                    hasAddDesktopButton = true,
                )
            )
            .isEqualTo(ADD_DESK_PLACEHOLDER_ID)
    }

    /*
                  5   3   1     |
       CLEAR_ALL                |       Invalid ID:
                  6   4   2     |       [25] --> [25]
    */
    @Test
    fun nextGridPage_invalidId_pressTab_noCycle_returnsCurrentPage() {
        assertThat(getNextGridPage(currentPageTaskViewId = 25, TAB, delta = -1, cycle = false))
            .isEqualTo(25)
    }

    // Col offset:  0   1   2
    //             -----------
    // ID grid:     4   2   0  start
    //         end [5]  3   1
    @Test
    fun gridTaskViewIdOffsetPairInTabOrderSequence_towardsStart() {
        val expected = listOf(Pair(4, 0), Pair(3, 1), Pair(2, 1), Pair(1, 2), Pair(0, 2))
        assertThat(
                gridTaskViewIdOffsetPairInTabOrderSequence(
                        initialTaskViewId = 5,
                        towardsStart = true,
                    )
                    .toList()
            )
            .isEqualTo(expected)
    }

    // Col offset:  2   1   0
    //             -----------
    // ID grid:     4   2  [0] start
    //          end 5   3   1
    @Test
    fun gridTaskViewIdOffsetPairInTabOrderSequence_towardsEnd() {
        val expected = listOf(Pair(1, 0), Pair(2, 1), Pair(3, 1), Pair(4, 2), Pair(5, 2))
        assertThat(
                gridTaskViewIdOffsetPairInTabOrderSequence(
                        initialTaskViewId = 0,
                        towardsStart = false,
                    )
                    .toList()
            )
            .isEqualTo(expected)
    }

    private fun getNextGridPage(
        currentPageTaskViewId: Int,
        direction: TaskGridNavHelper.TaskNavDirection,
        delta: Int,
        topIds: IntArray = IntArray.wrap(1, 3, 5),
        bottomIds: IntArray = IntArray.wrap(2, 4, 6),
        largeTileIds: List<Int> = emptyList(),
        hasAddDesktopButton: Boolean = false,
        cycle: Boolean = true,
    ): Int {
        val taskGridNavHelper =
            TaskGridNavHelper(topIds, bottomIds, largeTileIds, hasAddDesktopButton)
        return taskGridNavHelper.getNextGridPage(currentPageTaskViewId, delta, direction, cycle)
    }

    private fun gridTaskViewIdOffsetPairInTabOrderSequence(
        initialTaskViewId: Int,
        towardsStart: Boolean,
        topIds: IntArray = IntArray.wrap(0, 2, 4),
        bottomIds: IntArray = IntArray.wrap(1, 3, 5),
        largeTileIds: List<Int> = emptyList(),
        hasAddDesktopButton: Boolean = false,
    ): Sequence<Pair<Int, Int>> {
        val taskGridNavHelper =
            TaskGridNavHelper(topIds, bottomIds, largeTileIds, hasAddDesktopButton)
        return taskGridNavHelper.gridTaskViewIdOffsetPairInTabOrderSequence(
            initialTaskViewId,
            towardsStart,
        )
    }

    private companion object {
        const val FOCUSED_TASK_ID = 99
        const val DESKTOP_TASK_ID = 100
    }
}
