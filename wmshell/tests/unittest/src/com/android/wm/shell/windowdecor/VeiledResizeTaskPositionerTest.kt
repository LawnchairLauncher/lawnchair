/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.app.WindowConfiguration
import android.graphics.Point
import android.graphics.Rect
import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.Surface.ROTATION_0
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_90
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TransitionFinishCallback
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED
import java.util.function.Supplier
import junit.framework.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.argThat
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

/**
 * Tests for [VeiledResizeTaskPositioner].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:VeiledResizeTaskPositionerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class VeiledResizeTaskPositionerTest : ShellTestCase() {

    @Mock
    private lateinit var mockShellTaskOrganizer: ShellTaskOrganizer
    @Mock
    private lateinit var mockDesktopWindowDecoration: DesktopModeWindowDecoration
    @Mock
    private lateinit var mockDragStartListener: DragPositioningCallbackUtility.DragStartListener

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
    private lateinit var mockTransactionFactory: Supplier<SurfaceControl.Transaction>
    @Mock
    private lateinit var mockTransaction: SurfaceControl.Transaction
    @Mock
    private lateinit var mockTransitionBinder: IBinder
    @Mock
    private lateinit var mockTransitionInfo: TransitionInfo
    @Mock
    private lateinit var mockFinishCallback: TransitionFinishCallback
    @Mock
    private lateinit var mockTransitions: Transitions

    private lateinit var taskPositioner: VeiledResizeTaskPositioner

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        whenever(taskToken.asBinder()).thenReturn(taskBinder)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.densityDpi()).thenReturn(DENSITY_DPI)
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            if (mockDesktopWindowDecoration.mTaskInfo.configuration.windowConfiguration
                .displayRotation == ROTATION_90 ||
                mockDesktopWindowDecoration.mTaskInfo.configuration.windowConfiguration
                    .displayRotation == ROTATION_270
            ) {
                (i.arguments.first() as Rect).set(STABLE_BOUNDS_LANDSCAPE)
            } else {
                (i.arguments.first() as Rect).set(STABLE_BOUNDS_PORTRAIT)
            }
        }
        `when`(mockTransactionFactory.get()).thenReturn(mockTransaction)
        mockDesktopWindowDecoration.mTaskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = TASK_ID
            token = taskToken
            minWidth = MIN_WIDTH
            minHeight = MIN_HEIGHT
            defaultMinSize = DEFAULT_MIN
            displayId = DISPLAY_ID
            configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
            configuration.windowConfiguration.displayRotation = ROTATION_90
        }
        `when`(mockDesktopWindowDecoration.calculateValidDragArea()).thenReturn(VALID_DRAG_AREA)
        mockDesktopWindowDecoration.mDisplay = mockDisplay
        whenever(mockDisplay.displayId).thenAnswer { DISPLAY_ID }

        taskPositioner =
                VeiledResizeTaskPositioner(
                        mockShellTaskOrganizer,
                        mockDesktopWindowDecoration,
                        mockDisplayController,
                        mockDragStartListener,
                        mockTransactionFactory,
                        mockTransitions
                )
    }

    @Test
    fun testDragResize_noMove_doesNotShowResizeVeil() {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat()
        )
        verify(mockDesktopWindowDecoration, never()).showResizeVeil(STARTING_BOUNDS)

        taskPositioner.onDragPositioningEnd(
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat()
        )
        verify(mockTransitions, never()).startTransition(eq(TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == STARTING_BOUNDS}},
            eq(taskPositioner))
        verify(mockDesktopWindowDecoration, never()).hideResizeVeil()
    }

    @Test
    fun testDragResize_movesTask_doesNotShowResizeVeil() {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_UNDEFINED,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningMove(
            STARTING_BOUNDS.left.toFloat() + 60,
            STARTING_BOUNDS.top.toFloat() + 100
        )
        val rectAfterMove = Rect(STARTING_BOUNDS)
        rectAfterMove.left += 60
        rectAfterMove.right += 60
        rectAfterMove.top += 100
        rectAfterMove.bottom += 100
        verify(mockTransaction).setPosition(any(), eq(rectAfterMove.left.toFloat()),
                eq(rectAfterMove.top.toFloat()))

        taskPositioner.onDragPositioningEnd(
            STARTING_BOUNDS.left.toFloat() + 70,
            STARTING_BOUNDS.top.toFloat() + 20
        )
        val rectAfterEnd = Rect(STARTING_BOUNDS)
        rectAfterEnd.left += 70
        rectAfterEnd.right += 70
        rectAfterEnd.top += 20
        rectAfterEnd.bottom += 20

        verify(mockDesktopWindowDecoration, never()).showResizeVeil(any())
        verify(mockDesktopWindowDecoration, never()).hideResizeVeil()
        verify(mockTransitions).startTransition(eq(TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterEnd }},
                eq(taskPositioner))
    }

    @Test
    fun testDragResize_resize_boundsUpdateOnEnd() {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_RIGHT or CTRL_TYPE_TOP,
            STARTING_BOUNDS.right.toFloat(),
            STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningMove(
            STARTING_BOUNDS.right.toFloat() + 10,
            STARTING_BOUNDS.top.toFloat() + 10
        )

        val rectAfterMove = Rect(STARTING_BOUNDS)
        rectAfterMove.right += 10
        rectAfterMove.top += 10
        verify(mockDesktopWindowDecoration).showResizeVeil(rectAfterMove)
        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterMove
            }
        })

        taskPositioner.onDragPositioningEnd(
            STARTING_BOUNDS.right.toFloat() + 20,
            STARTING_BOUNDS.top.toFloat() + 20
        )
        val rectAfterEnd = Rect(rectAfterMove)
        rectAfterEnd.right += 10
        rectAfterEnd.top += 10
        verify(mockDesktopWindowDecoration).updateResizeVeil(any())
        verify(mockTransitions).startTransition(eq(TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterEnd}},
            eq(taskPositioner))
    }

    @Test
    fun testDragResize_noEffectiveMove_skipsTransactionOnEnd() {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningMove(
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningEnd(
            STARTING_BOUNDS.left.toFloat() + 10,
            STARTING_BOUNDS.top.toFloat() + 10
        )

        verify(mockTransitions, never()).startTransition(eq(TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == STARTING_BOUNDS}},
            eq(taskPositioner))

        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0)
            }
        })
    }


    @Test
    fun testDragResize_drag_setBoundsNotRunIfDragEndsInDisallowedEndArea() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_UNDEFINED, // drag
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        val newX = STARTING_BOUNDS.left.toFloat() + 5
        val newY = DISALLOWED_AREA_FOR_END_BOUNDS_HEIGHT.toFloat() - 1
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0)
            }
        })
    }

    @Test
    fun testDragResize_resize_resizingTaskReorderedToTopWhenNotFocused() {
        mockDesktopWindowDecoration.mTaskInfo.isFocused = false
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT, // Resize right
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Verify task is reordered to top
        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.hierarchyOps.any { hierarchyOps ->
                hierarchyOps.container == taskBinder && hierarchyOps.toTop }
        })
    }

    @Test
    fun testDragResize_resize_resizingTaskNotReorderedToTopWhenFocused() {
        mockDesktopWindowDecoration.mTaskInfo.isFocused = true
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT, // Resize right
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Verify task is not reordered to top
        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.hierarchyOps.any { hierarchyOps ->
                hierarchyOps.container == taskBinder && hierarchyOps.toTop }
        })
    }

    @Test
    fun testDragResize_drag_draggedTaskNotReorderedToTop() {
        mockDesktopWindowDecoration.mTaskInfo.isFocused = false
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_UNDEFINED, // drag
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Verify task is not reordered to top since task is already brought to top before dragging
        // begins
        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.hierarchyOps.any { hierarchyOps ->
                hierarchyOps.container == taskBinder && hierarchyOps.toTop }
        })
    }

    @Test
    fun testDragResize_drag_updatesStableBoundsOnRotate() {
        // Test landscape stable bounds
        performDrag(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat() + 2000, STARTING_BOUNDS.bottom.toFloat() + 2000,
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM)
        val rectAfterDrag = Rect(STARTING_BOUNDS)
        rectAfterDrag.right += 2000
        // First drag; we should fetch stable bounds.
        verify(mockDisplayLayout, times(1)).getStableBounds(any())
        verify(mockTransitions).startTransition(eq(TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterDrag}},
            eq(taskPositioner))
        // Drag back to starting bounds.
        performDrag(STARTING_BOUNDS.right.toFloat() + 2000, STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat(),
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM)

        // Display did not rotate; we should use previous stable bounds
        verify(mockDisplayLayout, times(1)).getStableBounds(any())

        // Rotate the screen to portrait
        mockDesktopWindowDecoration.mTaskInfo.apply {
            configuration.windowConfiguration.displayRotation = ROTATION_0
        }
        // Test portrait stable bounds
        performDrag(STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat() + 2000, STARTING_BOUNDS.bottom.toFloat() + 2000,
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM)
        rectAfterDrag.right -= 2000
        rectAfterDrag.bottom += 2000

        verify(mockTransitions).startTransition(eq(TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterDrag}},
            eq(taskPositioner))
        // Display has rotated; we expect a new stable bounds.
        verify(mockDisplayLayout, times(2)).getStableBounds(any())
    }

    @Test
    fun testIsResizingOrAnimatingResizeSet() {
        Assert.assertFalse(taskPositioner.isResizingOrAnimating)

        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningMove(
                STARTING_BOUNDS.left.toFloat() - 20,
                STARTING_BOUNDS.top.toFloat() - 20
        )

        // isResizingOrAnimating should be set to true after move during a resize
        Assert.assertTrue(taskPositioner.isResizingOrAnimating)

        taskPositioner.onDragPositioningEnd(
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // isResizingOrAnimating should be not be set till false until after transition animation
        Assert.assertTrue(taskPositioner.isResizingOrAnimating)
    }

    @Test
    fun testIsResizingOrAnimatingResizeResetAfterStartAnimation() {
        performDrag(
                STARTING_BOUNDS.left.toFloat(), STARTING_BOUNDS.top.toFloat(),
                STARTING_BOUNDS.left.toFloat() - 20, STARTING_BOUNDS.top.toFloat() - 20,
                CTRL_TYPE_TOP or CTRL_TYPE_RIGHT)

        taskPositioner.startAnimation(mockTransitionBinder, mockTransitionInfo, mockTransaction,
                mockTransaction, mockFinishCallback)

        // isResizingOrAnimating should be set to false until after transition successfully consumed
        Assert.assertFalse(taskPositioner.isResizingOrAnimating)
    }

    @Test
    fun testStartAnimation_useEndRelOffset() {
        val changeMock = mock(TransitionInfo.Change::class.java)
        val startTransaction = mock(Transaction::class.java)
        val finishTransaction = mock(Transaction::class.java)
        val point = Point(10, 20)
        val bounds = Rect(1, 2, 3, 4)
        `when`(changeMock.endRelOffset).thenReturn(point)
        `when`(changeMock.endAbsBounds).thenReturn(bounds)
        `when`(mockTransitionInfo.changes).thenReturn(listOf(changeMock))
        `when`(startTransaction.setWindowCrop(
            any(),
            eq(bounds.width()),
            eq(bounds.height())
        )).thenReturn(startTransaction)
        `when`(finishTransaction.setWindowCrop(
            any(),
            eq(bounds.width()),
            eq(bounds.height())
        )).thenReturn(finishTransaction)

        taskPositioner.startAnimation(
            mockTransitionBinder,
            mockTransitionInfo,
            startTransaction,
            finishTransaction,
            mockFinishCallback
        )

        verify(startTransaction).setPosition(any(), eq(point.x.toFloat()), eq(point.y.toFloat()))
        verify(finishTransaction).setPosition(any(), eq(point.x.toFloat()), eq(point.y.toFloat()))
        verify(changeMock).endRelOffset
    }

    private fun performDrag(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        ctrlType: Int
    ) {
        taskPositioner.onDragPositioningStart(
            ctrlType,
            startX,
            startY
        )
        taskPositioner.onDragPositioningMove(
            endX,
            endY
        )

        taskPositioner.onDragPositioningEnd(
            endX,
            endY
        )
    }

    companion object {
        private const val TASK_ID = 5
        private const val MIN_WIDTH = 10
        private const val MIN_HEIGHT = 10
        private const val DENSITY_DPI = 20
        private const val DEFAULT_MIN = 40
        private const val DISPLAY_ID = 1
        private const val NAVBAR_HEIGHT = 50
        private const val CAPTION_HEIGHT = 50
        private const val DISALLOWED_AREA_FOR_END_BOUNDS_HEIGHT = 10
        private val DISPLAY_BOUNDS = Rect(0, 0, 2400, 1600)
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
        private val STABLE_BOUNDS_LANDSCAPE = Rect(
            DISPLAY_BOUNDS.left,
            DISPLAY_BOUNDS.top + CAPTION_HEIGHT,
            DISPLAY_BOUNDS.right,
            DISPLAY_BOUNDS.bottom - NAVBAR_HEIGHT
        )
        private val STABLE_BOUNDS_PORTRAIT = Rect(
            DISPLAY_BOUNDS.top,
            DISPLAY_BOUNDS.left + CAPTION_HEIGHT,
            DISPLAY_BOUNDS.bottom,
            DISPLAY_BOUNDS.right - NAVBAR_HEIGHT
        )
        private val VALID_DRAG_AREA = Rect(
            DISPLAY_BOUNDS.left - 100,
            STABLE_BOUNDS_LANDSCAPE.top,
            DISPLAY_BOUNDS.right - 100,
            DISPLAY_BOUNDS.bottom - 100
        )
    }
}
