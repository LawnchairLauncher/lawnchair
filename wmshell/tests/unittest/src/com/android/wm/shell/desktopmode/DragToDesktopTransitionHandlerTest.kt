package com.android.wm.shell.desktopmode

import android.animation.AnimatorTestRule
import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WindowingMode
import android.graphics.PointF
import android.graphics.Rect
import android.os.IBinder
import android.os.SystemProperties
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_OPEN
import android.window.TransitionInfo
import android.window.TransitionInfo.FLAG_IS_WALLPAPER
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD
import com.android.internal.jank.Cuj.CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE
import com.android.internal.jank.InteractionJankMonitor
import com.android.window.flags.Flags
import com.android.window.flags.Flags.FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.bubbles.BubbleController
import com.android.wm.shell.bubbles.BubbleTransitions
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler.CancelState
import com.android.wm.shell.desktopmode.DragToDesktopTransitionHandler.Companion.DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT
import com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.MoveToDesktopAnimator
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import java.util.function.Supplier
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/** Tests of [DragToDesktopTransitionHandler]. */
@SmallTest
@RunWithLooper
@RunWith(AndroidTestingRunner::class)
class DragToDesktopTransitionHandlerTest : ShellTestCase() {
    @JvmField @Rule val mAnimatorTestRule = AnimatorTestRule(this)

    @Mock private lateinit var transitions: Transitions
    @Mock private lateinit var taskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer
    @Mock private lateinit var desksOrganizer: DesksOrganizer
    @Mock private lateinit var splitScreenController: SplitScreenController
    @Mock private lateinit var dragAnimator: MoveToDesktopAnimator
    @Mock private lateinit var mockInteractionJankMonitor: InteractionJankMonitor
    @Mock private lateinit var draggedTaskLeash: SurfaceControl
    @Mock private lateinit var homeTaskLeash: SurfaceControl
    @Mock private lateinit var wallpaperLeash: SurfaceControl
    @Mock private lateinit var desktopUserRepositories: DesktopUserRepositories
    @Mock private lateinit var bubbleController: BubbleController
    @Mock private lateinit var visualIndicator: DesktopModeVisualIndicator
    @Mock private lateinit var dragCancelCallback: Runnable
    @Mock
    private lateinit var dragToDesktopStateListener:
        DragToDesktopTransitionHandler.DragToDesktopStateListener
    private lateinit var desktopState: FakeDesktopState

    private val transactionSupplier = Supplier {
        val transaction = mock<SurfaceControl.Transaction>()
        whenever(transaction.setAlpha(any(), anyFloat())).thenReturn(transaction)
        whenever(transaction.setFrameTimeline(anyLong())).thenReturn(transaction)
        transaction
    }

    private lateinit var defaultHandler: DragToDesktopTransitionHandler
    private lateinit var springHandler: SpringDragToDesktopTransitionHandler

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        desktopState.canEnterDesktopMode = true
        defaultHandler =
            DefaultDragToDesktopTransitionHandler(
                    context,
                    transitions,
                    taskDisplayAreaOrganizer,
                    desksOrganizer,
                    desktopUserRepositories,
                    mockInteractionJankMonitor,
                    Optional.of(bubbleController),
                    transactionSupplier,
                    desktopState,
                )
                .apply {
                    setSplitScreenController(splitScreenController)
                    dragToDesktopStateListener =
                        this@DragToDesktopTransitionHandlerTest.dragToDesktopStateListener
                }
        springHandler =
            SpringDragToDesktopTransitionHandler(
                    context,
                    transitions,
                    taskDisplayAreaOrganizer,
                    desksOrganizer,
                    desktopUserRepositories,
                    mockInteractionJankMonitor,
                    Optional.of(bubbleController),
                    transactionSupplier,
                    desktopState,
                )
                .apply {
                    setSplitScreenController(splitScreenController)
                    dragToDesktopStateListener =
                        this@DragToDesktopTransitionHandlerTest.dragToDesktopStateListener
                }
        whenever(
                transitions.startTransition(
                    eq(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP),
                    /* wct= */ any(),
                    eq(defaultHandler),
                )
            )
            .thenReturn(mock<IBinder>())
    }

    @Test
    fun startDragToDesktop_animateDragWhenReady() {
        val task = createTask()
        // Simulate transition is started.
        val transition = startDragToDesktopTransition(defaultHandler, task, dragAnimator)

        // Now it's ready to animate.
        defaultHandler.startAnimation(
            transition = transition,
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP,
                    draggedTask = task,
                ),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {},
        )

        verify(dragAnimator).startAnimation()
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_startCancelTransition() {
        performEarlyCancel(
            defaultHandler,
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL,
        )
        verify(transitions)
            .startTransition(
                eq(TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP),
                any(),
                eq(defaultHandler),
            )
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_verifySplitLeftCancel() {
        performEarlyCancel(
            defaultHandler,
            DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_LEFT,
        )
        verify(splitScreenController)
            .requestEnterSplitSelect(
                /* taskInfo = */ any(),
                /* splitPosition = */ eq(SPLIT_POSITION_TOP_OR_LEFT),
                /* taskBounds = */ any(),
                /* startRecents = */ eq(false), // Home already running, so recents isn't needed.
                /* withRecentsWct = */ anyOrNull(),
            )
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_verifySplitRightCancel() {
        performEarlyCancel(
            defaultHandler,
            DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_RIGHT,
        )
        verify(splitScreenController)
            .requestEnterSplitSelect(
                /* taskInfo = */ any(),
                /* splitPosition = */ eq(SPLIT_POSITION_BOTTOM_OR_RIGHT),
                /* taskBounds = */ any(),
                /* startRecents = */ eq(false), // Home already running, so recents isn't needed.
                /* withRecentsWct = */ anyOrNull(),
            )
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_verifyBubbleLeftCancel() {
        performEarlyCancel(
            defaultHandler,
            DragToDesktopTransitionHandler.CancelState.CANCEL_BUBBLE_LEFT,
        )
        verify(bubbleController)
            .expandStackAndSelectBubble(
                any<RunningTaskInfo>(),
                argThat<BubbleTransitions.DragData> { isReleasedOnLeft },
            )
    }

    @Test
    fun startDragToDesktop_cancelledBeforeReady_verifyBubbleRightCancel() {
        performEarlyCancel(
            defaultHandler,
            DragToDesktopTransitionHandler.CancelState.CANCEL_BUBBLE_RIGHT,
        )
        verify(bubbleController)
            .expandStackAndSelectBubble(
                any<RunningTaskInfo>(),
                argThat<BubbleTransitions.DragData> { !isReleasedOnLeft },
            )
    }

    @Test
    fun startDragToDesktop_aborted_finishDropped() {
        val task = createTask()
        // Simulate transition is started.
        val transition = startDragToDesktopTransition(defaultHandler, task, dragAnimator)
        // But the transition was aborted.
        defaultHandler.onTransitionConsumed(transition, aborted = true, mock())

        // Attempt to finish the failed drag start.
        defaultHandler.finishDragToDesktopTransition(WindowContainerTransaction())

        // Should not be attempted and state should be reset.
        verify(transitions, never())
            .startTransition(eq(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP), any(), any())
        assertFalse(defaultHandler.inProgress)
    }

    @Test
    fun startDragToDesktop_aborted_cancelDropped() {
        val task = createTask()
        // Simulate transition is started.
        val transition = startDragToDesktopTransition(defaultHandler, task, dragAnimator)
        // But the transition was aborted.
        defaultHandler.onTransitionConsumed(transition, aborted = true, mock())

        // Attempt to finish the failed drag start.
        defaultHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )

        // Should not be attempted and state should be reset.
        assertFalse(defaultHandler.inProgress)
    }

    @Test
    fun startDragToDesktop_anotherTransitionInProgress_startDropped() {
        val task = createTask()

        // Simulate attempt to start two drag to desktop transitions.
        startDragToDesktopTransition(defaultHandler, task, dragAnimator)
        startDragToDesktopTransition(defaultHandler, task, dragAnimator)

        // Verify transition only started once.
        verify(transitions, times(1))
            .startTransition(
                eq(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP),
                any(),
                eq(defaultHandler),
            )
    }

    @Test
    fun isHomeChange_withoutTaskInfo_returnsFalse() {
        val change =
            TransitionInfo.Change(mock(), homeTaskLeash).apply {
                parent = null
                taskInfo = null
            }

        assertFalse(defaultHandler.isHomeChange(change))
        assertFalse(springHandler.isHomeChange(change))
    }

    @Test
    fun isHomeChange_withStandardActivityTaskInfo_returnsFalse() {
        val change =
            TransitionInfo.Change(mock(), homeTaskLeash).apply {
                parent = null
                taskInfo =
                    TestRunningTaskInfoBuilder().setActivityType(ACTIVITY_TYPE_STANDARD).build()
            }

        assertFalse(defaultHandler.isHomeChange(change))
        assertFalse(springHandler.isHomeChange(change))
    }

    @Test
    fun isHomeChange_withHomeActivityTaskInfo_returnsTrue() {
        val change =
            TransitionInfo.Change(mock(), homeTaskLeash).apply {
                parent = null
                taskInfo = TestRunningTaskInfoBuilder().setActivityType(ACTIVITY_TYPE_HOME).build()
            }

        assertTrue(defaultHandler.isHomeChange(change))
        assertTrue(springHandler.isHomeChange(change))
    }

    @Test
    fun isHomeChange_withSingleTranslucentHomeActivityTaskInfo_returnsFalse() {
        val change =
            TransitionInfo.Change(mock(), homeTaskLeash).apply {
                parent = null
                taskInfo =
                    TestRunningTaskInfoBuilder()
                        .setActivityType(ACTIVITY_TYPE_HOME)
                        .setTopActivityTransparent(true)
                        .setNumActivities(1)
                        .build()
            }

        assertFalse(defaultHandler.isHomeChange(change))
        assertFalse(springHandler.isHomeChange(change))
    }

    @Test
    fun cancelDragToDesktop_startWasReady_cancel_merged() {
        val startToken = startDrag(defaultHandler)

        // Then user cancelled after it had already started.
        val cancelToken =
            cancelDragToDesktopTransition(
                defaultHandler,
                DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL,
            )
        defaultHandler.mergeAnimation(
            cancelToken,
            TransitionInfo(TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP, 0),
            mock<SurfaceControl.Transaction>(),
            mock<SurfaceControl.Transaction>(),
            startToken,
            mock<Transitions.TransitionFinishCallback>(),
        )

        // Cancel animation should run since it had already started.
        verify(dragAnimator).cancelAnimator()
        assertFalse("Drag should not be in progress after cancelling", defaultHandler.inProgress)
    }

    @Test
    fun cancelDragToDesktop_startWasReady_cancel_aborted() {
        val startToken = startDrag(defaultHandler)

        // Then user cancelled after it had already started.
        val cancelToken =
            cancelDragToDesktopTransition(
                defaultHandler,
                DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL,
            )
        defaultHandler.onTransitionConsumed(cancelToken, aborted = true, null)

        // Cancel animation should run since it had already started.
        verify(dragAnimator).cancelAnimator()
        assertFalse("Drag should not be in progress after cancelling", defaultHandler.inProgress)
    }

    @Test
    fun cancelDragToDesktop_splitLeftCancelType_splitRequested() {
        startDrag(defaultHandler)

        // Then user cancelled it, requesting split.
        defaultHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_LEFT
        )

        // Verify the request went through split controller.
        verify(splitScreenController)
            .requestEnterSplitSelect(
                /* taskInfo = */ any(),
                /* splitPosition = */ eq(SPLIT_POSITION_TOP_OR_LEFT),
                /* taskBounds = */ any(),
                /* startRecents = */ eq(false), // Home already running, so recents isn't needed.
                /* withRecentsWct = */ anyOrNull(),
            )
    }

    @Test
    fun cancelDragToDesktop_splitRightCancelType_splitRequested() {
        startDrag(defaultHandler)

        // Then user cancelled it, requesting split.
        defaultHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.CANCEL_SPLIT_RIGHT
        )

        // Verify the request went through split controller.
        verify(splitScreenController)
            .requestEnterSplitSelect(
                /* taskInfo = */ any(),
                /* splitPosition = */ eq(SPLIT_POSITION_BOTTOM_OR_RIGHT),
                /* taskBounds = */ any(),
                /* startRecents = */ eq(false), // Home already running, so recents isn't needed.
                /* withRecentsWct = */ anyOrNull(),
            )
    }

    @Test
    fun cancelDragToDesktop_bubbleLeftCancelType_bubbleRequested() {
        startDrag(defaultHandler)

        // Then user cancelled it, requesting bubble.
        defaultHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.CANCEL_BUBBLE_LEFT
        )

        // Verify the request went through bubble controller.
        verify(bubbleController)
            .expandStackAndSelectBubble(
                any<RunningTaskInfo>(),
                argThat<BubbleTransitions.DragData> { isReleasedOnLeft },
            )
    }

    @Test
    fun cancelDragToDesktop_bubbleRightCancelType_bubbleRequested() {
        startDrag(defaultHandler)

        // Then user cancelled it, requesting bubble.
        defaultHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.CANCEL_BUBBLE_RIGHT
        )

        // Verify the request went through bubble controller.
        verify(bubbleController)
            .expandStackAndSelectBubble(
                any<RunningTaskInfo>(),
                argThat<BubbleTransitions.DragData> { !isReleasedOnLeft },
            )
    }

    @Test
    fun cancelDragToDesktop_startWasNotReady_animateCancel() {
        val task = createTask()
        // Simulate transition is started and is ready to animate.
        startDragToDesktopTransition(defaultHandler, task, dragAnimator)

        // Then user cancelled before the transition was ready and animated.
        defaultHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )

        // No need to animate the cancel since the start animation couldn't even start.
        verifyNoMoreInteractions(dragAnimator)
    }

    @Test
    fun cancelDragToDesktop_transitionNotInProgress_dropCancel() {
        // Then cancel is called before the transition was started.
        defaultHandler.cancelDragToDesktopTransition(
            DragToDesktopTransitionHandler.CancelState.STANDARD_CANCEL
        )

        // Verify cancel is dropped.
        verify(transitions, never())
            .startTransition(
                eq(TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP),
                any(),
                eq(defaultHandler),
            )
    }

    @Test
    fun finishDragToDesktop_transitionNotInProgress_dropFinish() {
        // Then finish is called before the transition was started.
        defaultHandler.finishDragToDesktopTransition(WindowContainerTransaction())

        // Verify finish is dropped.
        verify(transitions, never())
            .startTransition(
                eq(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP),
                any(),
                eq(defaultHandler),
            )
    }

    @Test
    fun mergeAnimation_otherTransition_doesNotMerge() {
        val mergedStartTransaction = mock<SurfaceControl.Transaction>()
        val mergedFinishTransaction = mock<SurfaceControl.Transaction>()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()

        startDrag(defaultHandler, task)
        defaultHandler.mergeAnimation(
            transition = mock<IBinder>(),
            info = createTransitionInfo(type = TRANSIT_OPEN, draggedTask = task),
            startT = mergedStartTransaction,
            finishT = mergedFinishTransaction,
            mergeTarget = mock<IBinder>(),
            finishCallback = finishCallback,
        )

        // Should NOT have any transaction changes
        verifyNoMoreInteractions(mergedStartTransaction)
        // Should NOT merge animation
        verify(finishCallback, never()).onTransitionFinished(any())
    }

    @Test
    fun mergeAnimation_endTransition_mergesAnimation() {
        val playingFinishTransaction = mock<SurfaceControl.Transaction>()
        val mergedStartTransaction = mock<SurfaceControl.Transaction>()
        val mergedFinishTransaction = mock<SurfaceControl.Transaction>()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()
        val startTransition =
            startDrag(defaultHandler, task, finishTransaction = playingFinishTransaction)
        defaultHandler.onTaskResizeAnimationListener = mock()

        defaultHandler.mergeAnimation(
            transition = mock<IBinder>(),
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
                    draggedTask = task,
                ),
            startT = mergedStartTransaction,
            finishT = mergedFinishTransaction,
            mergeTarget = startTransition,
            finishCallback = finishCallback,
        )

        // Should show dragged task layer in start and finish transaction
        verify(mergedStartTransaction).show(draggedTaskLeash)
        verify(playingFinishTransaction).show(draggedTaskLeash)
        // Should update the dragged task layer
        verify(mergedStartTransaction).setLayer(eq(draggedTaskLeash), anyInt())
        // Should merge animation
        verify(finishCallback).onTransitionFinished(null)
    }

    @Test
    fun mergeAnimation_endTransition_hasDeskChange_doesNotMoveBehindDraggedTask() {
        val playingFinishTransaction = mock<SurfaceControl.Transaction>()
        val mergedStartTransaction = mock<SurfaceControl.Transaction>()
        val mergedFinishTransaction = mock<SurfaceControl.Transaction>()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val deskChange = createDeskChange()
        val task = createTask()
        val startTransition =
            startDrag(defaultHandler, task, finishTransaction = playingFinishTransaction)
        defaultHandler.onTaskResizeAnimationListener = mock()
        whenever(desksOrganizer.isDeskChange(deskChange)).thenReturn(true)

        defaultHandler.mergeAnimation(
            transition = mock<IBinder>(),
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
                    draggedTask = task,
                    deskChange = deskChange,
                ),
            startT = mergedStartTransaction,
            finishT = mergedFinishTransaction,
            mergeTarget = startTransition,
            finishCallback = finishCallback,
        )

        // Don't move the desk leash back, or it will take the dragged task with it.
        verify(mergedStartTransaction, never())
            .setRelativeLayer(eq(deskChange.leash), eq(draggedTaskLeash), anyInt())
        verify(playingFinishTransaction, never())
            .setRelativeLayer(eq(deskChange.leash), eq(draggedTaskLeash), anyInt())
    }

    @Test
    fun mergeAnimation_endTransition_springHandler_hidesHome() {
        whenever(dragAnimator.computeCurrentVelocity()).thenReturn(PointF())
        val playingFinishTransaction = mock<SurfaceControl.Transaction>()
        val mergedStartTransaction = mock<SurfaceControl.Transaction>()
        val mergedFinishTransaction = mock<SurfaceControl.Transaction>()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()
        val startTransition =
            startDrag(springHandler, task, finishTransaction = playingFinishTransaction)
        springHandler.onTaskResizeAnimationListener = mock()

        springHandler.mergeAnimation(
            transition = mock<IBinder>(),
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
                    draggedTask = task,
                ),
            startT = mergedStartTransaction,
            finishT = mergedFinishTransaction,
            mergeTarget = startTransition,
            finishCallback = finishCallback,
        )

        // Should show dragged task layer in start and finish transaction
        verify(mergedStartTransaction).show(draggedTaskLeash)
        verify(playingFinishTransaction).show(draggedTaskLeash)
        // Should update the dragged task layer
        verify(mergedStartTransaction).setLayer(eq(draggedTaskLeash), anyInt())
        // Should hide home task leash in finish transaction
        verify(playingFinishTransaction).hide(homeTaskLeash)
        // Should merge animation
        verify(finishCallback).onTransitionFinished(null)
    }

    @Test
    fun mergeAnimation_endTransition_springHandler_noStartHomeChange_doesntCrash() {
        whenever(dragAnimator.computeCurrentVelocity()).thenReturn(PointF())
        val playingFinishTransaction = mock<SurfaceControl.Transaction>()
        val mergedStartTransaction = mock<SurfaceControl.Transaction>()
        val mergedFinishTransaction = mock<SurfaceControl.Transaction>()
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()
        val startTransition =
            startDrag(
                springHandler,
                task,
                finishTransaction = playingFinishTransaction,
                homeChange = null,
            )
        springHandler.onTaskResizeAnimationListener = mock()

        springHandler.mergeAnimation(
            transition = mock<IBinder>(),
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
                    draggedTask = task,
                ),
            startT = mergedStartTransaction,
            finishT = mergedFinishTransaction,
            mergeTarget = startTransition,
            finishCallback = finishCallback,
        )

        // Should show dragged task layer in start and finish transaction
        verify(mergedStartTransaction).show(draggedTaskLeash)
        verify(playingFinishTransaction).show(draggedTaskLeash)
        // Should update the dragged task layer
        verify(mergedStartTransaction).setLayer(eq(draggedTaskLeash), anyInt())
        // Should merge animation
        verify(finishCallback).onTransitionFinished(null)
    }

    @Test
    fun propertyValue_returnsSystemPropertyValue() {
        val mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(SystemProperties::class.java)
                .startMocking()

        val name = "property_name"
        val value = 10f

        whenever(SystemProperties.getInt(eq(systemPropertiesKey(name)), anyInt()))
            .thenReturn(value.toInt())

        assertEquals(
            "Expects to return system properties stored value",
            /* expected= */ value,
            /* actual= */ SpringDragToDesktopTransitionHandler.propertyValue(name),
        )

        mockitoSession.finishMocking()
    }

    @Test
    fun propertyValue_withScale_returnsScaledSystemPropertyValue() {
        val mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(SystemProperties::class.java)
                .startMocking()

        val name = "property_name"
        val value = 10f
        val scale = 100f

        whenever(SystemProperties.getInt(eq(systemPropertiesKey(name)), anyInt()))
            .thenReturn(value.toInt())

        assertEquals(
            "Expects to return scaled system properties stored value",
            /* expected= */ value / scale,
            /* actual= */ SpringDragToDesktopTransitionHandler.propertyValue(name, scale = scale),
        )

        mockitoSession.finishMocking()
    }

    @Test
    fun propertyValue_notSet_returnsDefaultValue() {
        val mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(SystemProperties::class.java)
                .startMocking()

        val name = "property_name"
        val defaultValue = 50f

        whenever(SystemProperties.getInt(eq(systemPropertiesKey(name)), eq(defaultValue.toInt())))
            .thenReturn(defaultValue.toInt())

        assertEquals(
            "Expects to return the default value",
            /* expected= */ defaultValue,
            /* actual= */ SpringDragToDesktopTransitionHandler.propertyValue(
                name,
                default = defaultValue,
            ),
        )

        mockitoSession.finishMocking()
    }

    @Test
    fun propertyValue_withScaleNotSet_returnsDefaultValue() {
        val mockitoSession =
            ExtendedMockito.mockitoSession()
                .strictness(Strictness.LENIENT)
                .mockStatic(SystemProperties::class.java)
                .startMocking()

        val name = "property_name"
        val defaultValue = 0.5f
        val scale = 100f
        // Default value is multiplied when provided as a default value for [SystemProperties]
        val scaledDefault = (defaultValue * scale).toInt()

        whenever(SystemProperties.getInt(eq(systemPropertiesKey(name)), eq(scaledDefault)))
            .thenReturn(scaledDefault)

        assertEquals(
            "Expects to return the default value",
            /* expected= */ defaultValue,
            /* actual= */ SpringDragToDesktopTransitionHandler.propertyValue(
                name,
                default = defaultValue,
                scale = scale,
            ),
        )

        mockitoSession.finishMocking()
    }

    @Test
    fun startDragToDesktop_aborted_logsDragHoldCancelled() {
        val transition = startDragToDesktopTransition(defaultHandler, createTask(), dragAnimator)

        defaultHandler.onTransitionConsumed(transition, aborted = true, mock())

        verify(mockInteractionJankMonitor).cancel(eq(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD))
        verify(mockInteractionJankMonitor, times(0))
            .cancel(eq(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE))
    }

    @Test
    fun mergeEndDragToDesktop_aborted_logsDragReleaseCancelled() {
        val task = createTask()
        val startTransition = startDrag(defaultHandler, task)
        val endTransition = mock<IBinder>()
        defaultHandler.onTaskResizeAnimationListener = mock()
        mergeAnimation(
            transition = endTransition,
            type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
            task = task,
            mergeTarget = startTransition,
        )

        defaultHandler.onTransitionConsumed(endTransition, aborted = true, mock())

        verify(mockInteractionJankMonitor)
            .cancel(eq(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_RELEASE))
        verify(mockInteractionJankMonitor, times(0))
            .cancel(eq(CUJ_DESKTOP_MODE_ENTER_APP_HANDLE_DRAG_HOLD))
    }

    @Test
    @DisableFlags(FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX)
    fun mergeOtherTransition_flagDisabled_cancelAndEndNotYetRequested_doesNotInterruptStartDrag() {
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()
        defaultHandler.onTaskResizeAnimationListener = mock()
        val startTransition = startDrag(defaultHandler, task, finishCallback = finishCallback)

        mergeInterruptingTransition(mergeTarget = startTransition)

        verify(finishCallback, never()).onTransitionFinished(anyOrNull())
        verify(dragAnimator, never()).cancelAnimator()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX)
    fun mergeOtherTransition_cancelAndEndNotYetRequested_interruptsStartDrag() {
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()
        defaultHandler.onTaskResizeAnimationListener = mock()
        val startTransition = startDrag(defaultHandler, task, finishCallback = finishCallback)

        mergeInterruptingTransition(mergeTarget = startTransition)

        verify(dragAnimator).cancelAnimator()
        verify(dragCancelCallback).run()
        verify(dragToDesktopStateListener).onTransitionInterrupted()
        assertThat(defaultHandler.inProgress).isTrue()
        // Doesn't finish start transition yet
        verify(finishCallback, never()).onTransitionFinished(/* wct= */ anyOrNull())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX)
    fun mergeOtherTransition_cancelAndEndNotYetRequested_finishesStartAfterAnimation() {
        val finishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()
        defaultHandler.onTaskResizeAnimationListener = mock()
        val startTransition = startDrag(defaultHandler, task, finishCallback = finishCallback)

        mergeInterruptingTransition(mergeTarget = startTransition)
        mAnimatorTestRule.advanceTimeBy(DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS)

        verify(finishCallback).onTransitionFinished(/* wct= */ anyOrNull())
        assertThat(defaultHandler.inProgress).isFalse()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX)
    fun mergeOtherTransition_endDragAlreadyMerged_doesNotInterruptStartDrag() {
        val startDragFinishCallback = mock<Transitions.TransitionFinishCallback>()
        val task = createTask()
        val startTransition =
            startDrag(defaultHandler, task, finishCallback = startDragFinishCallback)
        defaultHandler.onTaskResizeAnimationListener = mock()
        mergeAnimation(
            type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
            task = task,
            mergeTarget = startTransition,
        )

        mergeInterruptingTransition(mergeTarget = startTransition)

        verify(startDragFinishCallback, never()).onTransitionFinished(anyOrNull())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX)
    fun startEndAnimation_otherTransitionInterruptedStartAfterEndRequest_finishImmediately() {
        val task1 = createTask()
        val startTransition = startDrag(defaultHandler, task1)
        val endTransition =
            defaultHandler.finishDragToDesktopTransition(WindowContainerTransaction())
        val startTransaction = mock<SurfaceControl.Transaction>()
        val endDragFinishCallback = mock<Transitions.TransitionFinishCallback>()
        defaultHandler.onTaskResizeAnimationListener = mock()
        mergeInterruptingTransition(mergeTarget = startTransition)

        val didAnimate =
            defaultHandler.startAnimation(
                transition = requireNotNull(endTransition),
                info =
                    createTransitionInfo(
                        type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
                        draggedTask = task1,
                    ),
                startTransaction = startTransaction,
                finishTransaction = mock(),
                finishCallback = endDragFinishCallback,
            )

        assertThat(didAnimate).isTrue()
        verify(startTransaction).apply()
        verify(endDragFinishCallback).onTransitionFinished(anyOrNull())
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX)
    fun startDrag_otherTransitionInterruptedStartAfterEndRequested_animatesDragWhenReady() {
        val task1 = createTask()
        val startTransition = startDrag(defaultHandler, task1)
        verify(dragAnimator).startAnimation()
        val endTransition =
            defaultHandler.finishDragToDesktopTransition(WindowContainerTransaction())
        defaultHandler.onTaskResizeAnimationListener = mock()
        mergeInterruptingTransition(mergeTarget = startTransition)
        defaultHandler.startAnimation(
            transition = requireNotNull(endTransition),
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP,
                    draggedTask = task1,
                ),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = mock(),
        )

        startDrag(defaultHandler, createTask())

        verify(dragAnimator, times(2)).startAnimation()
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX)
    fun startCancelAnimation_otherTransitionInterruptingAfterCancelRequest_finishImmediately() {
        val task1 = createTask()
        val startTransition = startDrag(defaultHandler, task1)
        val cancelTransition =
            cancelDragToDesktopTransition(defaultHandler, CancelState.STANDARD_CANCEL)
        mergeInterruptingTransition(mergeTarget = startTransition)
        val cancelFinishCallback = mock<Transitions.TransitionFinishCallback>()
        val startTransaction = mock<SurfaceControl.Transaction>()

        val didAnimate =
            defaultHandler.startAnimation(
                transition = requireNotNull(cancelTransition),
                info =
                    createTransitionInfo(
                        type = TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP,
                        draggedTask = task1,
                    ),
                startTransaction = startTransaction,
                finishTransaction = mock(),
                finishCallback = cancelFinishCallback,
            )

        assertThat(didAnimate).isTrue()
        verify(startTransaction).apply()
        verify(cancelFinishCallback).onTransitionFinished(/* wct= */ anyOrNull())
    }

    private fun mergeInterruptingTransition(mergeTarget: IBinder) {
        defaultHandler.mergeAnimation(
            transition = mock<IBinder>(),
            info = createTransitionInfo(type = TRANSIT_OPEN, draggedTask = createTask()),
            startT = mock(),
            finishT = mock(),
            mergeTarget = mergeTarget,
            finishCallback = mock(),
        )
    }

    private fun mergeAnimation(
        transition: IBinder = mock(),
        type: Int,
        mergeTarget: IBinder,
        task: RunningTaskInfo,
    ) {
        defaultHandler.mergeAnimation(
            transition = transition,
            info = createTransitionInfo(type = type, draggedTask = task),
            startT = mock(),
            finishT = mock(),
            mergeTarget = mergeTarget,
            finishCallback = mock(),
        )
    }

    @Test
    fun getAnimationFraction_returnsFraction() {
        val fraction =
            SpringDragToDesktopTransitionHandler.getAnimationFraction(
                startBounds = Rect(0, 0, 0, 0),
                endBounds = Rect(0, 0, 10, 10),
                animBounds = Rect(0, 0, 5, 5),
            )
        assertThat(fraction).isWithin(TOLERANCE).of(0.5f)
    }

    @Test
    fun getAnimationFraction_animBoundsSameAsEnd_returnsOne() {
        val fraction =
            SpringDragToDesktopTransitionHandler.getAnimationFraction(
                startBounds = Rect(0, 0, 0, 0),
                endBounds = Rect(0, 0, 10, 10),
                animBounds = Rect(0, 0, 10, 10),
            )
        assertThat(fraction).isWithin(TOLERANCE).of(1f)
    }

    @Test
    fun getAnimationFraction_startAndEndBoundsSameWidth_usesHeight() {
        val fraction =
            SpringDragToDesktopTransitionHandler.getAnimationFraction(
                startBounds = Rect(0, 0, 10, 10),
                endBounds = Rect(0, 0, 10, 30),
                animBounds = Rect(0, 0, 10, 25),
            )
        assertThat(fraction).isWithin(TOLERANCE).of(0.75f)
    }

    @Test
    fun getAnimationFraction_startAndEndBoundsSame_returnsZero() {
        val fraction =
            SpringDragToDesktopTransitionHandler.getAnimationFraction(
                startBounds = Rect(0, 0, 10, 10),
                endBounds = Rect(0, 0, 10, 10),
                animBounds = Rect(0, 0, 10, 25),
            )
        assertThat(fraction).isWithin(TOLERANCE).of(0f)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX)
    fun startDrag_indicatorFlagEnabled_attachesIndicatorToTransitionRoot() {
        val task = createTask()
        val rootLeash = mock<SurfaceControl>()
        val startTransaction = mock<SurfaceControl.Transaction>()
        startDrag(
            defaultHandler,
            task,
            startTransaction = startTransaction,
            transitionRootLeash = rootLeash,
        )

        verify(visualIndicator).reparentLeash(startTransaction, rootLeash)
        verify(visualIndicator).fadeInIndicator()
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_VISUAL_INDICATOR_IN_TRANSITION_BUGFIX)
    fun startDrag_indicatorFlagDisabled_doesNotAttachIndicatorToTransitionRoot() {
        val task = createTask()
        val rootLeash = mock<SurfaceControl>()
        val startTransaction = mock<SurfaceControl.Transaction>()
        startDrag(
            defaultHandler,
            task,
            startTransaction = startTransaction,
            transitionRootLeash = rootLeash,
        )

        verify(visualIndicator, never()).reparentLeash(any(), any())
        verify(visualIndicator, never()).fadeInIndicator()
    }

    @Test
    fun startDrag_hasDesktop_layerOrder_taskOnWallpaperOnHome() {
        desktopState.overrideDesktopModeSupportPerDisplay[Display.DEFAULT_DISPLAY] = true
        val task = createTask()
        val rootLeash = mock<SurfaceControl>()
        val startTransaction = mock<SurfaceControl.Transaction>()
        startDrag(
            defaultHandler,
            task,
            startTransaction = startTransaction,
            transitionRootLeash = rootLeash,
        )

        val draggedTaskLayer =
            argumentCaptor<Int> {
                    verify(startTransaction).setLayer(eq(draggedTaskLeash), capture())
                }
                .firstValue
        val wallpaperLayer =
            argumentCaptor<Int> { verify(startTransaction).setLayer(eq(wallpaperLeash), capture()) }
                .firstValue
        val homeLayer =
            argumentCaptor<Int> { verify(startTransaction).setLayer(eq(homeTaskLeash), capture()) }
                .firstValue

        // dragged task -> wallpaper -> home
        assertThat(draggedTaskLayer).isGreaterThan(wallpaperLayer)
        assertThat(wallpaperLayer).isGreaterThan(homeLayer)
    }

    @Test
    @EnableFlags(
        com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN,
        com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE,
    )
    fun startDrag_noDesktop_layerOrder_taskOnHomeOnWallpaper() {
        mContext.orCreateTestableResources.addOverride(
            com.android.internal.R.bool.config_canInternalDisplayHostDesktops,
            false,
        )
        desktopState.overrideDesktopModeSupportPerDisplay[Display.DEFAULT_DISPLAY] = false
        val task = createTask()
        val rootLeash = mock<SurfaceControl>()
        val startTransaction = mock<SurfaceControl.Transaction>()
        startDrag(
            defaultHandler,
            task,
            startTransaction = startTransaction,
            transitionRootLeash = rootLeash,
        )

        val draggedTaskLayer =
            argumentCaptor<Int> {
                    verify(startTransaction).setLayer(eq(draggedTaskLeash), capture())
                }
                .firstValue
        val homeLayer =
            argumentCaptor<Int> { verify(startTransaction).setLayer(eq(homeTaskLeash), capture()) }
                .firstValue
        val wallpaperLayer =
            argumentCaptor<Int> { verify(startTransaction).setLayer(eq(wallpaperLeash), capture()) }
                .firstValue

        // dragged task -> home -> wallpaper
        assertThat(draggedTaskLayer).isGreaterThan(homeLayer)
        assertThat(homeLayer).isGreaterThan(wallpaperLayer)
    }

    private fun startDrag(
        handler: DragToDesktopTransitionHandler,
        task: RunningTaskInfo = createTask(),
        startTransaction: SurfaceControl.Transaction = mock(),
        finishTransaction: SurfaceControl.Transaction = mock(),
        homeChange: TransitionInfo.Change? = createHomeChange(),
        transitionRootLeash: SurfaceControl = mock(),
        finishCallback: Transitions.TransitionFinishCallback = mock(),
    ): IBinder {
        whenever(dragAnimator.position).thenReturn(PointF())
        // Simulate transition is started and is ready to animate.
        val transition = startDragToDesktopTransition(handler, task, dragAnimator)
        handler.startAnimation(
            transition = transition,
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP,
                    draggedTask = task,
                    homeChange = homeChange,
                    rootLeash = transitionRootLeash,
                ),
            startTransaction = startTransaction,
            finishTransaction = finishTransaction,
            finishCallback = finishCallback,
        )
        return transition
    }

    private fun startDragToDesktopTransition(
        handler: DragToDesktopTransitionHandler,
        task: RunningTaskInfo,
        dragAnimator: MoveToDesktopAnimator,
    ): IBinder {
        val token = mock<IBinder>()
        whenever(
                transitions.startTransition(
                    eq(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP),
                    any(),
                    eq(handler),
                )
            )
            .thenReturn(token)
        handler.startDragToDesktopTransition(
            task,
            dragAnimator,
            visualIndicator,
            dragCancelCallback,
        )
        return token
    }

    private fun cancelDragToDesktopTransition(
        handler: DragToDesktopTransitionHandler,
        cancelState: DragToDesktopTransitionHandler.CancelState,
    ): IBinder {
        val token = mock<IBinder>()
        whenever(
                transitions.startTransition(
                    eq(TRANSIT_DESKTOP_MODE_CANCEL_DRAG_TO_DESKTOP),
                    any(),
                    eq(handler),
                )
            )
            .thenReturn(token)
        handler.cancelDragToDesktopTransition(cancelState)
        mAnimatorTestRule.advanceTimeBy(DRAG_TO_DESKTOP_FINISH_ANIM_DURATION_MS)
        return token
    }

    private fun performEarlyCancel(
        handler: DragToDesktopTransitionHandler,
        cancelState: DragToDesktopTransitionHandler.CancelState,
    ) {
        val task = createTask()
        // Simulate transition is started and is ready to animate.
        val transition = startDragToDesktopTransition(handler, task, dragAnimator)

        handler.cancelDragToDesktopTransition(cancelState)

        handler.startAnimation(
            transition = transition,
            info =
                createTransitionInfo(
                    type = TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP,
                    draggedTask = task,
                ),
            startTransaction = mock(),
            finishTransaction = mock(),
            finishCallback = {},
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
            .setUserId(mContext.userId)
            .build()
            .also {
                whenever(splitScreenController.isTaskInSplitScreen(it.taskId))
                    .thenReturn(windowingMode == WINDOWING_MODE_MULTI_WINDOW)
            }
    }

    private fun createTransitionInfo(
        type: Int,
        draggedTask: RunningTaskInfo,
        homeChange: TransitionInfo.Change? = createHomeChange(),
        rootLeash: SurfaceControl = mock(),
        deskChange: TransitionInfo.Change? = null,
    ) =
        TransitionInfo(type, /* flags= */ 0).apply {
            homeChange?.let { addChange(it) }
            addChange( // Dragged Task.
                TransitionInfo.Change(mock(), draggedTaskLeash).apply {
                    parent = null
                    taskInfo = draggedTask
                }
            )
            deskChange?.let { addChange(it) }
            addChange( // Wallpaper.
                TransitionInfo.Change(mock(), wallpaperLeash).apply {
                    parent = null
                    taskInfo = null
                    flags = flags or FLAG_IS_WALLPAPER
                }
            )
            addRootLeash(draggedTask.displayId, rootLeash, /* offsetLeft= */ 0, /* offsetTop= */ 0)
        }

    private fun createHomeChange() =
        TransitionInfo.Change(mock(), homeTaskLeash).apply {
            parent = null
            taskInfo = TestRunningTaskInfoBuilder().setActivityType(ACTIVITY_TYPE_HOME).build()
            flags = flags or FLAG_IS_WALLPAPER
        }

    private fun createDeskChange() =
        TransitionInfo.Change(mock(), mock()).apply {
            parent = null
            taskInfo = TestRunningTaskInfoBuilder().build()
        }

    private fun systemPropertiesKey(name: String) =
        "${SpringDragToDesktopTransitionHandler.SYSTEM_PROPERTIES_GROUP}.$name"

    private companion object {
        private const val TOLERANCE = 1e-5f
    }
}
