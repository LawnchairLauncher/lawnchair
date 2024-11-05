package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WindowingMode
import android.graphics.PointF
import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_IS_WALLPAPER
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.common.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP
import com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP
import com.android.wm.shell.transition.Transitions.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP
import com.android.wm.shell.windowdecor.MoveToDesktopAnimator
import junit.framework.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyZeroInteractions
import org.mockito.kotlin.whenever
import java.util.function.Supplier

/** Tests of [DragToDesktopTransitionHandler]. */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DragToDesktopTransitionHandlerTest : ShellTestCase() {

    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var splitScreenController: SplitScreenController
    @Mock private lateinit var dragAnimator: MoveToDesktopAnimator

    private val transactionSupplier = Supplier { mock<SurfaceControl.Transaction>() }

    private lateinit var handler: DragToDesktopTransitionHandler

    @Before
    fun setUp() {
        handler =
            DragToDesktopTransitionHandler(
                    context,
                    transitions,
                    taskDisplayAreaOrganizer,
                    transactionSupplier
                )
                .apply { setSplitScreenController(splitScreenController) }
    }

    @Test
    fun startDragToDesktop_animateDragWhenReady() {
        val task = createTask()
        // Simulate transition is started.
        val transition = startDragToDesktopTransition(task, dragAnimator)

        // Now it's ready to animate.
        handler.startAnimation(
            transition = transition,
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP,
                    draggedTask = task
                ),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {}
        )

        verify(dragAnimator).startAnimation()
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_startCancelTransition() {
        performEarlyCancel(DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL)
        verify(transitions)
            .startTransition(eq(TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP), any(), eq(handler))
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_verifySplitLeftCancel() {
        performEarlyCancel(DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_LEFT)
        verify(splitScreenController).requestEnterSplitSelect(
            any(),
            any(),
            eq(SPLIT_POSITION_TOP_OR_LEFT),
            any()
        )
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_verifySplitRightCancel() {
        performEarlyCancel(DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_RIGHT)
        verify(splitScreenController).requestEnterSplitSelect(
            any(),
            any(),
            eq(SPLIT_POSITION_BOTTOM_OR_RIGHT),
            any()
        )
    }

    @Test
    fun startDragToDesktop_aborted_finishDropped() {
        val task = createTask()
        // Simulate transition is started.
        val transition = startDragToDesktopTransition(task, dragAnimator)
        // But the transition was aborted.
        handler.onTransitionConsumed(transition, aborted = true, mock())

        // Attempt to finish the failed drag start.
        handler.finishDragToDesktopTransition(WindowContainerTransaction())

        // Should not be attempted and state should be reset.
        verify(transitions, never())
                .startTransition(eq(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP), any(), any())
        assertFalse(handler.inProgress)
    }

    @Test
    fun startDragToDesktop_aborted_cancelDropped() {
        val task = createTask()
        // Simulate transition is started.
        val transition = startDragToDesktopTransition(task, dragAnimator)
        // But the transition was aborted.
        handler.onTransitionConsumed(transition, aborted = true, mock())

        // Attempt to finish the failed drag start.
        handler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )

        // Should not be attempted and state should be reset.
        assertFalse(handler.inProgress)
    }

    @Test
    fun startDragToDesktop_anotherTransitionInProgress_startDropped() {
        val task = createTask()

        // Simulate attempt to start two drag to desktop transitions.
        startDragToDesktopTransition(task, dragAnimator)
        startDragToDesktopTransition(task, dragAnimator)

        // Verify transition only started once.
        verify(transitions, times(1)).startTransition(
                eq(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP),
                any(),
                eq(handler)
        )
    }

    @Test
    fun cancelDragToDesktop_startWasReady_cancel() {
        startDrag()

        // Then user cancelled after it had already started.
        handler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )

        // Cancel animation should run since it had already started.
        verify(dragAnimator).cancelAnimator()
    }

    @Test
    fun cancelDragToDesktop_splitLeftCancelType_splitRequested() {
        startDrag()

        // Then user cancelled it, requesting split.
        handler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_LEFT
        )

        // Verify the request went through split controller.
        verify(splitScreenController).requestEnterSplitSelect(
            any(),
            any(),
            eq(SPLIT_POSITION_TOP_OR_LEFT),
            any()
        )
    }

    @Test
    fun cancelDragToDesktop_splitRightCancelType_splitRequested() {
        startDrag()

        // Then user cancelled it, requesting split.
        handler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_RIGHT
        )

        // Verify the request went through split controller.
        verify(splitScreenController).requestEnterSplitSelect(
            any(),
            any(),
            eq(SPLIT_POSITION_BOTTOM_OR_RIGHT),
            any()
        )
    }

    @Test
    fun cancelDragToDesktop_startWasNotReady_animateCancel() {
        val task = createTask()
        // Simulate transition is started and is ready to animate.
        startDragToDesktopTransition(task, dragAnimator)

        // Then user cancelled before the transition was ready and animated.
        handler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )

        // No need to animate the cancel since the start animation couldn't even start.
        verifyZeroInteractions(dragAnimator)
    }

    @Test
    fun cancelDragToDesktop_transitionNotInProgress_dropCancel() {
        // Then cancel is called before the transition was started.
        handler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )

        // Verify cancel is dropped.
        verify(transitions, never()).startTransition(
                eq(TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP),
                any(),
                eq(handler)
        )
    }

    @Test
    fun finishDragToDesktop_transitionNotInProgress_dropFinish() {
        // Then finish is called before the transition was started.
        handler.finishDragToDesktopTransition(WindowContainerTransaction())

        // Verify finish is dropped.
        verify(transitions, never()).startTransition(
                eq(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP),
                any(),
                eq(handler)
        )
    }

    private fun startDrag() {
        val task = createTask()
        whenever(dragAnimator.position).thenReturn(PointF())
        // Simulate transition is started and is ready to animate.
        val transition = startDragToDesktopTransition(task, dragAnimator)
        handler.startAnimation(
            transition = transition,
            info =
            createTransitionInfo(
                type = TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP,
                draggedTask = task
            ),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {}
        )
    }

    private fun startDragToDesktopTransition(
        task: RunningTaskInfo,
        dragAnimator: MoveToDesktopAnimator
    ): IBinder {
        val token = mock<IBinder>()
        whenever(
                transitions.startTransition(
                    eq(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP),
                    any(),
                    eq(handler)
                )
            )
            .thenReturn(token)
        handler.startDragToDesktopTransition(task.taskId, dragAnimator)
        return token
    }

    private fun performEarlyCancel(cancelState: DragToDesktopTransitionHandler.CancelState) {
        val task = createTask()
        // Simulate transition is started and is ready to animate.
        val transition = startDragToDesktopTransition(task, dragAnimator)

        handler.cancelDragToDesktopTransition(cancelState)

        handler.startAnimation(
            transition = transition,
            info =
            createTransitionInfo(
                type = TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP,
                draggedTask = task
            ),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {}
        )

        // Don't even animate the "drag" since it was already cancelled.
        verify(dragAnimator, never()).startAnimation()
    }

    private fun createTask(
        @WindowingMode windowingMode: Int = WINDOWING_MODE_FULLSCREEN,
        isHome: Boolean = false,
    ): RunningTaskInfo {
        return TestRunningTaskInfoBuilder()
            .setActivityType(if (isHome) ACTIVITY_TYPE_HOME else ACTIVITY_TYPE_STANDARD)
            .setWindowingMode(windowingMode)
            .build()
            .also {
                whenever(splitScreenController.isTaskInSplitScreen(it.taskId))
                    .thenReturn(windowingMode == WINDOWING_MODE_MULTI_WINDOW)
            }
    }

    private fun createTransitionInfo(type: Int, draggedTask: RunningTaskInfo): TransitionInfo {
        return TransitionInfo(type, 0 /* flags */).apply {
            addChange( // Home.
                TransitionInfo.Change(mock(), mock()).apply {
                    parent = null
                    taskInfo =
                        TestRunningTaskInfoBuilder().setActivityType(ACTIVITY_TYPE_HOME).build()
                    flags = flags or FLAG_IS_WALLPAPER
                }
            )
            addChange( // Dragged Task.
                TransitionInfo.Change(mock(), mock()).apply {
                    parent = null
                    taskInfo = draggedTask
                }
            )
            addChange( // Wallpaper.
                TransitionInfo.Change(mock(), mock()).apply {
                    parent = null
                    taskInfo = null
                    flags = flags or FLAG_IS_WALLPAPER
                }
            )
        }
    }
}
