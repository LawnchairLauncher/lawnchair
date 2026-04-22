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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.RunningTaskInfo
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.KeyGestureEvent
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import android.view.KeyEvent
import android.window.DisplayAreaInfo
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS
import com.android.window.flags.Flags.FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT
import com.android.window.flags.Flags.FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS
import com.android.wm.shell.MockToken
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.desktopmode.common.ToggleTaskSizeInteraction
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.windowdecor.DesktopModeWindowDecorViewModel
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

/**
 * Test class for [DesktopModeKeyGestureHandler]
 *
 * Usage: atest WMShellUnitTests:DesktopModeKeyGestureHandlerTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
@ExperimentalCoroutinesApi
@EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
class DesktopModeKeyGestureHandlerTest : ShellTestCase() {

    private val rootTaskDisplayAreaOrganizer = mock<RootTaskDisplayAreaOrganizer>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val focusTransitionObserver = mock<FocusTransitionObserver>()
    private val testExecutor = TestShellExecutor()
    private val inputManager = mock<InputManager>()
    private val displayController = mock<DisplayController>()
    private val displayLayout = mock<DisplayLayout>()
    private val desktopModeWindowDecorViewModel = mock<DesktopModeWindowDecorViewModel>()
    private val desktopTasksController = mock<DesktopTasksController>()

    private lateinit var desktopModeKeyGestureHandler: DesktopModeKeyGestureHandler
    private lateinit var keyGestureEventHandler: KeyGestureEventHandler
    private lateinit var mockitoSession: StaticMockitoSession
    private lateinit var testScope: CoroutineScope
    private lateinit var shellInit: ShellInit
    private lateinit var desktopUserRepositories: DesktopUserRepositories

    // Mock running tasks are registered here so we can get the list from mock shell task organizer
    private val runningTasks = mutableListOf<RunningTaskInfo>()

    private val repository: DesktopRepository
        get() = desktopUserRepositories.current

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        mockitoSession = mockitoSession().strictness(Strictness.LENIENT).startMocking()

        testScope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        shellInit = spy(ShellInit(testExecutor))

        whenever(shellTaskOrganizer.getRunningTasks(anyInt())).thenAnswer { runningTasks }
        whenever(displayController.getDisplayLayout(anyInt())).thenReturn(displayLayout)
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(STABLE_BOUNDS)
        }
        val tda = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
        tda.configuration.windowConfiguration.windowingMode = WINDOWING_MODE_FULLSCREEN
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)).thenReturn(tda)
        desktopUserRepositories =
            DesktopUserRepositories(
                ShellInit(TestShellExecutor()),
                /* shellController= */ mock(),
                /* persistentRepository= */ mock(),
                /* repositoryInitializer= */ mock(),
                testScope,
                /* userManager= */ mock(),
                FakeDesktopState(),
                FakeDesktopConfig(),
            )
        repository.addDesk(displayId = DEFAULT_DISPLAY, deskId = 10)
        repository.setActiveDesk(displayId = DEFAULT_DISPLAY, deskId = 10)
        repository.addDesk(displayId = SECOND_DISPLAY, deskId = 11)
        repository.setActiveDesk(displayId = SECOND_DISPLAY, deskId = 11)

        doAnswer {
                keyGestureEventHandler = (it.arguments[1] as KeyGestureEventHandler)
                null
            }
            .whenever(inputManager)
            .registerKeyGestureEventHandler(any(), any())
        shellInit.init()

        desktopModeKeyGestureHandler =
            DesktopModeKeyGestureHandler(
                context,
                Optional.of(desktopModeWindowDecorViewModel),
                Optional.of(desktopTasksController),
                desktopUserRepositories,
                inputManager,
                shellTaskOrganizer,
                focusTransitionObserver,
                testExecutor,
                displayController,
            )
    }

    @After
    fun tearDown() {
        mockitoSession.finishMocking()

        runningTasks.clear()
        testScope.cancel()
        testExecutor.flushAll()
    }

    @Test
    @EnableFlags(
        FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
        FLAG_ENABLE_MOVE_TO_NEXT_DISPLAY_SHORTCUT,
    )
    fun keyGestureMoveToNextDisplay_shouldMoveToNextDisplay() {
        // Set up two display ids
        whenever(rootTaskDisplayAreaOrganizer.displayIds)
            .thenReturn(intArrayOf(DEFAULT_DISPLAY, SECOND_DISPLAY))
        // Create a mock for the target display area: default display
        val defaultDisplayArea = DisplayAreaInfo(MockToken().token(), DEFAULT_DISPLAY, 0)
        whenever(rootTaskDisplayAreaOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY))
            .thenReturn(defaultDisplayArea)
        // Setup a focused task on secondary display, which is expected to move to default display
        val task = setUpDesktopTask(displayId = SECOND_DISPLAY)
        task.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks()).thenReturn(arrayListOf(task))
        whenever(focusTransitionObserver.hasGlobalFocus(eq(task))).thenReturn(true)

        val event =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_MOVE_TO_NEXT_DISPLAY)
                .setDisplayId(SECOND_DISPLAY)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_D))
                .setModifierState(KeyEvent.META_META_ON or KeyEvent.META_CTRL_ON)
                .build()
        keyGestureEventHandler.handleKeyGestureEvent(event, null)
        testExecutor.flushAll()

        verify(desktopTasksController).moveToNextDesktopDisplay(task.taskId)
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS)
    fun keyGestureSnapLeft_shouldSnapResizeTaskToLeft() {
        val task = setUpDesktopTask()
        task.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks()).thenReturn(arrayListOf(task))
        whenever(focusTransitionObserver.hasGlobalFocus(eq(task))).thenReturn(true)

        val event =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_LEFT_FREEFORM_WINDOW)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_LEFT_BRACKET))
                .setModifierState(KeyEvent.META_META_ON)
                .build()
        keyGestureEventHandler.handleKeyGestureEvent(event, null)
        testExecutor.flushAll()

        verify(desktopModeWindowDecorViewModel)
            .onSnapResize(
                task.taskId,
                true,
                DesktopModeEventLogger.Companion.InputMethod.KEYBOARD,
                /* fromMenu= */ false,
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS)
    fun keyGestureSnapRight_shouldSnapResizeTaskToRight() {
        val task = setUpDesktopTask()
        task.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks()).thenReturn(arrayListOf(task))
        whenever(focusTransitionObserver.hasGlobalFocus(eq(task))).thenReturn(true)

        val event =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_SNAP_RIGHT_FREEFORM_WINDOW)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_RIGHT_BRACKET))
                .setModifierState(KeyEvent.META_META_ON)
                .build()
        keyGestureEventHandler.handleKeyGestureEvent(event, null)
        testExecutor.flushAll()

        verify(desktopModeWindowDecorViewModel)
            .onSnapResize(
                task.taskId,
                false,
                DesktopModeEventLogger.Companion.InputMethod.KEYBOARD,
                /* fromMenu= */ false,
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS)
    fun keyGestureToggleFreeformWindowSize_shouldToggleTaskSize() {
        val task = setUpDesktopTask()
        task.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks()).thenReturn(arrayListOf(task))
        whenever(focusTransitionObserver.hasGlobalFocus(eq(task))).thenReturn(true)

        val event =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_TOGGLE_MAXIMIZE_FREEFORM_WINDOW)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_EQUALS))
                .setModifierState(KeyEvent.META_META_ON)
                .build()
        keyGestureEventHandler.handleKeyGestureEvent(event, null)
        testExecutor.flushAll()

        verify(desktopTasksController)
            .toggleDesktopTaskSize(
                task,
                ToggleTaskSizeInteraction(
                    isMaximized = isTaskMaximized(task, displayController),
                    source = ToggleTaskSizeInteraction.Source.KEYBOARD_SHORTCUT,
                    inputMethod = DesktopModeEventLogger.Companion.InputMethod.KEYBOARD,
                ),
            )
    }

    @Test
    @EnableFlags(FLAG_ENABLE_TASK_RESIZING_KEYBOARD_SHORTCUTS)
    fun keyGestureMinimizeFreeformWindow_shouldMinimizeTask() {
        val task = setUpDesktopTask()
        task.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks()).thenReturn(arrayListOf(task))
        whenever(focusTransitionObserver.hasGlobalFocus(eq(task))).thenReturn(true)

        val event =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_MINIMIZE_FREEFORM_WINDOW)
                .setKeycodes(intArrayOf(KeyEvent.KEYCODE_MINUS))
                .setModifierState(KeyEvent.META_META_ON)
                .build()
        keyGestureEventHandler.handleKeyGestureEvent(event, null)
        testExecutor.flushAll()

        verify(desktopTasksController).minimizeTask(task, MinimizeReason.KEY_GESTURE)
    }

    @Test
    fun keyGestureSwitchToPreviousDesk_activatesDesk() {
        val displayId = 2
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(displayId)
        val event =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_PREVIOUS_DESK)
                .build()

        keyGestureEventHandler.handleKeyGestureEvent(event, null)
        testExecutor.flushAll()

        verify(desktopTasksController).activatePreviousDesk(displayId)
    }

    @Test
    fun keyGestureSwitchToNextDesk_activatesDesk() {
        val displayId = 2
        whenever(focusTransitionObserver.globallyFocusedDisplayId).thenReturn(displayId)
        val event =
            KeyGestureEvent.Builder()
                .setKeyGestureType(KeyGestureEvent.KEY_GESTURE_TYPE_SWITCH_TO_NEXT_DESK)
                .build()

        keyGestureEventHandler.handleKeyGestureEvent(event, null)
        testExecutor.flushAll()

        verify(desktopTasksController).activateNextDesk(displayId)
    }

    private fun setUpDesktopTask(
        displayId: Int = DEFAULT_DISPLAY,
        bounds: Rect? = null,
    ): RunningTaskInfo {
        val task = createFreeformTask(displayId, bounds)
        val activityInfo = ActivityInfo()
        task.topActivityInfo = activityInfo
        whenever(shellTaskOrganizer.getRunningTaskInfo(task.taskId)).thenReturn(task)
        runningTasks.add(task)
        repository.addTask(
            displayId = displayId,
            taskId = task.taskId,
            isVisible = task.isVisible,
            taskBounds = task.configuration.windowConfiguration.getBounds(),
        )
        return task
    }

    private companion object {
        const val SECOND_DISPLAY = 2
        val STABLE_BOUNDS = Rect(0, 0, 1000, 1000)
    }
}
