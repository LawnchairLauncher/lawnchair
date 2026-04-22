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
package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.testing.AndroidTestingRunner
import android.testing.TestableResources
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
import androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread
import com.android.internal.jank.InteractionJankMonitor
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.MultiDisplayDragMoveIndicatorController
import com.android.wm.shell.common.MultiDisplayTestUtil.TestDisplay
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Tests for [MultiDisplayVeiledResizeTaskPositioner].
 *
 * Build/Install/Run: atest WMShellUnitTests:MultiDisplayVeiledResizeTaskPositionerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class MultiDisplayVeiledResizeTaskPositionerTest : ShellTestCase() {

    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockDesktopWindowDecoration = mock<DesktopModeWindowDecoration>()
    private val mockDragEventListener = mock<DragPositioningCallbackUtility.DragEventListener>()

    private val taskToken = mock<WindowContainerToken>()
    private val taskBinder = mock<IBinder>()

    private val mockDisplayController = mock<DisplayController>()
    private val mockDisplay = mock<Display>()
    private val mockTransactionFactory = mock<Supplier<SurfaceControl.Transaction>>()
    private val mockTransaction = mock<SurfaceControl.Transaction>()
    private val mockTransitionBinder = mock<IBinder>()
    private val mockTransitionInfo = mock<TransitionInfo>()
    private val mockFinishCallback = mock<TransitionFinishCallback>()
    private val mockTransitions = mock<Transitions>()
    private val mockInteractionJankMonitor = mock<InteractionJankMonitor>()
    private val mockSurfaceControl = mock<SurfaceControl>()
    private val mockMultiDisplayDragMoveIndicatorController =
        mock<MultiDisplayDragMoveIndicatorController>()
    private lateinit var resources: TestableResources
    private lateinit var spyDisplayLayout0: DisplayLayout
    private lateinit var spyDisplayLayout1: DisplayLayout
    private val desktopState = FakeDesktopState()

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var taskPositioner: MultiDisplayVeiledResizeTaskPositioner

    @Before
    fun setUp() {
        whenever(taskToken.asBinder()).thenReturn(taskBinder)
        mockDesktopWindowDecoration.mDisplay = mockDisplay
        mockDesktopWindowDecoration.mDecorWindowContext = mContext
        resources = mContext.orCreateTestableResources
        val resourceConfiguration = Configuration()
        resourceConfiguration.uiMode = 0
        resources.overrideConfiguration(resourceConfiguration)
        spyDisplayLayout0 = TestDisplay.DISPLAY_0.getSpyDisplayLayout(resources.resources)
        spyDisplayLayout1 = TestDisplay.DISPLAY_1.getSpyDisplayLayout(resources.resources)

        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID_0)).thenReturn(spyDisplayLayout0)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID_1)).thenReturn(spyDisplayLayout1)
        whenever(spyDisplayLayout0.densityDpi()).thenReturn(DENSITY_DPI)
        whenever(spyDisplayLayout1.densityDpi()).thenReturn(DENSITY_DPI)
        doAnswer { i ->
                val rect = i.getArgument<Rect>(0)
                if (
                    mockDesktopWindowDecoration.mTaskInfo.configuration.windowConfiguration
                        .displayRotation == ROTATION_90 ||
                        mockDesktopWindowDecoration.mTaskInfo.configuration.windowConfiguration
                            .displayRotation == ROTATION_270
                ) {
                    rect.set(STABLE_BOUNDS_LANDSCAPE)
                } else {
                    rect.set(STABLE_BOUNDS_PORTRAIT)
                }
                null
            }
            .whenever(spyDisplayLayout0)
            .getStableBounds(any())
        whenever(mockTransactionFactory.get()).thenReturn(mockTransaction)
        whenever(mockDesktopWindowDecoration.leash).thenReturn(mockSurfaceControl)
        whenever(mockTransaction.setPosition(any(), any(), any())).thenReturn(mockTransaction)
        whenever(mockTransaction.setAlpha(any(), any())).thenReturn(mockTransaction)
        mockDesktopWindowDecoration.mTaskInfo =
            ActivityManager.RunningTaskInfo().apply {
                taskId = TASK_ID
                token = taskToken
                minWidth = MIN_WIDTH
                minHeight = MIN_HEIGHT
                defaultMinSize = DEFAULT_MIN
                displayId = DISPLAY_ID_0
                configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
                configuration.windowConfiguration.displayRotation = ROTATION_90
                isResizeable = true
            }
        whenever(mockDesktopWindowDecoration.calculateValidDragArea()).thenReturn(VALID_DRAG_AREA)
        mockDesktopWindowDecoration.mDisplay = mockDisplay
        whenever(mockDisplay.displayId).thenAnswer { DISPLAY_ID_0 }

        taskPositioner =
            MultiDisplayVeiledResizeTaskPositioner(
                mockShellTaskOrganizer,
                mockDesktopWindowDecoration,
                mockDisplayController,
                { mockTransaction },
                mockTransitions,
                mockInteractionJankMonitor,
                mainHandler,
                mockMultiDisplayDragMoveIndicatorController,
                desktopState,
            )
    }

    @Test
    fun testDragResize_noMove_doesNotShowResizeVeil() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )
        verify(mockDesktopWindowDecoration, never()).showResizeVeil(STARTING_BOUNDS)

        taskPositioner.onDragPositioningEnd(
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        verify(mockTransitions, never())
            .startTransition(
                eq(TRANSIT_CHANGE),
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0 &&
                            change.configuration.windowConfiguration.bounds == STARTING_BOUNDS
                    }
                },
                eq(taskPositioner),
            )
        verify(mockDesktopWindowDecoration, never()).hideResizeVeil()
        verifyNoInteractions(mockMultiDisplayDragMoveIndicatorController)
    }

    @Test
    fun testDragResize_movesTask_doesNotShowResizeVeil() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_UNDEFINED,
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        taskPositioner.onDragPositioningMove(
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat() + 60,
            STARTING_BOUNDS.top.toFloat() + 100,
        )
        val rectAfterMove = Rect(STARTING_BOUNDS)
        rectAfterMove.offset(60, 100)
        verify(mockTransaction)
            .setPosition(any(), eq(rectAfterMove.left.toFloat()), eq(rectAfterMove.top.toFloat()))

        val endBounds =
            taskPositioner.onDragPositioningEnd(
                DISPLAY_ID_0,
                STARTING_BOUNDS.left.toFloat() + 70,
                STARTING_BOUNDS.top.toFloat() + 20,
            )
        val rectAfterEnd = Rect(STARTING_BOUNDS)
        rectAfterEnd.offset(70, 20)

        verify(mockDesktopWindowDecoration, never()).showResizeVeil(any())
        verify(mockDesktopWindowDecoration, never()).hideResizeVeil()
        verify(mockMultiDisplayDragMoveIndicatorController).onDragEnd(eq(TASK_ID), any())
        Assert.assertEquals(rectAfterEnd, endBounds)
    }

    @Test
    fun testDragResize_movesTaskOnSameDisplay_noPxDpConversion() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_UNDEFINED,
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        taskPositioner.onDragPositioningEnd(
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat() + 70,
            STARTING_BOUNDS.top.toFloat() + 20,
        )

        verify(spyDisplayLayout0, never()).localPxToGlobalDp(any(), any())
        verify(spyDisplayLayout0, never()).globalDpToLocalPx(any(), any())
        verify(mockMultiDisplayDragMoveIndicatorController).onDragEnd(eq(TASK_ID), any())
    }

    @Test
    fun testDragResize_movesTaskToNewDisplay() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_UNDEFINED,
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        taskPositioner.onDragPositioningMove(DISPLAY_ID_1, 200f, 1900f)

        val rectAfterMove = Rect(200, -50, 300, 50)
        verify(mockTransaction)
            .setPosition(any(), eq(rectAfterMove.left.toFloat()), eq(rectAfterMove.top.toFloat()))
        verify(mockTransaction)
            .setAlpha(eq(mockDesktopWindowDecoration.leash), eq(ALPHA_FOR_TRANSLUCENT_WINDOW))

        val endBounds = taskPositioner.onDragPositioningEnd(DISPLAY_ID_1, 300f, 450f)
        val rectAfterEnd = Rect(300, 450, 500, 650)

        verify(mockDesktopWindowDecoration, never()).showResizeVeil(any())
        verify(mockDesktopWindowDecoration, never()).hideResizeVeil()
        verify(mockMultiDisplayDragMoveIndicatorController).onDragEnd(eq(TASK_ID), any())
        Assert.assertEquals(rectAfterEnd, endBounds)
    }

    @Test
    fun testDragResize_movesTaskToNewDisplayThenBackToOriginalDisplay() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_UNDEFINED,
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        val inOrder = inOrder(mockTransaction)

        // Move to the display 1
        taskPositioner.onDragPositioningMove(DISPLAY_ID_1, 200f, 800f)
        val rectAfterMove = Rect(200, -600, 300, -400)
        inOrder
            .verify(mockTransaction)
            .setPosition(any(), eq(rectAfterMove.left.toFloat()), eq(rectAfterMove.top.toFloat()))
        inOrder
            .verify(mockTransaction)
            .setAlpha(eq(mockDesktopWindowDecoration.leash), eq(ALPHA_FOR_TRANSLUCENT_WINDOW))

        // Moving back to the original display
        taskPositioner.onDragPositioningMove(DISPLAY_ID_0, 100f, 1500f)
        rectAfterMove.set(100, 1500, 200, 1700)
        inOrder
            .verify(mockTransaction)
            .setPosition(any(), eq(rectAfterMove.left.toFloat()), eq(rectAfterMove.top.toFloat()))
        inOrder
            .verify(mockTransaction)
            .setAlpha(eq(mockDesktopWindowDecoration.leash), eq(ALPHA_FOR_VISIBLE_WINDOW))

        // Finish the drag move on the original display
        val endBounds = taskPositioner.onDragPositioningEnd(DISPLAY_ID_0, 50f, 50f)
        rectAfterMove.set(50, 50, 150, 150)

        verify(mockDesktopWindowDecoration, never()).showResizeVeil(any())
        verify(mockDesktopWindowDecoration, never()).hideResizeVeil()
        verify(mockMultiDisplayDragMoveIndicatorController).onDragEnd(eq(TASK_ID), any())
        Assert.assertEquals(rectAfterMove, endBounds)
    }

    @Test
    fun testDragResize_resize_boundsUpdateOnEnd() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_RIGHT or CTRL_TYPE_TOP,
            DISPLAY_ID_0,
            STARTING_BOUNDS.right.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        taskPositioner.onDragPositioningMove(
            DISPLAY_ID_0,
            STARTING_BOUNDS.right.toFloat() + 10,
            STARTING_BOUNDS.top.toFloat() + 10,
        )

        val rectAfterMove = Rect(STARTING_BOUNDS)
        rectAfterMove.right += 10
        rectAfterMove.top += 10
        verify(mockDesktopWindowDecoration).showResizeVeil(rectAfterMove)
        verify(mockShellTaskOrganizer, never())
            .applyTransaction(
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0 &&
                            change.configuration.windowConfiguration.bounds == rectAfterMove
                    }
                }
            )

        taskPositioner.onDragPositioningEnd(
            DISPLAY_ID_0,
            STARTING_BOUNDS.right.toFloat() + 20,
            STARTING_BOUNDS.top.toFloat() + 20,
        )
        val rectAfterEnd = Rect(rectAfterMove)
        rectAfterEnd.right += 10
        rectAfterEnd.top += 10
        verify(mockDesktopWindowDecoration).updateResizeVeil(any())
        verify(mockTransitions)
            .startTransition(
                eq(TRANSIT_CHANGE),
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0 &&
                            change.configuration.windowConfiguration.bounds == rectAfterEnd
                    }
                },
                eq(taskPositioner),
            )
        verifyNoInteractions(mockMultiDisplayDragMoveIndicatorController)
    }

    @Test
    fun testDragResize_noEffectiveMove_skipsTransactionOnEnd() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            DISPLAY_ID_0,
            CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        taskPositioner.onDragPositioningMove(
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        taskPositioner.onDragPositioningEnd(
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat() + 10,
            STARTING_BOUNDS.top.toFloat() + 10,
        )

        verify(mockTransitions, never())
            .startTransition(
                eq(TRANSIT_CHANGE),
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0 &&
                            change.configuration.windowConfiguration.bounds == STARTING_BOUNDS
                    }
                },
                eq(taskPositioner),
            )

        verify(mockShellTaskOrganizer, never())
            .applyTransaction(
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0)
                    }
                }
            )
    }

    @Test
    fun testDragResize_drag_setBoundsNotRunIfDragEndsInDisallowedEndArea() = runOnUiThread {
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_UNDEFINED, // drag
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        val newX = STARTING_BOUNDS.left.toFloat() + 5
        val newY = DISALLOWED_AREA_FOR_END_BOUNDS_HEIGHT.toFloat() - 1
        taskPositioner.onDragPositioningMove(DISPLAY_ID_0, newX, newY)

        taskPositioner.onDragPositioningEnd(DISPLAY_ID_0, newX, newY)

        verify(mockShellTaskOrganizer, never())
            .applyTransaction(
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0)
                    }
                }
            )
    }

    @Test
    fun testDragResize_resize_resizingTaskReorderedToTopWhenNotFocused() = runOnUiThread {
        mockDesktopWindowDecoration.mHasGlobalFocus = false
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_RIGHT, // Resize right
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        // Verify task is reordered to top
        verify(mockShellTaskOrganizer)
            .applyTransaction(
                argThat { wct ->
                    return@argThat wct.hierarchyOps.any { hierarchyOps ->
                        hierarchyOps.container == taskBinder && hierarchyOps.toTop
                    }
                }
            )
    }

    @Test
    fun testDragResize_resize_resizingTaskNotReorderedToTopWhenFocused() = runOnUiThread {
        mockDesktopWindowDecoration.mHasGlobalFocus = true
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_RIGHT, // Resize right
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        // Verify task is not reordered to top
        verify(mockShellTaskOrganizer, never())
            .applyTransaction(
                argThat { wct ->
                    return@argThat wct.hierarchyOps.any { hierarchyOps ->
                        hierarchyOps.container == taskBinder && hierarchyOps.toTop
                    }
                }
            )
    }

    @Test
    fun testDragResize_drag_draggedTaskNotReorderedToTop() = runOnUiThread {
        mockDesktopWindowDecoration.mHasGlobalFocus = false
        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_UNDEFINED, // drag
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        // Verify task is not reordered to top since task is already brought to top before dragging
        // begins
        verify(mockShellTaskOrganizer, never())
            .applyTransaction(
                argThat { wct ->
                    return@argThat wct.hierarchyOps.any { hierarchyOps ->
                        hierarchyOps.container == taskBinder && hierarchyOps.toTop
                    }
                }
            )
    }

    @Test
    fun testDragResize_drag_updatesStableBoundsOnRotate() = runOnUiThread {
        // Test landscape stable bounds
        performDrag(
            STARTING_BOUNDS.right.toFloat(),
            STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat() + 2000,
            STARTING_BOUNDS.bottom.toFloat() + 2000,
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
        )
        val rectAfterDrag = Rect(STARTING_BOUNDS)
        rectAfterDrag.right += 2000
        rectAfterDrag.bottom = STABLE_BOUNDS_LANDSCAPE.bottom
        // First drag; we should fetch stable bounds.
        verify(spyDisplayLayout0, times(1)).getStableBounds(any())
        verify(mockTransitions)
            .startTransition(
                eq(TRANSIT_CHANGE),
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0 &&
                            change.configuration.windowConfiguration.bounds == rectAfterDrag
                    }
                },
                eq(taskPositioner),
            )
        // Drag back to starting bounds.
        performDrag(
            STARTING_BOUNDS.right.toFloat() + 2000,
            STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat(),
            STARTING_BOUNDS.bottom.toFloat(),
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
        )

        // Display did not rotate; we should use previous stable bounds
        verify(spyDisplayLayout0, times(1)).getStableBounds(any())

        // Rotate the screen to portrait
        mockDesktopWindowDecoration.mTaskInfo.apply {
            configuration.windowConfiguration.displayRotation = ROTATION_0
        }
        // Test portrait stable bounds
        performDrag(
            STARTING_BOUNDS.right.toFloat(),
            STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat() + 2000,
            STARTING_BOUNDS.bottom.toFloat() + 2000,
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM,
        )
        rectAfterDrag.right = STABLE_BOUNDS_PORTRAIT.right
        rectAfterDrag.bottom = STARTING_BOUNDS.bottom + 2000

        verify(mockTransitions)
            .startTransition(
                eq(TRANSIT_CHANGE),
                argThat { wct ->
                    return@argThat wct.changes.any { (token, change) ->
                        token == taskBinder &&
                            (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) !=
                                0 &&
                            change.configuration.windowConfiguration.bounds == rectAfterDrag
                    }
                },
                eq(taskPositioner),
            )
        // Display has rotated; we expect a new stable bounds.
        verify(spyDisplayLayout0, times(2)).getStableBounds(any())
    }

    @Test
    fun testClose() = runOnUiThread {
        verify(mockDisplayController, times(1)).addDisplayWindowListener(eq(taskPositioner))

        taskPositioner.close()

        verify(mockDisplayController, times(1)).removeDisplayWindowListener(eq(taskPositioner))
    }

    @Test
    fun testIsResizingOrAnimatingResizeSet() = runOnUiThread {
        taskPositioner.addDragEventListener(mockDragEventListener)
        Assert.assertFalse(taskPositioner.isResizingOrAnimating)

        taskPositioner.onDragPositioningStart(
            CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        taskPositioner.onDragPositioningMove(
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat() - 20,
            STARTING_BOUNDS.top.toFloat() - 20,
        )

        // isResizingOrAnimating should be set to true after move during a resize
        Assert.assertTrue(taskPositioner.isResizingOrAnimating)
        verify(mockDragEventListener, times(1)).onDragMove(eq(TASK_ID))

        taskPositioner.onDragPositioningEnd(
            DISPLAY_ID_0,
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
        )

        // isResizingOrAnimating should be not be set till false until after transition animation
        Assert.assertTrue(taskPositioner.isResizingOrAnimating)
    }

    @Test
    fun testIsResizingOrAnimatingResizeResetAfterStartAnimation() = runOnUiThread {
        performDrag(
            STARTING_BOUNDS.left.toFloat(),
            STARTING_BOUNDS.top.toFloat(),
            STARTING_BOUNDS.left.toFloat() - 20,
            STARTING_BOUNDS.top.toFloat() - 20,
            CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
        )

        taskPositioner.startAnimation(
            mockTransitionBinder,
            mockTransitionInfo,
            mockTransaction,
            mockTransaction,
            mockFinishCallback,
        )

        // isResizingOrAnimating should be set to false until after transition successfully consumed
        Assert.assertFalse(taskPositioner.isResizingOrAnimating)
    }

    @Test
    fun testStartAnimation_updatesLeash() = runOnUiThread {
        val changeMock = mock<TransitionInfo.Change>()
        val nonTaskChangeMock = mock<TransitionInfo.Change>()
        val taskLeash = mock<SurfaceControl>()
        val nonTaskLeash = mock<SurfaceControl>()
        val startTransaction = mock<Transaction>()
        val finishTransaction = mock<Transaction>()
        val point = Point(10, 20)
        val bounds = Rect(1, 2, 3, 4)
        whenever(changeMock.leash).thenReturn(taskLeash)
        whenever(changeMock.endRelOffset).thenReturn(point)
        whenever(changeMock.endAbsBounds).thenReturn(bounds)
        whenever(changeMock.taskInfo).thenReturn(ActivityManager.RunningTaskInfo())
        whenever(nonTaskChangeMock.leash).thenReturn(nonTaskLeash)
        whenever(nonTaskChangeMock.endRelOffset).thenReturn(point)
        whenever(nonTaskChangeMock.endAbsBounds).thenReturn(bounds)
        whenever(nonTaskChangeMock.taskInfo).thenReturn(null)
        whenever(mockTransitionInfo.changes).thenReturn(listOf(changeMock, nonTaskChangeMock))
        whenever(startTransaction.setWindowCrop(any(), eq(bounds.width()), eq(bounds.height())))
            .thenReturn(startTransaction)
        whenever(finishTransaction.setWindowCrop(any(), eq(bounds.width()), eq(bounds.height())))
            .thenReturn(finishTransaction)

        taskPositioner.startAnimation(
            mockTransitionBinder,
            mockTransitionInfo,
            startTransaction,
            finishTransaction,
            mockFinishCallback,
        )

        verify(startTransaction)
            .setPosition(eq(taskLeash), eq(point.x.toFloat()), eq(point.y.toFloat()))
        verify(finishTransaction)
            .setPosition(eq(taskLeash), eq(point.x.toFloat()), eq(point.y.toFloat()))
        verify(startTransaction, never()).setPosition(eq(nonTaskLeash), any(), any())
        verify(finishTransaction, never()).setPosition(eq(nonTaskLeash), any(), any())
        verify(changeMock).endRelOffset
    }

    private fun performDrag(startX: Float, startY: Float, endX: Float, endY: Float, ctrlType: Int) {
        taskPositioner.onDragPositioningStart(ctrlType, DISPLAY_ID_0, startX, startY)
        taskPositioner.onDragPositioningMove(DISPLAY_ID_0, endX, endY)

        taskPositioner.onDragPositioningEnd(DISPLAY_ID_0, endX, endY)
    }

    companion object {
        private const val TASK_ID = 5
        private const val MIN_WIDTH = 10
        private const val MIN_HEIGHT = 10
        private const val DENSITY_DPI = 20
        private const val DEFAULT_MIN = 40
        private const val DISPLAY_ID_0 = 0
        private const val DISPLAY_ID_1 = 1
        private const val NAVBAR_HEIGHT = 50
        private const val CAPTION_HEIGHT = 50
        private const val DISALLOWED_AREA_FOR_END_BOUNDS_HEIGHT = 10
        private const val ALPHA_FOR_TRANSLUCENT_WINDOW = 0.7f
        private const val ALPHA_FOR_VISIBLE_WINDOW = 1.0f
        private val DISPLAY_BOUNDS = Rect(0, 0, 2400, 1600)
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
        private val STABLE_BOUNDS_LANDSCAPE =
            Rect(
                DISPLAY_BOUNDS.left,
                DISPLAY_BOUNDS.top + CAPTION_HEIGHT,
                DISPLAY_BOUNDS.right,
                DISPLAY_BOUNDS.bottom - NAVBAR_HEIGHT,
            )
        private val STABLE_BOUNDS_PORTRAIT =
            Rect(
                DISPLAY_BOUNDS.top,
                DISPLAY_BOUNDS.left + CAPTION_HEIGHT,
                DISPLAY_BOUNDS.bottom,
                DISPLAY_BOUNDS.right - NAVBAR_HEIGHT,
            )
        private val VALID_DRAG_AREA =
            Rect(
                DISPLAY_BOUNDS.left - 100,
                STABLE_BOUNDS_LANDSCAPE.top,
                DISPLAY_BOUNDS.right - 100,
                DISPLAY_BOUNDS.bottom - 100,
            )
    }
}
