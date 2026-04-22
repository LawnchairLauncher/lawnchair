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

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD
import android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW
import android.app.WindowConfiguration.WINDOWING_MODE_PINNED
import android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED
import android.app.WindowConfiguration.WindowingMode
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Region
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper.RunWithLooper
import android.view.Display.DEFAULT_DISPLAY
import android.view.ISystemGestureExclusionListener
import android.view.InsetsSource
import android.view.InsetsState
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.Surface
import android.view.SurfaceControl
import android.view.SurfaceView
import android.view.View
import android.view.ViewRootImpl
import android.view.WindowInsets.Type.statusBars
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.window.flags.Flags
import com.android.wm.shell.R
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopImmersiveController
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopTasksController
import com.android.wm.shell.desktopmode.DesktopTasksController.SnapPosition
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction
import com.android.wm.shell.recents.RecentsTransitionStateListener
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper
import com.android.wm.shell.shared.desktopmode.DesktopModeTransitionSource
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.util.StubTransaction
import com.google.common.truth.Truth.assertThat
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import junit.framework.Assert.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.KArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNotNull
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Tests of [DesktopModeWindowDecorViewModel]
 * Usage: atest WMShellUnitTests:DesktopModeWindowDecorViewModelTests
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
@RunWithLooper
class DesktopModeWindowDecorViewModelTests : DesktopModeWindowDecorViewModelTestsBase() {

    @Before
    fun setUp() {
        mockitoSession = ExtendedMockito.mockitoSession()
            .strictness(Strictness.LENIENT)
            .spyStatic(DragPositioningCallbackUtility::class.java)
            .startMocking()

        desktopState.canEnterDesktopMode = true
        desktopState.overridesShowAppHandle = false
        desktopState.isFreeformEnabled = true

        setUpCommon()
    }

    @Test
    fun testDeleteCaptionOnChangeTransitionWhenNecessary() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val taskSurface = SurfaceControl()
        val decoration = setUpMockDecorationForTask(task)

        onTaskOpening(task, taskSurface)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))

        task.setWindowingMode(WINDOWING_MODE_UNDEFINED)
        task.setActivityType(ACTIVITY_TYPE_UNDEFINED)
        onTaskChanging(task, taskSurface)

        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
        verify(decoration).close()
    }

    @Test
    fun testCreateCaptionOnChangeTransitionWhenNecessary() {
        val task = createTask(
            windowingMode = WINDOWING_MODE_UNDEFINED,
            activityType = ACTIVITY_TYPE_UNDEFINED,
        )
        val taskSurface = SurfaceControl()
        setUpMockDecorationForTask(task)

        onTaskChanging(task, taskSurface)
        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))

        task.setWindowingMode(WINDOWING_MODE_FREEFORM)
        task.setActivityType(ACTIVITY_TYPE_STANDARD)
        onTaskChanging(task, taskSurface)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_HANDLE_INPUT_FIX)
    fun testCreateAndDisposeEventReceiver() {
        val decor = createOpenTaskDecoration(windowingMode = WINDOWING_MODE_FREEFORM)
        desktopModeWindowDecorViewModel.destroyWindowDecoration(decor.mTaskInfo)

        verify(mockInputMonitorFactory).create(any(), any())
        verify(mockInputMonitor).dispose()
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_HANDLE_INPUT_FIX)
    fun testEventReceiversOnMultipleDisplays() {
        val secondaryDisplay = createVirtualDisplay() ?: return
        val secondaryDisplayId = secondaryDisplay.display.displayId
        val task = createTask(displayId = DEFAULT_DISPLAY, windowingMode = WINDOWING_MODE_FREEFORM)
        val secondTask = createTask(
            displayId = secondaryDisplayId,
            windowingMode = WINDOWING_MODE_FREEFORM,
        )
        val thirdTask = createTask(
            displayId = secondaryDisplayId,
            windowingMode = WINDOWING_MODE_FREEFORM,
        )
        setUpMockDecorationsForTasks(task, secondTask, thirdTask)

        onTaskOpening(task)
        onTaskOpening(secondTask)
        onTaskOpening(thirdTask)
        desktopModeWindowDecorViewModel.destroyWindowDecoration(thirdTask)
        desktopModeWindowDecorViewModel.destroyWindowDecoration(task)
        secondaryDisplay.release()

        verify(mockInputMonitorFactory, times(2)).create(any(), any())
        verify(mockInputMonitor, times(1)).dispose()
    }

    @Test
    fun snapToHalfScreen_callsCorrectPersistenceFunction() {
        val task = createTask(displayId = DEFAULT_DISPLAY, windowingMode = WINDOWING_MODE_FREEFORM)
        desktopModeWindowDecorViewModel.snapToHalfScreen(
            task,
            INITIAL_BOUNDS,
            DesktopTasksController.SnapPosition.LEFT,
        )

        verify(mockTilingWindowDecoration, times(1))
            .snapToHalfScreen(any(), anyOrNull(), any(), any(), isNull())

        desktopModeWindowDecorViewModel.snapPersistedTaskToHalfScreen(
            task,
            INITIAL_BOUNDS,
            DesktopTasksController.SnapPosition.LEFT,
        )

        verify(mockTilingWindowDecoration, times(1))
            .snapToHalfScreen(any(), anyOrNull(), any(), any(), isNotNull())
    }

    @Test
    fun overviewAnimationChanges_shouldNotifyTiling() {
        desktopModeWindowDecorViewModel.onRecentsAnimationEndedToSameDesk()

        verify(mockTilingWindowDecoration, times(1))
            .onOverviewAnimationEndedToSameDesk()
    }

    @Test
    fun testBackEventHasRightDisplayId() {
        val secondaryDisplay = createVirtualDisplay() ?: return
        val secondaryDisplayId = secondaryDisplay.display.displayId
        val task = createTask(
            displayId = secondaryDisplayId,
            windowingMode = WINDOWING_MODE_FREEFORM,
        )
        val windowDecor = setUpMockDecorationForTask(task)

        onTaskOpening(task)
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        verify(windowDecor).setCaptionListeners(
            onClickListenerCaptor.capture(), any(), any(), any())

        val onClickListener = onClickListenerCaptor.firstValue
        val view = mock<View> {
            on { id } doReturn R.id.back_button
        }

        val inputManager = mock<InputManager>()
        spyContext.addMockSystemService(InputManager::class.java, inputManager)

        desktopModeWindowDecorViewModel
            .setFreeformTaskTransitionStarter(mockFreeformTaskTransitionStarter)

        onClickListener.onClick(view)

        val eventCaptor = argumentCaptor<KeyEvent>()
        verify(inputManager, times(2)).injectInputEvent(eventCaptor.capture(), any<Int>())

        assertThat(eventCaptor.firstValue.displayId).isEqualTo(secondaryDisplayId)
        assertThat(eventCaptor.secondValue.displayId).isEqualTo(secondaryDisplayId)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS)
    fun testCloseButtonInFreeform_closeWindow() {
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            onCaptionButtonClickListener = onClickListenerCaptor,
        )

        val view = mock<View> {
            on { id } doReturn R.id.close_window
        }

        desktopModeWindowDecorViewModel
            .setFreeformTaskTransitionStarter(mockFreeformTaskTransitionStarter)

        onClickListenerCaptor.firstValue.onClick(view)

        verify(mockDesktopTasksController, never()).getNextFocusedTask(decor.mTaskInfo)

        val transactionCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(mockFreeformTaskTransitionStarter).startRemoveTransition(transactionCaptor.capture())
        val wct = transactionCaptor.firstValue

        assertThat(wct.hierarchyOps).hasSize(1)
        val hierarchyOp = wct.hierarchyOps[0]
        assertThat(hierarchyOp.type).isEqualTo(HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK)
        assertThat(hierarchyOp.container).isEqualTo(decor.mTaskInfo.token.asBinder())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS)
    fun testCloseButtonInFreeform_withStateChangeAnnouncementFlag_closeWindow() {
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            onCaptionButtonClickListener = onClickListenerCaptor,
        )

        val view = mock<View> {
            on { id } doReturn R.id.close_window
        }

        desktopModeWindowDecorViewModel
            .setFreeformTaskTransitionStarter(mockFreeformTaskTransitionStarter)

        onClickListenerCaptor.firstValue.onClick(view)

        verify(mockDesktopTasksController).getNextFocusedTask(decor.mTaskInfo)

        val transactionCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(mockFreeformTaskTransitionStarter).startRemoveTransition(transactionCaptor.capture())
        val wct = transactionCaptor.firstValue

        assertThat(wct.hierarchyOps).hasSize(1)
        val hierarchyOp = wct.hierarchyOps[0]
        assertThat(hierarchyOp.type).isEqualTo(HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK)
        assertThat(hierarchyOp.container).isEqualTo(decor.mTaskInfo.token.asBinder())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_MINIMIZE_BUTTON)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS)
    fun testMinimizeButtonInFreeform_minimizeWindow() {
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            onCaptionButtonClickListener = onClickListenerCaptor,
        )

        val view = mock<View> {
            on { id } doReturn R.id.minimize_window
        }

        desktopModeWindowDecorViewModel
            .setFreeformTaskTransitionStarter(mockFreeformTaskTransitionStarter)

        onClickListenerCaptor.firstValue.onClick(view)

        verify(mockDesktopTasksController, never()).getNextFocusedTask(decor.mTaskInfo)
        verify(mockDesktopTasksController)
            .minimizeTask(decor.mTaskInfo, MinimizeReason.MINIMIZE_BUTTON)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_MINIMIZE_BUTTON,
        Flags.FLAG_ENABLE_DESKTOP_APP_HEADER_STATE_CHANGE_ANNOUNCEMENTS
    )
    fun testMinimizeButtonInFreeform_withStateChangeAnnouncementFlag_minimizeWindow() {
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            onCaptionButtonClickListener = onClickListenerCaptor,
        )

        val view = mock<View> {
            on { id } doReturn R.id.minimize_window
        }

        desktopModeWindowDecorViewModel
            .setFreeformTaskTransitionStarter(mockFreeformTaskTransitionStarter)

        onClickListenerCaptor.firstValue.onClick(view)

        verify(mockDesktopTasksController).getNextFocusedTask(decor.mTaskInfo)
        verify(mockDesktopTasksController)
            .minimizeTask(decor.mTaskInfo, MinimizeReason.MINIMIZE_BUTTON)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun testDecorationIsNotCreatedForNoDisplayActivities() {
        val task = createTask(windowingMode = WINDOWING_MODE_FULLSCREEN).apply {
            isTopActivityNoDisplay = true
        }
        onTaskOpening(task)

        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun testDecorationIsNotCreatedForTopTranslucentActivities() {
        val task = createTask(windowingMode = WINDOWING_MODE_FULLSCREEN).apply {
            isActivityStackTransparent = true
            numActivities = 1
        }
        onTaskOpening(task)

        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun testDecorationIsNotCreatedForSystemUIActivities() {
        // Set task as systemUI package
        val systemUIPackageName = context.resources.getString(
            com.android.internal.R.string.config_systemUi)
        val baseComponent = ComponentName(systemUIPackageName, /* class */ "")
        val task = createTask(windowingMode = WINDOWING_MODE_FULLSCREEN).apply {
            baseActivity = baseComponent
        }

        onTaskOpening(task)

        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODALS_POLICY)
    fun testDecorationIsNotCreatedForDefaultHomePackage() {
        val task = createTask(windowingMode = WINDOWING_MODE_FULLSCREEN).apply {
            baseActivity = homeComponentName
        }

        onTaskOpening(task)

        assertFalse(windowDecorByTaskIdSpy.contains(task.taskId))
    }

    @Test
    fun testOnTaskInfoChanged_tilingNotified() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        setUpMockDecorationsForTasks(task)

        onTaskOpening(task)
        desktopModeWindowDecorViewModel.onTaskInfoChanged(task)

        verify(mockTilingWindowDecoration).onTaskInfoChange(task)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_IMMERSIVE_HANDLE_HIDING)
    fun testInsetsStateChanged_notifiesAllDecorsInDisplay() {
        val task1 = createTask(windowingMode = WINDOWING_MODE_FREEFORM, displayId = 1)
        val decoration1 = setUpMockDecorationForTask(task1)
        onTaskOpening(task1)
        val task2 = createTask(windowingMode = WINDOWING_MODE_FREEFORM, displayId = 2)
        val decoration2 = setUpMockDecorationForTask(task2)
        onTaskOpening(task2)
        val task3 = createTask(windowingMode = WINDOWING_MODE_FREEFORM, displayId = 2)
        val decoration3 = setUpMockDecorationForTask(task3)
        onTaskOpening(task3)

        // Add status bar insets source
        val insetsState = InsetsState().apply {
            addSource(InsetsSource(0 /* id */, statusBars()).apply {
                isVisible = false
            })
        }
        desktopModeOnInsetsChangedListener.insetsChanged(2 /* displayId */, insetsState)

        verify(decoration1, never()).onInsetsStateChanged(insetsState)
        verify(decoration2).onInsetsStateChanged(insetsState)
        verify(decoration3).onInsetsStateChanged(insetsState)
    }

    @Test
    fun testKeyguardState_notifiesAllDecors() {
        val decoration1 = createOpenTaskDecoration(windowingMode = WINDOWING_MODE_FREEFORM)
        val decoration2 = createOpenTaskDecoration(windowingMode = WINDOWING_MODE_FREEFORM)
        val decoration3 = createOpenTaskDecoration(windowingMode = WINDOWING_MODE_FREEFORM)

        desktopModeOnKeyguardChangedListener
            .onKeyguardVisibilityChanged(true /* visible */, true /* occluded */,
                false /* animatingDismiss */)

        verify(decoration1).onKeyguardStateChanged(true /* visible */, true /* occluded */)
        verify(decoration2).onKeyguardStateChanged(true /* visible */, true /* occluded */)
        verify(decoration3).onKeyguardStateChanged(true /* visible */, true /* occluded */)
    }

    @Test
    fun testDestroyWindowDecoration_closesBeforeCleanup() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val decoration = setUpMockDecorationForTask(task)
        val inOrder = Mockito.inOrder(decoration, windowDecorByTaskIdSpy)

        onTaskOpening(task)
        desktopModeWindowDecorViewModel.destroyWindowDecoration(task)

        inOrder.verify(decoration).close()
        inOrder.verify(windowDecorByTaskIdSpy).remove(task.taskId)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    fun testWindowDecor_deviceEligibleForDesktopMode_decorCreated() {
        val task = createTask(windowingMode = WINDOWING_MODE_FULLSCREEN)
        setUpMockDecorationsForTasks(task)

        onTaskOpening(task)
        assertTrue(task.taskId in windowDecorByTaskIdSpy)
    }

    @Test
    fun testOnDecorMaximizedOrRestored_togglesTaskSize_maximize() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
        )

        windowDecorationActionsCaptor.firstValue.onMaximizeOrRestore(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController).toggleDesktopTaskSize(
            decor.mTaskInfo,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.MAXIMIZE_MENU_TO_MAXIMIZE,
                InputMethod.UNKNOWN_INPUT_METHOD,
            )
        )
    }

    @Test
    fun testOnDecorMaximizedOrRestored_togglesTaskSize_maximizeFromMaximizedSize() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor
        )
        val movedMaximizedBounds = Rect(STABLE_BOUNDS)
        movedMaximizedBounds.offset(10, 10)
        decor.mTaskInfo.configuration.windowConfiguration.bounds.set(movedMaximizedBounds)

        windowDecorationActionsCaptor.firstValue.onMaximizeOrRestore(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController).toggleDesktopTaskSize(
            decor.mTaskInfo,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                ToggleTaskSizeInteraction.Source.MAXIMIZE_MENU_TO_MAXIMIZE,
                InputMethod.UNKNOWN_INPUT_METHOD,
            )
        )
    }

    @Test
    fun testOnDecorMaximizedOrRestored_togglesTaskSize_restore() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
        )
        decor.mTaskInfo.configuration.windowConfiguration.bounds.set(STABLE_BOUNDS)

        windowDecorationActionsCaptor.firstValue.onMaximizeOrRestore(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController).toggleDesktopTaskSize(
            decor.mTaskInfo,
            ToggleTaskSizeInteraction(
                ToggleTaskSizeInteraction.Direction.RESTORE,
                ToggleTaskSizeInteraction.Source.MAXIMIZE_MENU_TO_RESTORE,
                InputMethod.UNKNOWN_INPUT_METHOD,
            )
        )
    }

    @Test
    fun testOnDecorSnappedLeft_snapResizes() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
        )

        windowDecorationActionsCaptor.firstValue.onLeftSnap(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController).handleInstantSnapResizingTask(
            eq(decor.mTaskInfo),
            eq(SnapPosition.LEFT),
            eq(ResizeTrigger.SNAP_LEFT_MENU),
            eq(InputMethod.UNKNOWN_INPUT_METHOD),
        )
    }

    @Test
    @DisableFlags(Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING)
    fun testOnSnapResizeLeft_nonResizable_decorSnappedLeft() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor
        ).apply {
            mTaskInfo.isResizeable = false
        }

        windowDecorationActionsCaptor.firstValue.onLeftSnap(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController).handleInstantSnapResizingTask(
            eq(decor.mTaskInfo),
            eq(SnapPosition.LEFT),
            eq(ResizeTrigger.SNAP_LEFT_MENU),
            eq(InputMethod.UNKNOWN_INPUT_METHOD),
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING)
    fun testOnSnapResizeLeft_nonResizable_decorNotSnapped() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
        ).apply {
            mTaskInfo.isResizeable = false
        }

        val currentBounds = decor.mTaskInfo.configuration.windowConfiguration.bounds
        windowDecorationActionsCaptor.firstValue.onLeftSnap(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController, never())
            .snapToHalfScreen(
                eq(decor.mTaskInfo), any(), eq(currentBounds), eq(SnapPosition.LEFT),
                eq(ResizeTrigger.MAXIMIZE_BUTTON),
                eq(InputMethod.UNKNOWN_INPUT_METHOD),
            )
    }

    @Test
    fun testOnDecorSnappedRight_snapResizes() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor
        )

        windowDecorationActionsCaptor.firstValue.onRightSnap(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController).handleInstantSnapResizingTask(
            eq(decor.mTaskInfo),
            eq(SnapPosition.RIGHT),
            eq(ResizeTrigger.SNAP_RIGHT_MENU),
            eq(InputMethod.UNKNOWN_INPUT_METHOD),
        )
    }

    @Test
    @DisableFlags(Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING)
    fun testOnSnapResizeRight_nonResizable_decorSnappedRight() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
        ).apply {
            mTaskInfo.isResizeable = false
        }

        windowDecorationActionsCaptor.firstValue.onRightSnap(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController).handleInstantSnapResizingTask(
            eq(decor.mTaskInfo),
            eq(SnapPosition.RIGHT),
            eq(ResizeTrigger.SNAP_RIGHT_MENU),
            eq(InputMethod.UNKNOWN_INPUT_METHOD),
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_DISABLE_NON_RESIZABLE_APP_SNAP_RESIZING)
    fun testOnSnapResizeRight_nonResizable_decorNotSnapped() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
        ).apply {
            mTaskInfo.isResizeable = false
        }

        val currentBounds = decor.mTaskInfo.configuration.windowConfiguration.bounds
        windowDecorationActionsCaptor.firstValue.onRightSnap(
            decor.mTaskInfo.taskId,
            InputMethod.UNKNOWN_INPUT_METHOD
        )

        verify(mockDesktopTasksController, never())
            .snapToHalfScreen(
                eq(decor.mTaskInfo), any(), eq(currentBounds), eq(SnapPosition.RIGHT),
                eq(ResizeTrigger.MAXIMIZE_BUTTON),
                eq(InputMethod.UNKNOWN_INPUT_METHOD),
            )
    }

    @Test
    fun testDecor_onClickToDesktop_movesToDesktopWithSource() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FULLSCREEN,
            windowDecorationActions = windowDecorationActionsCaptor,
        )

        windowDecorationActionsCaptor.firstValue.onToDesktop(
            decor.mTaskInfo.taskId,
            DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON
        )

        verify(mockDesktopTasksController).moveTaskToDefaultDeskAndActivate(
            eq(decor.mTaskInfo.taskId),
            any(),
            eq(DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun testDecor_onClickToDesktop_addsCaptionInsets() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FULLSCREEN,
            windowDecorationActions = windowDecorationActionsCaptor,
        )

        windowDecorationActionsCaptor.firstValue.onToDesktop(
            decor.mTaskInfo.taskId,
            DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON
        )

        verify(decor).addCaptionInset(any())
    }

    @Test
    fun testDecor_onClickToFullscreen_isFreeform_movesToFullscreen() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
        )

        windowDecorationActionsCaptor.firstValue.onToFullscreen(decor.mTaskInfo.taskId)

        verify(mockDesktopTasksController).moveToFullscreen(
            decor.mTaskInfo.taskId,
            DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
            remoteTransition = null,
        )
    }

    @Test
    fun testDecor_onClickToFullscreen_isSplit_movesToFullscreen() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_MULTI_WINDOW,
            windowDecorationActions = windowDecorationActionsCaptor
        )

        windowDecorationActionsCaptor.firstValue.onToFullscreen(decor.mTaskInfo.taskId)

        verify(mockSplitScreenController).moveTaskToFullscreen(
            decor.mTaskInfo.taskId,
            SplitScreenController.EXIT_REASON_DESKTOP_MODE,
        )
    }

    @Test
    fun testDecor_onClickToSplitScreen_requestsSplit() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_MULTI_WINDOW,
            windowDecorationActions = windowDecorationActionsCaptor
        )

        windowDecorationActionsCaptor.firstValue.onToSplitScreen(decor.mTaskInfo.taskId)

        verify(mockDesktopTasksController).requestSplit(decor.mTaskInfo, leftOrTop = false)
    }

    @Test
    fun testDecor_onClickToOpenBrowser_opensBrowser() {
        doNothing().whenever(spyContext).startActivity(any())
        val uri = Uri.parse("https://www.google.com")
        val intent = Intent(ACTION_MAIN, uri)
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FULLSCREEN,
            windowDecorationActions = windowDecorationActionsCaptor
        )

        windowDecorationActionsCaptor.firstValue.onOpenInBrowser(decor.mTaskInfo.taskId, intent)

        verify(spyContext).startActivityAsUser(argThat { intent ->
            uri.equals(intent.data)
                    && intent.action == ACTION_MAIN
        }, any(), eq(mockUserHandle))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun testDecor_createWindowDecoration_setsAppHandleEducationTooltipClickCallbacks() {
        desktopState.canEnterDesktopMode = true

        shellInit.init()

        verify(
            mockAppHandleEducationController,
            times(1),
        ).setAppHandleEducationTooltipCallbacks(any(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun testDecor_invokeOpenHandleMenuCallback_openHandleMenu() {
        desktopState.canEnterDesktopMode = true
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val decor = setUpMockDecorationForTask(task)
        val openHandleMenuCallbackCaptor = argumentCaptor<(Int) -> Unit>()
        // Set task as gmail
        val gmailPackageName = "com.google.android.gm"
        val baseComponent = ComponentName(gmailPackageName, /* class */ "")
        task.baseActivity = baseComponent

        onTaskOpening(task)
        verify(
            mockAppHandleEducationController,
            times(1),
        ).setAppHandleEducationTooltipCallbacks(openHandleMenuCallbackCaptor.capture(), any())
        openHandleMenuCallbackCaptor.lastValue.invoke(task.taskId)
        bgExecutor.flushAll()
        testShellExecutor.flushAll()

        verify(decor, times(1)).createHandleMenu(any<Boolean>())
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun testDecor_openTaskWithFlagDisabled_doNotOpenHandleMenu() {
        desktopState.canEnterDesktopMode = true
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        setUpMockDecorationForTask(task)
        val openHandleMenuCallbackCaptor = argumentCaptor<(Int) -> Unit>()
        // Set task as gmail
        val gmailPackageName = "com.google.android.gm"
        val baseComponent = ComponentName(gmailPackageName, /* class */ "")
        task.baseActivity = baseComponent

        onTaskOpening(task)
        verify(
            mockAppHandleEducationController,
            never(),
        ).setAppHandleEducationTooltipCallbacks(openHandleMenuCallbackCaptor.capture(), any())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_APP_HANDLE_EDUCATION)
    fun testDecor_invokeOnToDesktopCallback_setsAppHandleEducationTooltipClickCallbacks() {
        desktopState.canEnterDesktopMode = true
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        setUpMockDecorationsForTasks(task)
        onTaskOpening(task)
        val onToDesktopCallbackCaptor = argumentCaptor<(Int, DesktopModeTransitionSource) -> Unit>()

        verify(
            mockAppHandleEducationController,
            times(1),
        ).setAppHandleEducationTooltipCallbacks(any(), onToDesktopCallbackCaptor.capture())
        onToDesktopCallbackCaptor.lastValue.invoke(
            task.taskId,
            DesktopModeTransitionSource.APP_HANDLE_MENU_BUTTON,
        )

        verify(mockDesktopTasksController, times(1))
            .moveTaskToDefaultDeskAndActivate(any(), any(), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun testOnDisplayRotation_tasksOutOfValidArea_taskBoundsUpdated() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val secondTask =
            createTask(displayId = task.displayId, windowingMode = WINDOWING_MODE_FREEFORM)
        val thirdTask =
            createTask(displayId = task.displayId, windowingMode = WINDOWING_MODE_FREEFORM)

        ExtendedMockito.doReturn(true).`when` {
            DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(any(), any())
        }
        setUpMockDecorationsForTasks(task, secondTask, thirdTask)

        onTaskOpening(task)
        onTaskOpening(secondTask)
        onTaskOpening(thirdTask)

        val wct = mock<WindowContainerTransaction>()

        displayChangingListener.onDisplayChange(
            task.displayId, Surface.ROTATION_0, Surface.ROTATION_90, null, wct
        )

        verify(wct).setBounds(eq(task.token), any())
        verify(wct).setBounds(eq(secondTask.token), any())
        verify(wct).setBounds(eq(thirdTask.token), any())
    }

    @Test
    fun testOnDisplayRotation_taskInValidArea_taskBoundsNotUpdated() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val secondTask =
            createTask(displayId = task.displayId, windowingMode = WINDOWING_MODE_FREEFORM)
        val thirdTask =
            createTask(displayId = task.displayId, windowingMode = WINDOWING_MODE_FREEFORM)

        ExtendedMockito.doReturn(false).`when` {
            DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(any(), any())
        }
        setUpMockDecorationsForTasks(task, secondTask, thirdTask)

        onTaskOpening(task)
        onTaskOpening(secondTask)
        onTaskOpening(thirdTask)

        val wct = mock<WindowContainerTransaction>()
        displayChangingListener.onDisplayChange(
            task.displayId, Surface.ROTATION_0, Surface.ROTATION_90, null, wct
        )

        verify(wct, never()).setBounds(eq(task.token), any())
        verify(wct, never()).setBounds(eq(secondTask.token), any())
        verify(wct, never()).setBounds(eq(thirdTask.token), any())
    }

    @Test
    fun testOnDisplayRotation_sameOrientationRotation_taskBoundsNotUpdated() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val secondTask =
            createTask(displayId = task.displayId, windowingMode = WINDOWING_MODE_FREEFORM)
        val thirdTask =
            createTask(displayId = task.displayId, windowingMode = WINDOWING_MODE_FREEFORM)

        setUpMockDecorationsForTasks(task, secondTask, thirdTask)

        onTaskOpening(task)
        onTaskOpening(secondTask)
        onTaskOpening(thirdTask)

        val wct = mock<WindowContainerTransaction>()
        displayChangingListener.onDisplayChange(
            task.displayId, Surface.ROTATION_0, Surface.ROTATION_180, null, wct
        )

        verify(wct, never()).setBounds(eq(task.token), any())
        verify(wct, never()).setBounds(eq(secondTask.token), any())
        verify(wct, never()).setBounds(eq(thirdTask.token), any())
    }

    @Test
    fun testOnDisplayRotation_differentDisplayId_taskBoundsNotUpdated() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val secondTask = createTask(displayId = -2, windowingMode = WINDOWING_MODE_FREEFORM)
        val thirdTask = createTask(displayId = -3, windowingMode = WINDOWING_MODE_FREEFORM)

        ExtendedMockito.doReturn(true).`when` {
            DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(any(), any())
        }
        setUpMockDecorationsForTasks(task, secondTask, thirdTask)

        onTaskOpening(task)
        onTaskOpening(secondTask)
        onTaskOpening(thirdTask)

        val wct = mock<WindowContainerTransaction>()
        displayChangingListener.onDisplayChange(
            task.displayId, Surface.ROTATION_0, Surface.ROTATION_90, null, wct
        )

        verify(wct).setBounds(eq(task.token), any())
        verify(wct, never()).setBounds(eq(secondTask.token), any())
        verify(wct, never()).setBounds(eq(thirdTask.token), any())
    }

    @Test
    fun testOnDisplayRotation_nonFreeformTask_taskBoundsNotUpdated() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val secondTask = createTask(displayId = -2, windowingMode = WINDOWING_MODE_FULLSCREEN)
        val thirdTask = createTask(displayId = -3, windowingMode = WINDOWING_MODE_PINNED)

        ExtendedMockito.doReturn(true).`when` {
            DragPositioningCallbackUtility.snapTaskBoundsIfNecessary(any(), any())
        }
        setUpMockDecorationsForTasks(task, secondTask, thirdTask)

        onTaskOpening(task)
        onTaskOpening(secondTask)
        onTaskOpening(thirdTask)

        val wct = mock<WindowContainerTransaction>()
        displayChangingListener.onDisplayChange(
            task.displayId, Surface.ROTATION_0, Surface.ROTATION_90, null, wct
        )

        verify(wct).setBounds(eq(task.token), any())
        verify(wct, never()).setBounds(eq(secondTask.token), any())
        verify(wct, never()).setBounds(eq(thirdTask.token), any())
    }

    @Test
    fun testCloseButtonInFreeform_closeWindow_ignoreMoveEventsWithoutBoundsChange() {
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        val onTouchListenerCaptor = argumentCaptor<View.OnTouchListener>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            onCaptionButtonClickListener = onClickListenerCaptor,
            onCaptionButtonTouchListener = onTouchListenerCaptor,
        )

        mockTaskPositioner.stub {
            on { onDragPositioningStart(any(), any(), any(), any()) } doReturn INITIAL_BOUNDS
            on { onDragPositioningMove(any(), any(), any()) } doReturn INITIAL_BOUNDS
            on { onDragPositioningEnd(any(), any(), any()) } doReturn INITIAL_BOUNDS
        }

        val viewRootImpl = mock<ViewRootImpl> {
            on { inputToken } doReturn null
        }
        val view = mock<View> {
            on { id } doReturn R.id.close_window
            on { getViewRootImpl() } doReturn viewRootImpl
        }

        desktopModeWindowDecorViewModel
            .setFreeformTaskTransitionStarter(mockFreeformTaskTransitionStarter)

        onTouchListenerCaptor.firstValue.onTouch(view,
            MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                MotionEvent.ACTION_DOWN, /* x= */ 0f, /* y= */ 0f, /* metaState= */ 0))
        onTouchListenerCaptor.firstValue.onTouch(view,
            MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                MotionEvent.ACTION_MOVE, /* x= */ 0f, /* y= */ 0f, /* metaState= */ 0))
        onTouchListenerCaptor.firstValue.onTouch(view,
            MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, /* x= */ 0f, /* y= */ 0f, /* metaState= */ 0))
        onClickListenerCaptor.firstValue.onClick(view)

        val transactionCaptor = argumentCaptor<WindowContainerTransaction>()
        verify(mockFreeformTaskTransitionStarter).startRemoveTransition(transactionCaptor.capture())
        val wct = transactionCaptor.firstValue


        assertThat(wct.hierarchyOps).hasSize(1)
        val hierarchyOp = wct.hierarchyOps[0]
        assertThat(hierarchyOp.type).isEqualTo(HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_TASK)
        assertThat(hierarchyOp.container).isEqualTo(decor.mTaskInfo.token.asBinder())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun testImmersiveRestoreButtonClick_exitsImmersiveMode() {
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            onCaptionButtonClickListener = onClickListenerCaptor,
            requestingImmersive = true,
        )
        val view = mock<View> {
            on { id } doReturn R.id.maximize_window
        }
        mockDesktopRepository.stub {
            on { isTaskInFullImmersiveState(decor.mTaskInfo.taskId) } doReturn true
        }

        onClickListenerCaptor.firstValue.onClick(view)

        verify(mockDesktopImmersiveController).moveTaskToNonImmersive(
            decor.mTaskInfo,
            DesktopImmersiveController.ExitReason.USER_INTERACTION,
        )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun testMaximizeButtonClick_notRequestingImmersive_togglesDesktopTaskSize() {
        val onClickListenerCaptor = argumentCaptor<View.OnClickListener>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            onCaptionButtonClickListener = onClickListenerCaptor,
            requestingImmersive = false,
        )
        val view = mock<View> {
            on { id } doReturn R.id.maximize_window
        }

        onClickListenerCaptor.firstValue.onClick(view)

        verify(mockDesktopTasksController)
            .toggleDesktopTaskSize(
                decor.mTaskInfo,
                ToggleTaskSizeInteraction(
                    ToggleTaskSizeInteraction.Direction.MAXIMIZE,
                    ToggleTaskSizeInteraction.Source.HEADER_BUTTON_TO_MAXIMIZE,
                    InputMethod.UNKNOWN_INPUT_METHOD,
                )
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun testImmersiveMenuOptionClick_entersImmersiveMode() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
            requestingImmersive = true,
        )
        mockDesktopRepository.stub {
            on { isTaskInFullImmersiveState(decor.mTaskInfo.taskId) } doReturn false
        }

        windowDecorationActionsCaptor.firstValue.onImmersiveOrRestore(decor.mTaskInfo)

        verify(mockDesktopImmersiveController).moveTaskToImmersive(decor.mTaskInfo)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    fun testImmersiveMenuOptionClick_exitsTiling() {
        val windowDecorationActionsCaptor = argumentCaptor<WindowDecorationActions>()
        val decor = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            windowDecorationActions = windowDecorationActionsCaptor,
            requestingImmersive = true,
        )
        mockDesktopRepository.stub {
            on { isTaskInFullImmersiveState(decor.mTaskInfo.taskId) } doReturn false
        }

        windowDecorationActionsCaptor.firstValue.onImmersiveOrRestore(decor.mTaskInfo)

        verify(mockTilingWindowDecoration)
            .removeTaskIfTiled(decor.mTaskInfo.displayId, decor.mTaskInfo.taskId)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS)
    fun testOnTaskInfoChanged_enableShellTransitionsFlag() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val taskSurface = SurfaceControl()
        val decoration = setUpMockDecorationForTask(task)

        onTaskOpening(task, taskSurface)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))

        decoration.mHasGlobalFocus = true
        desktopModeWindowDecorViewModel.onTaskInfoChanged(task)
        verify(decoration).relayout(eq(task), eq(true), anyOrNull())

        decoration.mHasGlobalFocus = false
        desktopModeWindowDecorViewModel.onTaskInfoChanged(task)
        verify(decoration).relayout(eq(task), eq(false), anyOrNull())
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS)
    fun testOnTaskInfoChanged_disableShellTransitionsFlag() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val taskSurface = SurfaceControl()
        val decoration = setUpMockDecorationForTask(task)

        onTaskOpening(task, taskSurface)
        assertTrue(windowDecorByTaskIdSpy.contains(task.taskId))

        task.isFocused = true
        desktopModeWindowDecorViewModel.onTaskInfoChanged(task)
        verify(decoration).relayout(eq(task), eq(true), anyOrNull())

        task.isFocused = false
        desktopModeWindowDecorViewModel.onTaskInfoChanged(task)
        verify(decoration).relayout(eq(task), eq(false), anyOrNull())
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun testGestureExclusionChanged_updatesDecorations() {
        val captor = argumentCaptor<ISystemGestureExclusionListener>()
        verify(mockWindowManager)
            .registerSystemGestureExclusionListener(captor.capture(), eq(DEFAULT_DISPLAY))
        val task = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            displayId = DEFAULT_DISPLAY,
        )
        val task2 = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            displayId = DEFAULT_DISPLAY,
        )
        val newRegion = Region.obtain().apply {
            set(Rect(0, 0, 1600, 80))
        }

        captor.firstValue.onSystemGestureExclusionChanged(DEFAULT_DISPLAY, newRegion, newRegion)
        testShellExecutor.flushAll()

        verify(task).onExclusionRegionChanged(newRegion)
        verify(task2).onExclusionRegionChanged(newRegion)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun testGestureExclusionChanged_updatesDecorations_onlyOnItsDisplayId() {
        val gestureExclusionCaptor = argumentCaptor<ISystemGestureExclusionListener>()
        val displayListenerCaptor = argumentCaptor<DisplayController.OnDisplaysChangedListener>()
        verify(mockDisplayController).addDisplayWindowListener(displayListenerCaptor.capture())
        displayListenerCaptor.firstValue.onDisplayAdded(DEFAULT_DISPLAY)
        displayListenerCaptor.firstValue.onDisplayAdded(SECOND_DISPLAY)
        verify(mockWindowManager)
            .registerSystemGestureExclusionListener(gestureExclusionCaptor.capture(),
                eq(DEFAULT_DISPLAY))
        verify(mockWindowManager)
            .registerSystemGestureExclusionListener(gestureExclusionCaptor.capture(),
                eq(SECOND_DISPLAY))
        val task = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            displayId = DEFAULT_DISPLAY,
        )
        val task2 = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            displayId = SECOND_DISPLAY,
        )
        val newRegion = Region.obtain().apply {
            set(Rect(0, 0, 1600, 80))
        }

        gestureExclusionCaptor.firstValue.onSystemGestureExclusionChanged(SECOND_DISPLAY, newRegion,
            newRegion)
        testShellExecutor.flushAll()

        verify(task, never()).onExclusionRegionChanged(newRegion)
        verify(task2).onExclusionRegionChanged(newRegion)
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY)
    fun testGestureExclusionChanged_otherDisplay_skipsDecorationUpdate() {
        val captor = argumentCaptor<ISystemGestureExclusionListener>()
        verify(mockWindowManager)
            .registerSystemGestureExclusionListener(captor.capture(), eq(DEFAULT_DISPLAY))
        val task = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            displayId = DEFAULT_DISPLAY,
        )
        val task2 = createOpenTaskDecoration(
            windowingMode = WINDOWING_MODE_FREEFORM,
            displayId = 2,
        )
        val newRegion = Region.obtain().apply {
            set(Rect(0, 0, 1600, 80))
        }

        captor.firstValue.onSystemGestureExclusionChanged(DEFAULT_DISPLAY, newRegion, newRegion)
        testShellExecutor.flushAll()

        verify(task).onExclusionRegionChanged(newRegion)
        verify(task2, never()).onExclusionRegionChanged(newRegion)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    fun testRecentsTransitionStateListener_requestedState_setsTransitionRunning() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val decoration = setUpMockDecorationForTask(task)
        onTaskOpening(task, SurfaceControl())

        desktopModeRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED)

        verify(decoration).setIsRecentsTransitionRunning(true)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    fun testRecentsTransitionStateListener_nonRunningState_setsTransitionNotRunning() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val decoration = setUpMockDecorationForTask(task)
        onTaskOpening(task, SurfaceControl())
        desktopModeRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED)

        desktopModeRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING)

        verify(decoration).setIsRecentsTransitionRunning(false)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    fun testRecentsTransitionStateListener_requestedAndAnimating_setsTransitionRunningOnce() {
        val task = createTask(windowingMode = WINDOWING_MODE_FREEFORM)
        val decoration = setUpMockDecorationForTask(task)
        onTaskOpening(task, SurfaceControl())

        desktopModeRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED)
        desktopModeRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_ANIMATING)

        verify(decoration, times(1)).setIsRecentsTransitionRunning(true)
    }

    @Test
    fun testOnTaskOpening_expandedBubbleTask_skipsWindowDecorationCreation() {
        val taskInfo = createTask(windowingMode = WINDOWING_MODE_MULTI_WINDOW).apply {
            // Bubble task is launched with ActivityOptions#setTaskAlwaysOnTop
            // in BubbleTaskViewListener#onInitialized.
            configuration.windowConfiguration.setAlwaysOnTop(true)
        }
        mockBubbleController.stub {
            on { hasStableBubbleForTask(taskInfo.taskId) } doReturn true
        }

        val isWindowDecorCreated = desktopModeWindowDecorViewModel.onTaskOpening(
            taskInfo,
            SurfaceControl(), /* taskSurface */
            StubTransaction(), /* startT */
            StubTransaction(), /* finishT */
        )

        assertThat(isWindowDecorCreated).isFalse()
    }

    @Test
    fun testOnTaskChanging_collapsedBubbleTask_skipsWindowDecorationCreation() {
        assumeTrue(BubbleAnythingFlagHelper.enableCreateAnyBubbleWithForceExcludedFromRecents())

        val taskInfo = createTask(windowingMode = WINDOWING_MODE_MULTI_WINDOW)
        mockBubbleController.stub {
            on { hasStableBubbleForTask(taskInfo.taskId) } doReturn true
        }

        desktopModeWindowDecorViewModel.onTaskChanging(
            taskInfo,
            SurfaceControl(), /* taskSurface */
            StubTransaction(), /* startT */
            StubTransaction(), /* finishT */
        )

        assertThat(windowDecorByTaskIdSpy.contains(taskInfo.taskId)).isFalse()
    }

    @Test
    fun testOnTaskChanging_convertTaskToBubble_destroysWindowDecoration() {
        assumeTrue(BubbleAnythingFlagHelper.enableCreateAnyBubbleWithForceExcludedFromRecents())

        val taskInfo = createTask(windowingMode = WINDOWING_MODE_MULTI_WINDOW)
        mockBubbleController.stub {
            on { hasStableBubbleForTask(taskInfo.taskId) } doReturn true
        }
        val mockDecoration = mock<DesktopModeWindowDecoration>()
        windowDecorByTaskIdSpy.put(taskInfo.taskId, mockDecoration)

        desktopModeWindowDecorViewModel.onTaskChanging(
            taskInfo,
            SurfaceControl(), /* taskSurface */
            StubTransaction(), /* startT */
            StubTransaction(), /* finishT */
        )

        verify(mockDecoration).close()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX)
    fun testOnFreeformWindowDragEnd_toDesktopModeDisplay_updateBounds() {
        val onTouchListenerCaptor = argumentCaptor<View.OnTouchListener>()
        val decor =
            createOpenTaskDecoration(
                windowingMode = WINDOWING_MODE_FREEFORM,
                onCaptionButtonTouchListener = onTouchListenerCaptor,
            )

        val touchListener = onTouchListenerCaptor.firstValue
        if (touchListener is DesktopModeWindowDecorViewModel.DesktopModeTouchEventListener) {
            val taskInfo = decor.mTaskInfo
            mockDesktopTasksController.stub { on { getActiveDeskId(DEFAULT_DISPLAY) } doReturn 1 }
            mockDesktopTasksController.stub { on { getActiveDeskId(SECOND_DISPLAY) } doReturn 2 }
            val mockInputToken = mock<IBinder>()
            val mockViewRootImpl = mock<ViewRootImpl> { on { inputToken } doReturn mockInputToken }
            val view = mock<View> { on { getViewRootImpl() } doReturn mockViewRootImpl }
            mockTaskPositioner.stub {
                on { onDragPositioningStart(any(), any(), any(), any()) } doReturn INITIAL_BOUNDS
                on { onDragPositioningMove(any(), any(), any()) } doReturn BOUNDS_AFTER_FIRST_MOVE
                on { onDragPositioningEnd(any(), any(), any()) } doReturn
                    BOUNDS_ON_DRAG_END_DESKTOP_ACCEPTED
            }

            touchListener.handleMotionEvent(
                view,
                MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0).apply {
                    displayId = DEFAULT_DISPLAY
                },
            )
            // ACTION_MOVE on desktop-mode display
            touchListener.handleMotionEvent(
                view,
                MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_MOVE, 10f, 10f, 0).apply {
                    displayId = SECOND_DISPLAY
                },
            )

            // Verify point icon does not change and bounds changes
            verify(mockInputManager, never()).setPointerIcon(any(), any(), any(), any(), any())
            verify(mockDesktopTasksController)
                .onDragPositioningMove(
                    eq(taskInfo),
                    any<SurfaceControl>(),
                    eq(SECOND_DISPLAY),
                    eq(10f),
                    eq(10f),
                    eq(BOUNDS_AFTER_FIRST_MOVE),
                )

            // ACTION_UP on desktop-mode display
            touchListener.handleMotionEvent(
                view,
                MotionEvent.obtain(0L, 2L, MotionEvent.ACTION_UP, 20f, 20f, 0).apply {
                    displayId = SECOND_DISPLAY
                },
            )

            // Verify point icon does not change and bounds changes
            verify(mockInputManager, never()).setPointerIcon(any(), any(), any(), any(), any())
            verify(mockDesktopTasksController)
                .onDragPositioningEnd(
                    eq(taskInfo),
                    any<SurfaceControl>(),
                    eq(SECOND_DISPLAY),
                    eq(PointF(20f, 20f)),
                    eq(BOUNDS_ON_DRAG_END_DESKTOP_ACCEPTED),
                    any<Rect>(),
                    any<Rect>(),
                    any<MotionEvent>(),
                )
        } else {
            fail("touchListener was not a DesktopModeTouchEventListener as expected.")
        }
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_BLOCK_NON_DESKTOP_DISPLAY_WINDOW_DRAG_BUGFIX)
    fun testOnFreeformWindowDragMove_toNonDesktopModeDisplay_setsNoDropIconAndKeepsBounds() {
        val onTouchListenerCaptor = argumentCaptor<View.OnTouchListener>()
        val decor =
            createOpenTaskDecoration(
                windowingMode = WINDOWING_MODE_FREEFORM,
                onCaptionButtonTouchListener = onTouchListenerCaptor,
            )

        val touchListener = onTouchListenerCaptor.firstValue
        if (touchListener is DesktopModeWindowDecorViewModel.DesktopModeTouchEventListener) {
            val taskInfo = decor.mTaskInfo
            mockDesktopTasksController.stub { on { getActiveDeskId(DEFAULT_DISPLAY) } doReturn 1 }
            mockDesktopTasksController.stub { on { getActiveDeskId(SECOND_DISPLAY) } doReturn null }
            val mockInputToken = mock<IBinder>()
            val mockViewRootImpl = mock<ViewRootImpl> { on { inputToken } doReturn mockInputToken }
            val view = mock<View> { on { getViewRootImpl() } doReturn mockViewRootImpl }
            mockTaskPositioner.stub {
                on { onDragPositioningStart(any(), any(), any(), any()) } doReturn INITIAL_BOUNDS
                on { onDragPositioningMove(any(), any(), any()) } doReturn BOUNDS_AFTER_FIRST_MOVE
                on { onDragPositioningEnd(any(), any(), any()) } doReturn
                    BOUNDS_IGNORED_ON_NON_DESKTOP
            }

            touchListener.handleMotionEvent(
                view,
                MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 0f, 0f, 0).apply {
                    displayId = DEFAULT_DISPLAY
                },
            )
            // ACTION_MOVE on desktop-mode display
            touchListener.handleMotionEvent(
                view,
                MotionEvent.obtain(0L, 1L, MotionEvent.ACTION_MOVE, 10f, 10f, 0).apply {
                    displayId = DEFAULT_DISPLAY
                },
            )

            // Verify point icon does not change and bounds changes
            verify(mockInputManager, never()).setPointerIcon(any(), any(), any(), any(), any())
            verify(mockDesktopTasksController)
                .onDragPositioningMove(
                    eq(taskInfo),
                    any(),
                    eq(DEFAULT_DISPLAY),
                    eq(10f),
                    eq(10f),
                    eq(BOUNDS_AFTER_FIRST_MOVE),
                )

            // ACTION_MOVE to non-desktop-mode display
            touchListener.handleMotionEvent(
                view,
                MotionEvent.obtain(0L, 2L, MotionEvent.ACTION_MOVE, 20f, 20f, 0).apply {
                    displayId = SECOND_DISPLAY
                },
            )

            // Verify point icon changes and bounds stays the same
            verify(mockInputManager)
                .setPointerIcon(
                    argThat { icon -> icon.type == PointerIcon.TYPE_NO_DROP },
                    eq(SECOND_DISPLAY),
                    any(),
                    eq(0),
                    eq(mockInputToken),
                )
            verify(mockDesktopTasksController)
                .onDragPositioningMove(
                    eq(taskInfo),
                    any(),
                    eq(SECOND_DISPLAY),
                    eq(20f),
                    eq(20f),
                    eq(BOUNDS_AFTER_FIRST_MOVE),
                )

            // ACTION_UP on non-desktop-mode display
            touchListener.handleMotionEvent(
                view,
                MotionEvent.obtain(0L, 2L, MotionEvent.ACTION_UP, 30f, 30f, 0).apply {
                    displayId = SECOND_DISPLAY
                },
            )

            // Verify point icon changes and bounds resets to initial bounds
            verify(mockInputManager)
                .setPointerIcon(
                    argThat { icon -> icon.type == PointerIcon.TYPE_ARROW },
                    eq(SECOND_DISPLAY),
                    any(),
                    eq(0),
                    eq(mockInputToken),
                )
            verify(mockDesktopTasksController)
                .onDragPositioningEnd(
                    eq(taskInfo),
                    any<SurfaceControl>(),
                    eq(SECOND_DISPLAY),
                    eq(PointF(30f, 30f)),
                    eq(INITIAL_BOUNDS),
                    any<Rect>(),
                    any<Rect>(),
                    any<MotionEvent>(),
                )
        } else {
            fail("touchListener was not a DesktopModeTouchEventListener as expected.")
        }
    }

    private fun createOpenTaskDecoration(
        @WindowingMode windowingMode: Int,
        taskSurface: SurfaceControl = SurfaceControl(),
        requestingImmersive: Boolean = false,
        displayId: Int = DEFAULT_DISPLAY,
        windowDecorationActions: KArgumentCaptor<WindowDecorationActions> = argumentCaptor(),
        onCaptionButtonClickListener: KArgumentCaptor<View.OnClickListener> = argumentCaptor(),
        onCaptionButtonTouchListener: KArgumentCaptor<View.OnTouchListener> = argumentCaptor(),
    ): DesktopModeWindowDecoration {
        val decor = setUpMockDecorationForTask(
            createTask(
                windowingMode = windowingMode,
                displayId = displayId,
                requestingImmersive = requestingImmersive
            ),
            windowDecorationActions
        )
        onTaskOpening(decor.mTaskInfo, taskSurface)
        decor.stub { on { leash } doReturn taskSurface }
        verify(decor).setCaptionListeners(
            onCaptionButtonClickListener.capture(), onCaptionButtonTouchListener.capture(),
            any(), any())
        return decor
    }

    private fun setUpMockDecorationsForTasks(vararg tasks: RunningTaskInfo) {
        tasks.forEach { setUpMockDecorationForTask(it) }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        val surfaceView = SurfaceView(mContext)
        val dm = mContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return dm.createVirtualDisplay(
            "testEventReceiversOnMultipleDisplays",
            /*width=*/ 400,
            /*height=*/ 400,
            /*densityDpi=*/ 320,
            surfaceView.holder.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
        )
    }

    private companion object {
        const val SECOND_DISPLAY = 2
        private val BOUNDS_AFTER_FIRST_MOVE = Rect(10, 10, 110, 110)
        private val BOUNDS_IGNORED_ON_NON_DESKTOP = Rect(20, 20, 120, 120)
        private val BOUNDS_ON_DRAG_END_DESKTOP_ACCEPTED = Rect(50, 50, 150, 150)
    }
}
