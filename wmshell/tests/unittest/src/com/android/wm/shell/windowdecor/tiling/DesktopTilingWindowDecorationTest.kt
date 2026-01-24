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
package com.android.wm.shell.windowdecor.tiling

import android.app.ActivityManager
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.os.IBinder
import android.testing.AndroidTestingRunner
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_PIP
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerTransaction
import androidx.test.filters.SmallTest
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.common.SyncTransactionQueue
import com.android.wm.shell.desktopmode.DesktopModeEventLogger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopRepository
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createPinnedTask
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.ReturnToDragStartAnimator
import com.android.wm.shell.desktopmode.ToggleResizeDesktopTaskTransitionHandler
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_START_RECENTS_TRANSITION
import com.android.wm.shell.windowdecor.DesktopModeWindowDecoration
import com.android.wm.shell.windowdecor.DragResizeWindowGeometry
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainCoroutineDispatcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.capture
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopTilingWindowDecorationTest : ShellTestCase() {

    private val context: Context = mock()

    private val syncQueue: SyncTransactionQueue = mock()

    private val displayController: DisplayController = mock()
    private val displayId: Int = 0
    private val deskId: Int = 0
    private val rootTdaOrganizer: RootTaskDisplayAreaOrganizer = mock()

    private val transitions: Transitions = mock()

    private val shellTaskOrganizer: ShellTaskOrganizer = mock()

    private val toggleResizeDesktopTaskTransitionHandler: ToggleResizeDesktopTaskTransitionHandler =
        mock()

    private val returnToDragStartAnimator: ReturnToDragStartAnimator = mock()

    private val desktopWindowDecoration: DesktopModeWindowDecoration = mock()

    private val displayLayout: DisplayLayout = mock()

    private val resources: Resources = mock()
    private val surfaceControlMock: SurfaceControl = mock()
    private val transaction: SurfaceControl.Transaction = mock()
    private val tiledTaskHelper: DesktopTilingWindowDecoration.AppResizingHelper = mock()
    private val transition: IBinder = mock()
    private val info: TransitionInfo = mock()
    private val finishCallback: Transitions.TransitionFinishCallback = mock()
    private val userRepositories: DesktopUserRepositories = mock()
    private val desktopModeEventLogger: DesktopModeEventLogger = mock()
    private val desktopTilingDividerWindowManager: DesktopTilingDividerWindowManager = mock()
    private val motionEvent: MotionEvent = mock()
    private val desktopRepository: DesktopRepository = mock()
    private val mainDispatcher: MainCoroutineDispatcher = mock()
    private val bgScope: CoroutineScope = mock()
    private val taskResourceLoader: WindowDecorTaskResourceLoader = mock()
    private val focusTransitionObserver: FocusTransitionObserver = mock()
    private val mainExecutor: ShellExecutor = mock()
    private lateinit var tilingDecoration: DesktopTilingWindowDecoration
    private lateinit var desktopState: FakeDesktopState

    private val split_divider_width = 10

    @Captor private lateinit var wctCaptor: ArgumentCaptor<WindowContainerTransaction>
    @Captor private lateinit var callbackCaptor: ArgumentCaptor<(() -> Unit)>

    @Before
    fun setUp() {
        desktopState = FakeDesktopState()
        desktopState.canEnterDesktopMode = true
        tilingDecoration =
            DesktopTilingWindowDecoration(
                context,
                mainDispatcher,
                bgScope,
                syncQueue,
                displayController,
                taskResourceLoader,
                displayId,
                deskId,
                rootTdaOrganizer,
                transitions,
                shellTaskOrganizer,
                toggleResizeDesktopTaskTransitionHandler,
                returnToDragStartAnimator,
                userRepositories,
                desktopModeEventLogger,
                focusTransitionObserver,
                mainExecutor,
                desktopState,
            )
        whenever(context.createContextAsUser(any(), any())).thenReturn(context)
        whenever(userRepositories.current).thenReturn(desktopRepository)
    }

    @Test
    fun taskTiled_toCorrectBounds_leftTile() {
        val task1 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(displayController.getDisplayContext(any())).thenReturn(context)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        verify(toggleResizeDesktopTaskTransitionHandler)
            .startTransition(capture(wctCaptor), any(), any())
        for (change in wctCaptor.value.changes) {
            val bounds = change.value.configuration.windowConfiguration.bounds
            val leftBounds = getLeftTaskBounds()
            assertRectEqual(bounds, leftBounds)
        }
    }

    @Test
    fun taskTiled_toCorrectBounds_rightTile() {
        // Setup
        val task1 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(displayController.getDisplayContext(any())).thenReturn(context)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        verify(toggleResizeDesktopTaskTransitionHandler)
            .startTransition(capture(wctCaptor), any(), any())
        for (change in wctCaptor.value.changes) {
            val bounds = change.value.configuration.windowConfiguration.bounds
            val leftBounds = getRightTaskBounds()
            assertRectEqual(bounds, leftBounds)
        }
    }

    @Test
    fun taskTiled_notAnimated_whenTilingPositionNotChange() {
        val task1 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayController.getDisplayContext(any())).thenReturn(context)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(desktopWindowDecoration.getLeash()).thenReturn(surfaceControlMock)

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        task1.configuration.windowConfiguration.setBounds(getLeftTaskBounds())
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            NON_STABLE_BOUNDS_MOCK,
            destinationBoundsOverride = null,
        )

        verify(toggleResizeDesktopTaskTransitionHandler, times(1))
            .startTransition(capture(wctCaptor), any(), any())
        verify(returnToDragStartAnimator, times(1)).start(any(), any(), any(), any(), anyOrNull())
        for (change in wctCaptor.value.changes) {
            val bounds = change.value.configuration.windowConfiguration.bounds
            val leftBounds = getLeftTaskBounds()
            assertRectEqual(bounds, leftBounds)
        }
    }

    @Test
    fun taskNotTiled_notBroughtToFront_tilingNotInitialised() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        assertThat(tilingDecoration.moveTiledPairToFront(task2.taskId, false)).isFalse()
        verify(transitions, never()).startTransition(any(), any(), any())
    }

    @Test
    fun taskNotTiled_notBroughtToFront_taskNotTiled() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val task3 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        assertThat(tilingDecoration.moveTiledPairToFront(task3.taskId, false)).isFalse()
        verify(transitions, never()).startTransition(any(), any(), any())
    }

    @Test
    fun taskTiled_broughtToFront_alreadyInFrontStillReorder() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(userRepositories.current.isVisibleTask(eq(task1.taskId))).thenReturn(true)
        whenever(userRepositories.current.isVisibleTask(eq(task2.taskId))).thenReturn(true)

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        verify(toggleResizeDesktopTaskTransitionHandler, times(2))
            .startTransition(capture(wctCaptor), any(), capture(callbackCaptor))
        (callbackCaptor.value).invoke()
        // Ensures tiling isn't brought to front if tasks aren't focused, for example when
        // initializing tiling from persistence.
        verify(transitions, never()).startTransition(eq(TRANSIT_TO_FRONT), any(), eq(null))

        task1.isFocused = true

        assertThat(tilingDecoration.moveTiledPairToFront(task1.taskId, isFocusedOnDisplay = true))
            .isTrue()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_TO_FRONT), any(), eq(null))
    }

    @Test
    fun taskTiled_broughtToFront_uponTilingFocusedTasks() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(userRepositories.current.isVisibleTask(eq(task1.taskId))).thenReturn(true)
        whenever(userRepositories.current.isVisibleTask(eq(task2.taskId))).thenReturn(true)
        task1.isFocused = true
        task2.isFocused = true

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        verify(toggleResizeDesktopTaskTransitionHandler, times(2))
            .startTransition(capture(wctCaptor), any(), capture(callbackCaptor))
        (callbackCaptor.value).invoke()

        verify(transitions, times(1)).startTransition(eq(TRANSIT_TO_FRONT), any(), eq(null))
    }

    @Test
    fun taskTiled_broughtToFront_bringToFront() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val task3 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(desktopWindowDecoration.getLeash()).thenReturn(surfaceControlMock)
        whenever(userRepositories.current.isVisibleTask(any())).thenReturn(true)
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        task1.isFocused = true
        task3.isFocused = true
        verify(toggleResizeDesktopTaskTransitionHandler, times(2))
            .startTransition(capture(wctCaptor), any(), capture(callbackCaptor))
        (callbackCaptor.value).invoke()

        assertThat(tilingDecoration.moveTiledPairToFront(task3.taskId, true)).isFalse()
        assertThat(tilingDecoration.moveTiledPairToFront(task1.taskId, true)).isTrue()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_TO_FRONT), any(), eq(null))
    }

    @Test
    fun taskTiled_broughtToFront_taskInfoNotUpdated_bringToFront() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val task3 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(desktopWindowDecoration.getLeash()).thenReturn(surfaceControlMock)
        whenever(userRepositories.current.isVisibleTask(any())).thenReturn(true)
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        verify(toggleResizeDesktopTaskTransitionHandler, times(2))
            .startTransition(capture(wctCaptor), any(), capture(callbackCaptor))
        (callbackCaptor.value).invoke()

        assertThat(tilingDecoration.moveTiledPairToFront(task3.taskId, isFocusedOnDisplay = true))
            .isFalse()
        assertThat(tilingDecoration.moveTiledPairToFront(task1.taskId, isFocusedOnDisplay = true))
            .isTrue()
        verify(transitions, times(1)).startTransition(eq(TRANSIT_TO_FRONT), any(), eq(null))
    }

    @Test
    fun taskTiledTasks_NotResized_BeforeTouchEndArrival() {
        // Setup
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        desktopWindowDecoration.mTaskInfo = task1
        task1.minWidth = 0
        task1.minHeight = 0
        initTiledTaskHelperMock(task1)
        desktopWindowDecoration.mDecorWindowContext = context
        whenever(resources.getBoolean(any())).thenReturn(true)

        // Act
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        tilingDecoration.leftTaskResizingHelper = tiledTaskHelper
        tilingDecoration.rightTaskResizingHelper = tiledTaskHelper
        tilingDecoration.onDividerHandleMoved(BOUNDS, transaction)

        // Assert
        verify(transaction, times(1)).apply()
        // Show should be called twice for each tiled app, to show the veil and the icon for each
        // of them.
        verify(tiledTaskHelper, times(2)).showVeil(any())

        // Move again
        tilingDecoration.onDividerHandleMoved(BOUNDS, transaction)
        verify(tiledTaskHelper, times(2)).updateVeil(any())
        verify(transitions, never()).startTransition(any(), any(), any())

        // End moving, no startTransition because bounds did not change.
        tiledTaskHelper.newBounds.set(BOUNDS)
        tilingDecoration.onDividerHandleDragEnd(BOUNDS, transaction, motionEvent)
        verify(tiledTaskHelper, times(2)).hideVeil()
        verify(transitions, never()).startTransition(any(), any(), any())

        // Move then end again with bounds changing to ensure startTransition is called.
        tilingDecoration.onDividerHandleMoved(BOUNDS, transaction)
        tilingDecoration.onDividerHandleDragEnd(BOUNDS, transaction, motionEvent)
        verify(transitions, times(1))
            .startTransition(eq(TRANSIT_CHANGE), any(), eq(tilingDecoration))
        // No hide veil until start animation is called.
        verify(tiledTaskHelper, times(2)).hideVeil()

        tilingDecoration.startAnimation(transition, info, transaction, transaction, finishCallback)
        // the startAnimation function should hide the veils.
        verify(tiledTaskHelper, times(4)).hideVeil()
    }

    @Test
    fun tiledTasksResizedUsingDividerHandle_shouldLogResizingEvents() {
        // Setup
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        desktopWindowDecoration.mTaskInfo = task1
        task1.minWidth = 0
        task1.minHeight = 0
        initTiledTaskHelperMock(task1)
        desktopWindowDecoration.mDecorWindowContext = context
        whenever(resources.getBoolean(any())).thenReturn(true)

        // Act
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.leftTaskResizingHelper = tiledTaskHelper
        tilingDecoration.rightTaskResizingHelper = tiledTaskHelper
        tilingDecoration.onDividerHandleDragStart(motionEvent)
        // Log start event for task1 and task2, but the tasks are the same in
        // this test, so we verify the same log twice.
        verify(desktopModeEventLogger, times(2))
            .logTaskResizingStarted(
                ResizeTrigger.TILING_DIVIDER,
                DesktopModeEventLogger.Companion.InputMethod.UNKNOWN_INPUT_METHOD,
                task1,
                BOUNDS.width() / 2,
                BOUNDS.height(),
                displayController,
            )

        tilingDecoration.onDividerHandleMoved(BOUNDS, transaction)
        tilingDecoration.onDividerHandleDragEnd(BOUNDS, transaction, motionEvent)
        // Log end event for task1 and task2, but the tasks are the same in
        // this test, so we verify the same log twice.
        verify(desktopModeEventLogger, times(2))
            .logTaskResizingEnded(
                ResizeTrigger.TILING_DIVIDER,
                DesktopModeEventLogger.Companion.InputMethod.UNKNOWN_INPUT_METHOD,
                task1,
                BOUNDS.width(),
                BOUNDS.height(),
                displayController,
            )
    }

    @Test
    fun taskTiled_shouldBeRemoved_whenTileBroken() {
        val task1 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.leftTaskResizingHelper = tiledTaskHelper

        tilingDecoration.removeTaskIfTiled(task1.taskId)

        assertThat(tilingDecoration.leftTaskResizingHelper).isNull()
        verify(desktopWindowDecoration, times(1)).removeDragResizeListener(any())
        verify(desktopWindowDecoration, times(1))
            .updateDisabledResizingEdge(eq(DragResizeWindowGeometry.DisabledEdge.NONE), eq(false))
        verify(tiledTaskHelper, times(1)).dispose()
    }

    @Test
    fun taskTiled_shouldBeRemoved_whenEnteringPip() {
        val task1 = createPipTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.leftTaskResizingHelper = tiledTaskHelper
        val changeInfo = createPipChangeTransition(task1)
        tilingDecoration.onTransitionReady(
            transition = mock(),
            info = changeInfo,
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        assertThat(tilingDecoration.leftTaskResizingHelper).isNull()
        verify(tiledTaskHelper, times(1)).dispose()
    }

    @Test
    fun tilingDivider_shouldBeShown_whenTiledTasksBecomeVisible() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val additionalTaskHelper: DesktopTilingWindowDecoration.AppResizingHelper = mock()
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
        whenever(additionalTaskHelper.taskInfo).thenReturn(task2)
        whenever(additionalTaskHelper.desktopModeWindowDecoration)
            .thenReturn(desktopWindowDecoration)

        tilingDecoration.leftTaskResizingHelper = tiledTaskHelper
        tilingDecoration.rightTaskResizingHelper = additionalTaskHelper
        tilingDecoration.desktopTilingDividerWindowManager = desktopTilingDividerWindowManager
        val changeInfo = createTransitFrontTransition(task1, task2)
        tilingDecoration.onTransitionReady(
            transition = mock(),
            info = changeInfo,
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(desktopTilingDividerWindowManager, times(1)).showDividerBar(equals(false))
    }

    @Test
    fun showDividerWithFadeIn_afterRecentsTransition() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val additionalTaskHelper: DesktopTilingWindowDecoration.AppResizingHelper = mock()
        val manager = desktopTilingDividerWindowManager
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
        whenever(additionalTaskHelper.taskInfo).thenReturn(task2)
        whenever(additionalTaskHelper.desktopModeWindowDecoration)
            .thenReturn(desktopWindowDecoration)

        tilingDecoration.apply {
            leftTaskResizingHelper = tiledTaskHelper
            rightTaskResizingHelper = additionalTaskHelper
            this.desktopTilingDividerWindowManager = manager
            isTilingManagerInitialised = true
        }
        val changeInfoWithRecents =
            createTransitFrontTransition(task1, task2, TRANSIT_START_RECENTS_TRANSITION)
        tilingDecoration.onTransitionReady(
            transition = mock(),
            info = changeInfoWithRecents,
            startTransaction = mock(),
            finishTransaction = mock(),
        )

        verify(desktopTilingDividerWindowManager, times(1)).hideDividerBar()

        tilingDecoration.onRecentsAnimationEndedToSameDesk()

        // First transition won't result in transit to front as there are no tiled tasks.
        verify(desktopTilingDividerWindowManager, times(1)).showDividerBar(equals(true))
    }

    @Test
    fun taskNotTiled_shouldNotBeRemoved_whenNotTiled() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.leftTaskResizingHelper = tiledTaskHelper

        tilingDecoration.removeTaskIfTiled(task2.taskId)

        assertThat(tilingDecoration.leftTaskResizingHelper).isNotNull()
        verify(desktopWindowDecoration, never()).removeDragResizeListener(any())
        verify(desktopWindowDecoration, never()).updateDisabledResizingEdge(any(), any())
        verify(tiledTaskHelper, never()).dispose()
    }

    @Test
    fun tasksTiled_shouldBeRemoved_whenSessionDestroyed() {
        val task1 = createVisibleTask()
        val task2 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.onAppTiled(
            task2,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )
        tilingDecoration.leftTaskResizingHelper = tiledTaskHelper
        tilingDecoration.rightTaskResizingHelper = tiledTaskHelper
        tilingDecoration.desktopTilingDividerWindowManager = desktopTilingDividerWindowManager

        tilingDecoration.resetTilingSession()

        assertThat(tilingDecoration.leftTaskResizingHelper).isNull()
        assertThat(tilingDecoration.rightTaskResizingHelper).isNull()
        verify(desktopWindowDecoration, times(2)).removeDragResizeListener(any())
        verify(tiledTaskHelper, times(2)).dispose()
        verify(context, never()).getApplicationContext()
    }

    @Test
    fun addLeftTiledTask_updatesTaskRepository_whenLeftTileInitializedOrBroken() {
        val task1 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.LEFT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        verify(desktopRepository, times(1)).addLeftTiledTaskToDesk(displayId, task1.taskId, deskId)
        verify(desktopRepository, never()).addRightTiledTaskToDesk(displayId, task1.taskId, deskId)

        tilingDecoration.removeTaskIfTiled(task1.taskId)

        verify(desktopRepository, times(1)).removeLeftTiledTaskFromDesk(displayId, deskId)
        verify(desktopRepository, never()).removeRightTiledTaskFromDesk(displayId, deskId)
    }

    @Test
    fun addRightTiledTask_updatesTaskRepository_whenRightTileInitializedOrBroken() {
        val task1 = createVisibleTask()
        val stableBounds = STABLE_BOUNDS_MOCK
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
        whenever(context.resources).thenReturn(resources)
        whenever(resources.getDimensionPixelSize(any())).thenReturn(split_divider_width)
        whenever(tiledTaskHelper.taskInfo).thenReturn(task1)
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)

        tilingDecoration.onAppTiled(
            task1,
            desktopWindowDecoration,
            DesktopTasksController.SnapPosition.RIGHT,
            BOUNDS,
            destinationBoundsOverride = null,
        )

        verify(desktopRepository, times(1)).addRightTiledTaskToDesk(displayId, task1.taskId, deskId)
        verify(desktopRepository, never()).addLeftTiledTaskToDesk(displayId, task1.taskId, deskId)

        tilingDecoration.removeTaskIfTiled(task1.taskId)

        verify(desktopRepository, times(1)).removeRightTiledTaskFromDesk(displayId, deskId)
        verify(desktopRepository, never()).removeLeftTiledTaskFromDesk(displayId, deskId)
    }

    private fun initTiledTaskHelperMock(taskInfo: ActivityManager.RunningTaskInfo) {
        whenever(tiledTaskHelper.bounds).thenReturn(BOUNDS)
        whenever(tiledTaskHelper.taskInfo).thenReturn(taskInfo)
        whenever(tiledTaskHelper.newBounds).thenReturn(Rect(BOUNDS))
        whenever(tiledTaskHelper.desktopModeWindowDecoration).thenReturn(desktopWindowDecoration)
    }

    private fun assertRectEqual(rect1: Rect, rect2: Rect) {
        assertThat(rect1.left).isEqualTo(rect2.left)
        assertThat(rect1.right).isEqualTo(rect2.right)
        assertThat(rect1.top).isEqualTo(rect2.top)
        assertThat(rect1.bottom).isEqualTo(rect2.bottom)
        return
    }

    private fun getRightTaskBounds(): Rect {
        val stableBounds = STABLE_BOUNDS_MOCK
        val destinationWidth = stableBounds.width() / 2
        val leftBound = stableBounds.right - destinationWidth + split_divider_width / 2
        return Rect(leftBound, stableBounds.top, stableBounds.right, stableBounds.bottom)
    }

    private fun getLeftTaskBounds(): Rect {
        val stableBounds = STABLE_BOUNDS_MOCK
        val destinationWidth = stableBounds.width() / 2
        val rightBound = stableBounds.left + destinationWidth - split_divider_width / 2
        return Rect(stableBounds.left, stableBounds.top, rightBound, stableBounds.bottom)
    }

    private fun createVisibleTask() =
        createFreeformTask().also {
            whenever(userRepositories.current.isVisibleTask(eq(it.taskId))).thenReturn(true)
        }

    private fun createPipTask() =
        createPinnedTask().also {
            whenever(userRepositories.current.isVisibleTask(eq(it.taskId))).thenReturn(true)
        }

    private fun createPipChangeTransition(task: RunningTaskInfo?, type: Int = TRANSIT_PIP) =
        TransitionInfo(type, /* flags= */ 0).apply {
            addChange(
                Change(mock(), mock()).apply {
                    mode = TRANSIT_PIP
                    parent = null
                    taskInfo = task
                    flags = flags
                }
            )
        }

    private fun createTransitFrontTransition(
        task1: RunningTaskInfo?,
        task2: RunningTaskInfo?,
        type: Int = TRANSIT_TO_FRONT,
    ) =
        TransitionInfo(type, /* flags= */ 0).apply {
            addChange(
                Change(mock(), mock()).apply {
                    mode = TRANSIT_TO_FRONT
                    parent = null
                    taskInfo = task1
                    flags = flags
                }
            )
            addChange(
                Change(mock(), mock()).apply {
                    mode = TRANSIT_TO_FRONT
                    parent = null
                    taskInfo = task2
                    flags = flags
                }
            )
        }

    companion object {
        private val NON_STABLE_BOUNDS_MOCK = Rect(50, 55, 100, 100)
        private val STABLE_BOUNDS_MOCK = Rect(0, 0, 100, 100)
        private val BOUNDS = Rect(1, 2, 3, 4)
    }
}
