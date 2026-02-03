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

package com.android.wm.shell.windowdecor.common

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.graphics.Rect
import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.splitscreen.SplitScreenController
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [DesktopMenuPositionUtility].
 *
 * Build/Install/Run: atest WMShellUnitTests:DesktopMenuPositionUtilityTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopMenuPositionUtilityTest : ShellTestCase() {

    @Mock private val mockSplitScreenController = mock<SplitScreenController>()

    @Test
    fun testFullscreenPositionCalculation() {
        val task = setupTaskInfo(WINDOWING_MODE_FULLSCREEN)
        val result =
            calculateMenuPosition(
                splitScreenController = mockSplitScreenController,
                taskInfo = task,
                MARGIN_START,
                MARGIN_TOP,
                CAPTION_X,
                CAPTION_Y,
                CAPTION_WIDTH,
                MENU_WIDTH,
                isRtl = false,
            )
        assertEquals(CAPTION_X + (CAPTION_WIDTH / 2) - (MENU_WIDTH / 2), result.x)
        assertEquals(CAPTION_Y + MARGIN_TOP, result.y)
    }

    @Test
    fun testSplitLeftPositionCalculation() {
        val task = setupTaskInfo(WINDOWING_MODE_MULTI_WINDOW)
        setupMockSplitScreenController(
            splitPosition = SPLIT_POSITION_TOP_OR_LEFT,
            isLeftRightSplit = true,
        )
        val result =
            calculateMenuPosition(
                splitScreenController = mockSplitScreenController,
                taskInfo = task,
                MARGIN_START,
                MARGIN_TOP,
                CAPTION_X,
                CAPTION_Y,
                CAPTION_WIDTH,
                MENU_WIDTH,
                isRtl = false,
            )
        assertEquals(CAPTION_X + (CAPTION_WIDTH / 2) - (MENU_WIDTH / 2), result.x)
        assertEquals(CAPTION_Y + MARGIN_TOP, result.y)
    }

    @Test
    fun testSplitRightPositionCalculation() {
        val task = setupTaskInfo(WINDOWING_MODE_MULTI_WINDOW)
        setupMockSplitScreenController(
            splitPosition = SPLIT_POSITION_BOTTOM_OR_RIGHT,
            isLeftRightSplit = true,
        )
        val result =
            calculateMenuPosition(
                splitScreenController = mockSplitScreenController,
                taskInfo = task,
                MARGIN_START,
                MARGIN_TOP,
                CAPTION_X,
                CAPTION_Y,
                CAPTION_WIDTH,
                MENU_WIDTH,
                isRtl = false,
            )
        assertEquals(
            CAPTION_X + (CAPTION_WIDTH / 2) - (MENU_WIDTH / 2) + SPLIT_LEFT_BOUNDS.width(),
            result.x,
        )
        assertEquals(CAPTION_Y + MARGIN_TOP, result.y)
    }

    @Test
    fun testSplitTopPositionCalculation() {
        val task = setupTaskInfo(WINDOWING_MODE_MULTI_WINDOW)
        setupMockSplitScreenController(
            splitPosition = SPLIT_POSITION_TOP_OR_LEFT,
            isLeftRightSplit = false,
        )
        val result =
            calculateMenuPosition(
                splitScreenController = mockSplitScreenController,
                taskInfo = task,
                MARGIN_START,
                MARGIN_TOP,
                CAPTION_X,
                CAPTION_Y,
                CAPTION_WIDTH,
                MENU_WIDTH,
                isRtl = false,
            )
        assertEquals(CAPTION_X + (CAPTION_WIDTH / 2) - (MENU_WIDTH / 2), result.x)
        assertEquals(CAPTION_Y + MARGIN_TOP, result.y)
    }

    @Test
    fun testSplitBottomPositionCalculation() {
        val task = setupTaskInfo(WINDOWING_MODE_MULTI_WINDOW)
        setupMockSplitScreenController(
            splitPosition = SPLIT_POSITION_BOTTOM_OR_RIGHT,
            isLeftRightSplit = false,
        )
        val result =
            calculateMenuPosition(
                splitScreenController = mockSplitScreenController,
                taskInfo = task,
                MARGIN_START,
                MARGIN_TOP,
                CAPTION_X,
                CAPTION_Y,
                CAPTION_WIDTH,
                MENU_WIDTH,
                isRtl = false,
            )
        assertEquals(CAPTION_X + (CAPTION_WIDTH / 2) - (MENU_WIDTH / 2), result.x)
        assertEquals(CAPTION_Y + MARGIN_TOP + SPLIT_TOP_BOUNDS.height(), result.y)
    }

    private fun setupTaskInfo(windowingMode: Int): RunningTaskInfo {
        return TestRunningTaskInfoBuilder().setWindowingMode(windowingMode).build()
    }

    private fun setupMockSplitScreenController(isLeftRightSplit: Boolean, splitPosition: Int) {
        whenever(mockSplitScreenController.getSplitPosition(anyInt())).thenReturn(splitPosition)
        whenever(mockSplitScreenController.getRefStageBounds(any(), any())).thenAnswer {
            (it.arguments.first() as Rect).set(
                if (isLeftRightSplit) {
                    SPLIT_LEFT_BOUNDS
                } else {
                    SPLIT_TOP_BOUNDS
                }
            )
            (it.arguments[1] as Rect).set(
                if (isLeftRightSplit) {
                    SPLIT_RIGHT_BOUNDS
                } else {
                    SPLIT_BOTTOM_BOUNDS
                }
            )
        }
        whenever(mockSplitScreenController.isLeftRightSplit).thenReturn(isLeftRightSplit)
    }

    companion object {
        private val SPLIT_LEFT_BOUNDS = Rect(0, 0, 1280, 1600)
        private val SPLIT_RIGHT_BOUNDS = Rect(1280, 0, 2560, 1600)
        private val SPLIT_TOP_BOUNDS = Rect(0, 0, 2560, 800)
        private val SPLIT_BOTTOM_BOUNDS = Rect(0, 800, 2560, 1600)
        private const val CAPTION_X = 800
        private const val CAPTION_Y = 50
        private const val MARGIN_START = 30
        private const val MARGIN_TOP = 50
        private const val MENU_WIDTH = 500
        private const val CAPTION_WIDTH = 200
    }
}
