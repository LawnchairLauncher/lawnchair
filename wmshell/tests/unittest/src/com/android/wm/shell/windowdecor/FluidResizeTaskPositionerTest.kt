package com.android.wm.shell.windowdecor

import android.app.ActivityManager
import android.app.WindowConfiguration
import android.content.Context
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.Surface
import android.view.Surface.ROTATION_270
import android.view.Surface.ROTATION_90
import android.view.SurfaceControl
import android.view.WindowManager
import android.window.TransitionInfo
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.Change.CHANGE_DRAG_RESIZING
import androidx.test.filters.SmallTest
import com.android.wm.shell.R
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_BOTTOM
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_RIGHT
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_TOP
import com.android.wm.shell.windowdecor.DragPositioningCallback.CTRL_TYPE_UNDEFINED
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.any
import org.mockito.Mockito.argThat
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import java.util.function.Supplier
import org.mockito.Mockito.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever

/**
 * Tests for [FluidResizeTaskPositioner].
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:FluidResizeTaskPositionerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class FluidResizeTaskPositionerTest : ShellTestCase() {

    @Mock
    private lateinit var mockShellTaskOrganizer: ShellTaskOrganizer
    @Mock
    private lateinit var mockTransitions: Transitions
    @Mock
    private lateinit var mockWindowDecoration: WindowDecoration<*>
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
    private lateinit var mockContext: Context
    @Mock
    private lateinit var mockResources: Resources
    private lateinit var taskPositioner: FluidResizeTaskPositioner

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)

        whenever(taskToken.asBinder()).thenReturn(taskBinder)
        whenever(mockDisplayController.getDisplayLayout(DISPLAY_ID)).thenReturn(mockDisplayLayout)
        whenever(mockDisplayLayout.densityDpi()).thenReturn(DENSITY_DPI)
        whenever(mockDisplayLayout.getStableBounds(any())).thenAnswer { i ->
            if (mockWindowDecoration.mTaskInfo.configuration.windowConfiguration
                    .displayRotation == ROTATION_90 ||
                mockWindowDecoration.mTaskInfo.configuration.windowConfiguration
                    .displayRotation == ROTATION_270
            ) {
                (i.arguments.first() as Rect).set(STABLE_BOUNDS_LANDSCAPE)
            } else {
                (i.arguments.first() as Rect).set(STABLE_BOUNDS_PORTRAIT)
            }
        }
        `when`(mockDisplayLayout.stableInsets()).thenReturn(STABLE_INSETS)
        `when`(mockTransactionFactory.get()).thenReturn(mockTransaction)

        mockWindowDecoration.mTaskInfo = ActivityManager.RunningTaskInfo().apply {
            taskId = TASK_ID
            token = taskToken
            minWidth = MIN_WIDTH
            minHeight = MIN_HEIGHT
            defaultMinSize = DEFAULT_MIN
            displayId = DISPLAY_ID
            configuration.windowConfiguration.setBounds(STARTING_BOUNDS)
            configuration.windowConfiguration.displayRotation = ROTATION_90
        }
        `when`(mockWindowDecoration.calculateValidDragArea()).thenReturn(VALID_DRAG_AREA)
        mockWindowDecoration.mDisplay = mockDisplay
        mockWindowDecoration.mDecorWindowContext = mockContext
        whenever(mockWindowDecoration.mDecorWindowContext.resources).thenReturn(mockResources)
        whenever(mockResources.getDimensionPixelSize(R.dimen.desktop_mode_minimum_window_width))
                .thenReturn(DESKTOP_MODE_MIN_WIDTH)
        whenever(mockResources.getDimensionPixelSize(R.dimen.desktop_mode_minimum_window_height))
                .thenReturn(DESKTOP_MODE_MIN_HEIGHT)
        whenever(mockDisplay.displayId).thenAnswer { DISPLAY_ID }
        whenever(mockTransitions.startTransition(anyInt(), any(), any()))
                .doReturn(mockTransitionBinder)

        taskPositioner = FluidResizeTaskPositioner(
                mockShellTaskOrganizer,
                mockTransitions,
                mockWindowDecoration,
                mockDisplayController,
                mockDragStartListener,
                mockTransactionFactory
        )
    }

    @Test
    fun testDragResize_notMove_skipsTransitionOnEnd() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningEnd(
                STARTING_BOUNDS.left.toFloat() + 10,
                STARTING_BOUNDS.top.toFloat() + 10
        )

        verify(mockTransitions, never()).startTransition(
                eq(WindowManager.TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0)
            }}, eq(taskPositioner))
    }

    @Test
    fun testDragResize_noEffectiveMove_skipsTransitionOnMoveAndEnd() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningMove(
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0)
            }
        })

        taskPositioner.onDragPositioningEnd(
                STARTING_BOUNDS.left.toFloat() + 10,
                STARTING_BOUNDS.top.toFloat() + 10
        )

        verify(mockTransitions, never()).startTransition(
                eq(WindowManager.TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0)
            }}, eq(taskPositioner))
    }

    @Test
    fun testDragResize_hasEffectiveMove_issuesTransitionOnMoveAndEnd() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_TOP or CTRL_TYPE_RIGHT,
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        taskPositioner.onDragPositioningMove(
                STARTING_BOUNDS.left.toFloat() + 10,
                STARTING_BOUNDS.top.toFloat()
        )
        val rectAfterMove = Rect(STARTING_BOUNDS)
        rectAfterMove.right += 10
        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterMove
            }
        })

        taskPositioner.onDragPositioningEnd(
                STARTING_BOUNDS.left.toFloat() + 10,
                STARTING_BOUNDS.top.toFloat() + 10
        )
        val rectAfterEnd = Rect(rectAfterMove)
        rectAfterEnd.top += 10
        verify(mockTransitions).startTransition(
                eq(WindowManager.TRANSIT_CHANGE), argThat { wct ->
        return@argThat wct.changes.any { (token, change) ->
            token == taskBinder &&
                    (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                    change.configuration.windowConfiguration.bounds == rectAfterEnd
            }}, eq(taskPositioner))
    }

    @Test
    fun testDragResize_move_skipsDragResizingFlag() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_UNDEFINED, // Move
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Move the task 10px to the right.
        val newX = STARTING_BOUNDS.left.toFloat() + 10
        val newY = STARTING_BOUNDS.top.toFloat()
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.changeMask and CHANGE_DRAG_RESIZING) != 0) &&
                        change.dragResizing
            }
        })
        verify(mockTransitions, never()).startTransition(
                eq(WindowManager.TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.changeMask and CHANGE_DRAG_RESIZING) != 0) &&
                        change.dragResizing
            }}, eq(taskPositioner))
    }

    @Test
    fun testDragResize_resize_setsDragResizingFlag() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT, // Resize right
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Resize the task by 10px to the right.
        val newX = STARTING_BOUNDS.right.toFloat() + 10
        val newY = STARTING_BOUNDS.top.toFloat()
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.changeMask and CHANGE_DRAG_RESIZING) != 0) &&
                        change.dragResizing
            }
        })
        verify(mockTransitions).startTransition(
                eq(WindowManager.TRANSIT_CHANGE), argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.changeMask and CHANGE_DRAG_RESIZING) != 0) &&
                        !change.dragResizing
            }}, eq(taskPositioner))
    }

    @Test
    fun testDragResize_resize_setBoundsDoesNotChangeHeightWhenLessThanMin() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Resize to width of 95px and height of 5px with min height of 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 5
        val newY = STARTING_BOUNDS.top.toFloat() + 95
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS)
                                != 0) && change.configuration.windowConfiguration.bounds.top ==
                        STARTING_BOUNDS.top &&
                        change.configuration.windowConfiguration.bounds.bottom ==
                        STARTING_BOUNDS.bottom
            }
        })
    }

    @Test
    fun testDragResize_resize_setBoundsDoesNotChangeWidthWhenLessThanMin() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Resize to height of 95px and width of 5px with min width of 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 95
        val newY = STARTING_BOUNDS.top.toFloat() + 5
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS)
                                != 0) && change.configuration.windowConfiguration.bounds.right ==
                        STARTING_BOUNDS.right &&
                        change.configuration.windowConfiguration.bounds.left ==
                        STARTING_BOUNDS.left
            }
        })
    }

    @Test
    fun testDragResize_resize_setBoundsDoesNotChangeHeightWhenNegative() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Resize to height of -5px and width of 95px
        val newX = STARTING_BOUNDS.right.toFloat() - 5
        val newY = STARTING_BOUNDS.top.toFloat() + 105
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS)
                                != 0) && change.configuration.windowConfiguration.bounds.top ==
                        STARTING_BOUNDS.top &&
                        change.configuration.windowConfiguration.bounds.bottom ==
                        STARTING_BOUNDS.bottom
            }
        })
    }

    @Test
    fun testDragResize_resize_setBoundsDoesNotChangeWidthWhenNegative() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Resize to width of -5px and height of 95px
        val newX = STARTING_BOUNDS.right.toFloat() - 105
        val newY = STARTING_BOUNDS.top.toFloat() + 5
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS)
                                != 0) && change.configuration.windowConfiguration.bounds.right ==
                        STARTING_BOUNDS.right &&
                        change.configuration.windowConfiguration.bounds.left ==
                        STARTING_BOUNDS.left
            }
        })
    }

    @Test
    fun testDragResize_resize_setBoundsRunsWhenResizeBoundsValid() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Shrink to height 20px and width 20px with both min height/width equal to 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 80
        val newY = STARTING_BOUNDS.top.toFloat() + 80
        taskPositioner.onDragPositioningMove(
                newX,
                newY
        )

        taskPositioner.onDragPositioningEnd(newX, newY)

        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        ((change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0)
            }
        })
    }

    @Test
    fun testDragResize_resize_setBoundsDoesNotRunWithNegativeHeightAndWidth() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Shrink to height 5px and width 5px with both min height/width equal to 10px
        val newX = STARTING_BOUNDS.right.toFloat() - 95
        val newY = STARTING_BOUNDS.top.toFloat() + 95
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
    fun testDragResize_resize_useDefaultMinWhenMinWidthInvalid() {
        mockWindowDecoration.mTaskInfo.minWidth = -1

        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Shrink to width and height of 3px with invalid minWidth = -1 and defaultMinSize = 5px
        val newX = STARTING_BOUNDS.right.toFloat() - 97
        val newY = STARTING_BOUNDS.top.toFloat() + 97
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
    fun testDragResize_resize_useMinWidthWhenValid() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_TOP, // Resize right and top
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // Shrink to width and height of 7px with valid minWidth = 10px and defaultMinSize = 5px
        val newX = STARTING_BOUNDS.right.toFloat() - 93
        val newY = STARTING_BOUNDS.top.toFloat() + 93
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

    fun testDragResize_toDisallowedBounds_freezesAtLimit() {
        taskPositioner.onDragPositioningStart(
                CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM, // Resize right-bottom corner
                STARTING_BOUNDS.right.toFloat(),
                STARTING_BOUNDS.bottom.toFloat()
        )

        // Resize the task by 10px to the right and bottom, a valid destination
        val newBounds = Rect(
                STARTING_BOUNDS.left,
                STARTING_BOUNDS.top,
                STARTING_BOUNDS.right + 10,
                STARTING_BOUNDS.bottom + 10)
        taskPositioner.onDragPositioningMove(
                newBounds.right.toFloat(),
                newBounds.bottom.toFloat()
        )

        // Resize the task by another 10px to the right (allowed) and to just in the disallowed
        // area of the Y coordinate.
        val newBounds2 = Rect(
                newBounds.left,
                newBounds.top,
                newBounds.right + 10,
                DISALLOWED_RESIZE_AREA.top
        )
        taskPositioner.onDragPositioningMove(
                newBounds2.right.toFloat(),
                newBounds2.bottom.toFloat()
        )

        taskPositioner.onDragPositioningEnd(newBounds2.right.toFloat(), newBounds2.bottom.toFloat())

        // The first resize falls in the allowed area, verify there's a change for it.
        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder && change.ofBounds(newBounds)
            }
        })
        // The second resize falls in the disallowed area, verify there's no change for it.
        verify(mockShellTaskOrganizer, never()).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder && change.ofBounds(newBounds2)
            }
        })
        // Instead, there should be a change for its allowed portion (the X movement) with the Y
        // staying frozen in the last valid resize position.
        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder && change.ofBounds(
                        Rect(
                                newBounds2.left,
                                newBounds2.top,
                                newBounds2.right,
                                newBounds.bottom // Stayed at the first resize destination.
                        )
                )
            }
        })
    }

    @Test
    fun testStartAnimation_useEndRelOffset() {
        val mockTransitionInfo = mock(TransitionInfo::class.java)
        val changeMock = mock(TransitionInfo.Change::class.java)
        val startTransaction = mock(SurfaceControl.Transaction::class.java)
        val finishTransaction = mock(SurfaceControl.Transaction::class.java)
        val point = Point(10, 20)
        val bounds = Rect(1, 2, 3, 4)
        `when`(changeMock.endRelOffset).thenReturn(point)
        `when`(changeMock.endAbsBounds).thenReturn(bounds)
        `when`(mockTransitionInfo.changes).thenReturn(listOf(changeMock))
        `when`(startTransaction.setWindowCrop(any(),
            eq(bounds.width()),
            eq(bounds.height()))).thenReturn(startTransaction)
        `when`(finishTransaction.setWindowCrop(any(),
            eq(bounds.width()),
            eq(bounds.height()))).thenReturn(finishTransaction)

        taskPositioner.startAnimation(mockTransitionBinder, mockTransitionInfo, startTransaction,
            finishTransaction, { _ -> })

        verify(startTransaction).setPosition(any(), eq(point.x.toFloat()), eq(point.y.toFloat()))
        verify(finishTransaction).setPosition(any(), eq(point.x.toFloat()), eq(point.y.toFloat()))
        verify(changeMock).endRelOffset
    }

    private fun WindowContainerTransaction.Change.ofBounds(bounds: Rect): Boolean {
        return ((windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0) &&
                bounds == configuration.windowConfiguration.bounds
    }

    @Test
    fun testDragResize_resize_resizingTaskReorderedToTopWhenNotFocused() {
        mockWindowDecoration.mTaskInfo.isFocused = false
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
        mockWindowDecoration.mTaskInfo.isFocused = true
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
        mockWindowDecoration.mTaskInfo.isFocused = false
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
        verify(mockDisplayLayout, Mockito.times(1)).getStableBounds(any())
        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterDrag
            }
        })
        // Drag back to starting bounds.
        performDrag(
            STARTING_BOUNDS.right.toFloat() + 2000, STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat(),
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM)

        // Display did not rotate; we should use previous stable bounds
        verify(mockDisplayLayout, Mockito.times(1)).getStableBounds(any())

        // Rotate the screen to portrait
        mockWindowDecoration.mTaskInfo.apply {
            configuration.windowConfiguration.displayRotation = Surface.ROTATION_0
        }
        // Test portrait stable bounds
        performDrag(
            STARTING_BOUNDS.right.toFloat(), STARTING_BOUNDS.bottom.toFloat(),
            STARTING_BOUNDS.right.toFloat() + 2000, STARTING_BOUNDS.bottom.toFloat() + 2000,
            CTRL_TYPE_RIGHT or CTRL_TYPE_BOTTOM)
        rectAfterDrag.right -= 2000
        rectAfterDrag.bottom += 2000

        verify(mockShellTaskOrganizer).applyTransaction(argThat { wct ->
            return@argThat wct.changes.any { (token, change) ->
                token == taskBinder &&
                        (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0 &&
                        change.configuration.windowConfiguration.bounds == rectAfterDrag
            }
        })
        // Display has rotated; we expect a new stable bounds.
        verify(mockDisplayLayout, Mockito.times(2)).getStableBounds(any())
    }

    @Test
    fun testIsResizingOrAnimatingResizeSet() {
        assertFalse(taskPositioner.isResizingOrAnimating)

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
        assertTrue(taskPositioner.isResizingOrAnimating)

        taskPositioner.onDragPositioningEnd(
                STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat()
        )

        // isResizingOrAnimating should be not be set till false until after transition animation
        assertTrue(taskPositioner.isResizingOrAnimating)
    }

    @Test
    fun testIsResizingOrAnimatingResizeResetAfterAbortedTransition() {
        performDrag(STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat(), STARTING_BOUNDS.left.toFloat() - 20,
                STARTING_BOUNDS.top.toFloat() - 20, CTRL_TYPE_TOP or CTRL_TYPE_RIGHT)

        taskPositioner.onTransitionConsumed(mockTransitionBinder, true /* aborted */,
                mockTransaction)

        // isResizingOrAnimating should be set to false until after transition successfully consumed
        assertFalse(taskPositioner.isResizingOrAnimating)
    }

    @Test
    fun testIsResizingOrAnimatingResizeResetAfterNonAbortedTransition() {
        performDrag(STARTING_BOUNDS.left.toFloat(),
                STARTING_BOUNDS.top.toFloat(), STARTING_BOUNDS.left.toFloat() - 20,
                STARTING_BOUNDS.top.toFloat() - 20, CTRL_TYPE_TOP or CTRL_TYPE_RIGHT)

        taskPositioner.onTransitionConsumed(mockTransitionBinder, false /* aborted */,
                mockTransaction)

        // isResizingOrAnimating should be set to false until after transition successfully consumed
        assertFalse(taskPositioner.isResizingOrAnimating)
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
        private const val DESKTOP_MODE_MIN_WIDTH = 20
        private const val DESKTOP_MODE_MIN_HEIGHT = 20
        private const val DENSITY_DPI = 20
        private const val DEFAULT_MIN = 40
        private const val DISPLAY_ID = 1
        private const val NAVBAR_HEIGHT = 50
        private const val CAPTION_HEIGHT = 50
        private const val DISALLOWED_AREA_FOR_END_BOUNDS_HEIGHT = 10
        private val DISPLAY_BOUNDS = Rect(0, 0, 2400, 1600)
        private val STARTING_BOUNDS = Rect(100, 100, 200, 200)
        private val STABLE_INSETS = Rect(0, 50, 0, 0)
        private val DISALLOWED_RESIZE_AREA = Rect(
                DISPLAY_BOUNDS.left,
                DISPLAY_BOUNDS.bottom - NAVBAR_HEIGHT,
                DISPLAY_BOUNDS.right,
                DISPLAY_BOUNDS.bottom)
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
