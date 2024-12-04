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
package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.content.Context
import android.content.res.Resources
import android.graphics.PointF
import android.graphics.Rect
import android.os.IBinder
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display
import android.window.WindowContainerToken
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.shared.DesktopModeStatus
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

/**
 * Tests for [DragPositioningCallbackUtility].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:DragPositioningCallbackUtilityTest
 */
@RunWith(AndroidTestingRunner::class)
class DragPositioningCallbackUtilityTest {
    @Mock
    private lateinit var mockWindowDecoration: WindowDecoration<*>
    @Mock
    private lateinit var taskToken: WindowContainerToken
    @Mock
    private lateinit var taskBinder: IBinder
    @Mock
    private lateinit var mockDisplayController: DisplayController
    @Mock
    private lateinit var mockDisplayLayout: DisplayLayout
    @Mock
    private lateinit var mockDisplay: Display
    @Mock
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockResources: Resources

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)

        whenever(taskToken.asBinder()).thenReturn(taskBinder)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.densityDpi()).thenReturn(DENSITY_DPI)
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }

        initializeTaskInfo()
        mockWindowDecoration.mDisplay = mockDisplay
        mockWindowDecoration.mDecorWindowContext = mockContext
        whenever(mockContext.getResources()).thenReturn(mockResources)
        whenever(mockWindowDecoration.mDecorWindowContext.resources).thenReturn(mockResources)
        whenever(mockResources.getDimensionPixelSize(R.dimen.desktop_mode_minimum_window_width))
                .thenReturn(DESKTOP_MODE_MIN_WIDTH)
        whenever(mockResources.getDimensionPixelSize(R.dimen.desktop_mode_minimum_window_height))
                .thenReturn(DESKTOP_MODE_MIN_HEIGHT)
        whenever(mockDisplay.displayId).thenAnswer { DISPLAY_ID }
    }

    @Test
    fun testChangeBoundsDoesNotChangeHeightWhenLessThanMin() {
        val startingPoint = PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.top.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)

        // Resize to width of 95px and height of 5px with min width of 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 5
        val newY = STARTING_BOUNDS.top.toFloat() + 95
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_TOP,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)

        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right - 5)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom)
    }

    @Test
    fun testChangeBoundsDoesNotChangeWidthWhenLessThanMin() {
        val startingPoint = PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.top.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)

        // Resize to height of 95px and width of 5px with min width of 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 95
        val newY = STARTING_BOUNDS.top.toFloat() + 5
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_TOP,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)

        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top + 5)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom)
    }

    @Test
    fun testChangeBoundsDoesNotChangeHeightWhenNegative() {
        val startingPoint = PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.top.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)

        // Resize to width of 95px and height of -5px with minimum of 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 5
        val newY = STARTING_BOUNDS.top.toFloat() + 105
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_TOP,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)

        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right - 5)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom)
    }

    @Test
    fun testChangeBoundsRunsWhenResizeBoundsValid() {
        val startingPoint = PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.top.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)

        // Shrink to height 20px and width 20px with both min height/width equal to 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 80
        val newY = STARTING_BOUNDS.top.toFloat() + 80
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_TOP,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)
        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top + 80)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right - 80)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom)
    }

    @Test
    fun testChangeBoundsDoesNotRunWithNegativeHeightAndWidth() {
        val startingPoint = PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.top.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)
        // Shrink to height -5px and width -5px with both min height/width equal to 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 105
        val newY = STARTING_BOUNDS.top.toFloat() + 105

        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_TOP,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)
        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom)
    }

    @Test
    fun testDragEndSnapsTaskBoundsWhenOutsideValidDragArea() {
        val startingPoint = PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.top.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)
        val validDragArea = Rect(DISPLAY_BOUNDS.left - 100,
            STABLE_BOUNDS.top,
            DISPLAY_BOUNDS.right - 100,
            DISPLAY_BOUNDS.bottom - 100)

        DragPositioningCallbackUtility.updateTaskBounds(repositionTaskBounds, STARTING_BOUNDS,
            startingPoint, startingPoint.x - 1000, (DISPLAY_BOUNDS.bottom + 1000).toFloat())
        DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(repositionTaskBounds,
            validDragArea)
        assertThat(repositionTaskBounds.left).isEqualTo(validDragArea.left)
        assertThat(repositionTaskBounds.top).isEqualTo(validDragArea.bottom)
        assertThat(repositionTaskBounds.right)
                .isEqualTo(validDragArea.left + STARTING_BOUNDS.width())
        assertThat(repositionTaskBounds.bottom)
                .isEqualTo(validDragArea.bottom + STARTING_BOUNDS.height())
    }

    @Test
    fun testChangeBounds_toDisallowedBounds_freezesAtLimit() {
        val startingPoint = PointF(STARTING_BOUNDS.right.toFloat(),
            STARTING_BOUNDS.bottom.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)
        // Initial resize to width and height 110px.
        var newX = STARTING_BOUNDS.right.toFloat() + 10
        var newY = STARTING_BOUNDS.bottom.toFloat() + 10
        var delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)
        assertTrue(DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration))
        // Resize width to 120px, height to disallowed area which should not result in a change.
        newX += 10
        newY = DISALLOWED_RESIZE_AREA.top.toFloat()
        delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)
        assertTrue(DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration))
        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right + 20)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom + 10)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS)
    fun taskMinWidthHeightUndefined_changeBoundsInDesktopModeLessThanMin_shouldNotChangeBounds() {
        whenever(DesktopModeStatus.canEnterDesktopMode(mockContext)).thenReturn(true)
        initializeTaskInfo(taskMinWidth = -1, taskMinHeight = -1)
        val startingPoint =
            PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)
        // Shrink height and width to 1px. The default allowed width and height are defined in
        // R.dimen.desktop_mode_minimum_window_width and R.dimen.desktop_mode_minimum_window_height
        val newX = STARTING_BOUNDS.right.toFloat() - 99
        val newY = STARTING_BOUNDS.bottom.toFloat() - 99
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)
        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_SIZE_CONSTRAINTS)
    fun taskMinWidthHeightUndefined_changeBoundsInDesktopModeAllowedSize_shouldChangeBounds() {
        whenever(DesktopModeStatus.canEnterDesktopMode(mockContext)).thenReturn(true)
        initializeTaskInfo(taskMinWidth = -1, taskMinHeight = -1)
        val startingPoint =
            PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)
        // Shrink height and width to 20px. The default allowed width and height are defined in
        // R.dimen.desktop_mode_minimum_window_width and R.dimen.desktop_mode_minimum_window_height
        val newX = STARTING_BOUNDS.right.toFloat() - 80
        val newY = STARTING_BOUNDS.bottom.toFloat() - 80
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)
        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right - 80)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom - 80)
    }

    @Test
    fun taskMinWidthHeightUndefined_changeBoundsLessThanDefaultMinSize_shouldNotChangeBounds() {
        initializeTaskInfo(taskMinWidth = -1, taskMinHeight = -1)
        val startingPoint =
            PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)
        // Shrink height and width to 1px. The default allowed width and height are defined in the
        // defaultMinSize of the TaskInfo.
        val newX = STARTING_BOUNDS.right.toFloat() - 99
        val newY = STARTING_BOUNDS.bottom.toFloat() - 99
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)
        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom)
    }

    @Test
    fun taskMinWidthHeightUndefined_changeBoundsToAnAllowedSize_shouldChangeBounds() {
        initializeTaskInfo(taskMinWidth = -1, taskMinHeight = -1)
        val startingPoint =
            PointF(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat())
        val repositionTaskBounds = Rect(STARTING_BOUNDS)
        // Shrink height and width to 50px. The default allowed width and height are defined in the
        // defaultMinSize of the TaskInfo.
        val newX = STARTING_BOUNDS.right.toFloat() - 50
        val newY = STARTING_BOUNDS.bottom.toFloat() - 50
        val delta = DragPositioningCallbackUtility.calculateDelta(newX, newY, startingPoint)

        DragPositioningCallbackUtility.changeBounds(CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
            repositionTaskBounds, STARTING_BOUNDS, STABLE_BOUNDS, delta, mockDisplayController,
            mockWindowDecoration)
        assertThat(repositionTaskBounds.left).isEqualTo(STARTING_BOUNDS.left)
        assertThat(repositionTaskBounds.top).isEqualTo(STARTING_BOUNDS.top)
        assertThat(repositionTaskBounds.right).isEqualTo(STARTING_BOUNDS.right - 50)
        assertThat(repositionTaskBounds.bottom).isEqualTo(STARTING_BOUNDS.bottom - 50)
    }

    private fun initializeTaskInfo(taskMinWidth: Int = MIN_WIDTH, taskMinHeight: Int = MIN_HEIGHT) {
        mockWindowDecoration.mTaskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = TASK_ID
            token = taskToken
            minWidth = taskMinWidth
            minHeight = taskMinHeight
            defaultMinSize = DEFAULT_MIN
            displayId = DISPLAY_ID
            configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
        }
    }

    companion object {
        private const val TASK_ID = 5
        private const val MIN_WIDTH = 10
        private const val MIN_HEIGHT = 10
        private const val DESKTOP_MODE_MIN_WIDTH = 20
        private const val DESKTOP_MODE_MIN_HEIGHT = 20
        private const val DENSITY_DPI = 20
        private const val DEFAULT_MIN = 40
        private const val DISPLAY_ID = 1
        private const val NAVBAR_HEIGHT = 50
        private val DISPLAY_BOUNDS = Rect(0, 0, 2400, 1600)
        private val STARTING_BOUNDS = Rect(0, 0, 100, 100)
        private val DISALLOWED_RESIZE_AREA = Rect(
            DISPLAY_BOUNDS.left,
            DISPLAY_BOUNDS.bottom - NAVBAR_HEIGHT,
            DISPLAY_BOUNDS.right,
            DISPLAY_BOUNDS.bottom)
        private val STABLE_BOUNDS = Rect(
            DISPLAY_BOUNDS.left,
            DISPLAY_BOUNDS.top,
            DISPLAY_BOUNDS.right,
            DISPLAY_BOUNDS.bottom - NAVBAR_HEIGHT
        )
    }
}