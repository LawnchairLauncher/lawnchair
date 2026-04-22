/*
 * Copyright (C) 2025 The Android Open Source Project
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
import android.app.WindowConfiguration
import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.view.Display.DEFAULT_DISPLAY
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_TO_BACK
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.WindowContainerTransaction
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayImeController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DesktopImeHandlerTest : ShellTestCase() {

    private val testExecutor = mock<ShellExecutor>()
    private val transitions = mock<Transitions>()
    private val context = mock<Context>()
    private val shellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val tasksController = mock<DesktopTasksController>()
    private val displayLayout = mock<DisplayLayout>()

    private val displayImeController = mock<DisplayImeController>()
    private val displayController = mock<DisplayController>()
    private val focusTransitionObserver = mock<FocusTransitionObserver>()
    private val desktopUserRepositories = mock<DesktopUserRepositories>()
    private val tasksRepository = mock<DesktopRepository>()

    private lateinit var imeHandler: DesktopImeHandler
    private lateinit var shellInit: ShellInit

    @Before
    fun setup() {
        shellInit = spy(ShellInit(testExecutor))

        whenever(tasksController.isAnyDeskActive(any())).thenReturn(true)
        whenever(displayController.getDisplayLayout(any())).thenReturn(displayLayout)
        whenever(desktopUserRepositories.current).thenReturn(tasksRepository)
        whenever(tasksRepository.isActiveTask(any())).thenReturn(true)

        imeHandler =
            DesktopImeHandler(
                tasksController,
                desktopUserRepositories,
                focusTransitionObserver,
                shellTaskOrganizer,
                displayImeController,
                displayController,
                transitions,
                mainExecutor = mock(),
                animExecutor = mock(),
                context,
                shellInit,
            )
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX)
    fun onImeStartPositioning_outsideOfDesktop_noOp() {
        setUpLandscapeDisplay()
        whenever(tasksController.isAnyDeskActive(any())).thenReturn(false)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = false,
            t = mock(),
        )

        // Moves the task up to the top of stable bounds
        verify(transitions, never()).startTransition(eq(TRANSIT_CHANGE), any(), anyOrNull())
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX)
    @DisableFlags(Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS)
    fun onImeStartPositioning_movesLargeTaskToTopAndBack() {
        setUpLandscapeDisplay()
        val wct = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val taskBounds = Rect(0, 400, 500, 1600)
        val expectedBounds =
            Rect(
                taskBounds.left,
                STATUS_BAR_HEIGHT,
                taskBounds.right,
                STATUS_BAR_HEIGHT + taskBounds.height(),
            )
        var freeformTask = createFreeformTask(DEFAULT_DISPLAY, taskBounds)
        freeformTask.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks(any())).thenReturn(arrayListOf(freeformTask))
        whenever(shellTaskOrganizer.getRunningTaskInfo(freeformTask.taskId))
            .thenReturn(freeformTask)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = false,
            t = mock(),
        )

        // Moves the task up to the top of stable bounds
        verify(transitions).startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(expectedBounds)

        // Update the freeform task bounds due to above transition
        freeformTask.configuration.windowConfiguration.setBounds(expectedBounds)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = false,
            isFloating = false,
            t = mock(),
        )

        // Moves the task back to original bounds
        verify(transitions, times(2))
            .startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(taskBounds)
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
        Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
    )
    fun onImeStartPositioning_displayFocusEnabled_movesLargeTaskToTopAndBack() {
        setUpLandscapeDisplay()
        val wct = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val taskBounds = Rect(0, 400, 500, 1600)
        val expectedBounds =
            Rect(
                taskBounds.left,
                STATUS_BAR_HEIGHT,
                taskBounds.right,
                STATUS_BAR_HEIGHT + taskBounds.height(),
            )
        var freeformTask = createFreeformTask(DEFAULT_DISPLAY, taskBounds)
        freeformTask.isFocused = true
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(freeformTask.taskId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(freeformTask.taskId))
            .thenReturn(freeformTask)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = false,
            t = mock(),
        )

        // Moves the task up to the top of stable bounds
        verify(transitions).startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(expectedBounds)

        // Update the freeform task bounds due to above transition
        freeformTask.configuration.windowConfiguration.setBounds(expectedBounds)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = false,
            isFloating = false,
            t = mock(),
        )

        // Moves the task back to original bounds
        verify(transitions, times(2))
            .startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(taskBounds)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX)
    @DisableFlags(Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS)
    fun onImeStartPositioning_movesSmallTaskToTopAndBack() {
        setUpLandscapeDisplay()
        val wct = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val taskBounds = Rect(0, 1000, 500, 1600)
        val expectedBounds =
            Rect(taskBounds.left, IME_HEIGHT - taskBounds.height(), taskBounds.right, IME_HEIGHT)
        var freeformTask = createFreeformTask(DEFAULT_DISPLAY, taskBounds)
        freeformTask.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks(any())).thenReturn(arrayListOf(freeformTask))
        whenever(shellTaskOrganizer.getRunningTaskInfo(freeformTask.taskId))
            .thenReturn(freeformTask)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = false,
            t = mock(),
        )

        // Moves the task up to the top of stable bounds
        verify(transitions).startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(expectedBounds)

        // Update the freeform task bounds due to above transition
        freeformTask.configuration.windowConfiguration.setBounds(expectedBounds)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = false,
            isFloating = false,
            t = mock(),
        )

        // Moves the task back to original bounds
        verify(transitions, times(2))
            .startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(taskBounds)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX)
    fun onImeStartPositioning_floatingIme_noOp() {
        setUpLandscapeDisplay()
        val wct = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val taskBounds = Rect(0, 400, 500, 1600)
        var freeformTask = createFreeformTask(DEFAULT_DISPLAY, taskBounds)
        freeformTask.isFocused = true
        whenever(shellTaskOrganizer.getRunningTasks(any())).thenReturn(arrayListOf(freeformTask))

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = true,
            t = mock(),
        )

        // No transition is started because the IME is floating
        verify(transitions, never()).startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
        Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
    )
    fun onImeStartPositioning_changeTaskPositionManually_doesNotRestorePreImeBounds() {
        setUpLandscapeDisplay()
        val wct = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val taskBounds = Rect(0, 400, 500, 1600)
        val expectedBounds =
            Rect(
                taskBounds.left,
                STATUS_BAR_HEIGHT,
                taskBounds.right,
                STATUS_BAR_HEIGHT + taskBounds.height(),
            )
        var freeformTask = createFreeformTask(DEFAULT_DISPLAY, taskBounds)
        freeformTask.isFocused = true
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(freeformTask.taskId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(freeformTask.taskId))
            .thenReturn(freeformTask)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = false,
            t = mock(),
        )

        // Moves the task up to the top of stable bounds
        verify(transitions).startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(expectedBounds)

        // Update the freeform task bounds to some other bounds that might happen due to user
        // action.
        expectedBounds.offset(100, 100)
        freeformTask.configuration.windowConfiguration.setBounds(expectedBounds)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = false,
            isFloating = false,
            t = mock(),
        )

        // Task is not moved back to original position with a new transition.
        verify(transitions, times(1))
            .startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
        Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
    )
    fun onImeStartPositioning_newTransitionOnTask_doesNotRestorePreImeBounds() {
        setUpLandscapeDisplay()
        val wct = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val taskBounds = Rect(0, 400, 500, 1600)
        val expectedBounds =
            Rect(
                taskBounds.left,
                STATUS_BAR_HEIGHT,
                taskBounds.right,
                STATUS_BAR_HEIGHT + taskBounds.height(),
            )
        var freeformTask = createFreeformTask(DEFAULT_DISPLAY, taskBounds)
        freeformTask.isFocused = true
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(freeformTask.taskId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(freeformTask.taskId))
            .thenReturn(freeformTask)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = false,
            t = mock(),
        )

        // Moves the task up to the top of stable bounds
        verify(transitions).startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
        assertThat(findBoundsChange(wct.value, freeformTask)).isEqualTo(expectedBounds)

        // Create a transition that affects the freeform task we modified previously
        imeHandler.onTransitionReady(
            transition = Mockito.mock(IBinder::class.java),
            info = createToBackTransition(freeformTask),
        )

        // This should be no op.
        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = false,
            isFloating = false,
            t = mock(),
        )

        // Task is not moved back to original position with a new transition.
        verify(transitions, times(1))
            .startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
    }

    @Test
    @EnableFlags(
        Flags.FLAG_ENABLE_DESKTOP_IME_BUGFIX,
        Flags.FLAG_ENABLE_DISPLAY_FOCUS_IN_SHELL_TRANSITIONS,
    )
    fun onImeStartPositioning_taskAboveIme_noOp() {
        setUpLandscapeDisplay()
        val wct = ArgumentCaptor.forClass(WindowContainerTransaction::class.java)
        val taskBounds = Rect(0, 200, 500, 400)
        var freeformTask = createFreeformTask(DEFAULT_DISPLAY, taskBounds)
        freeformTask.isFocused = true
        whenever(focusTransitionObserver.globallyFocusedTaskId).thenReturn(freeformTask.taskId)
        whenever(shellTaskOrganizer.getRunningTaskInfo(freeformTask.taskId))
            .thenReturn(freeformTask)

        imeHandler.onImeStartPositioning(
            DEFAULT_DISPLAY,
            hiddenTop = DISPLAY_DIMENSION_SHORT,
            shownTop = IME_HEIGHT,
            showing = true,
            isFloating = false,
            t = mock(),
        )

        // Does not move the task
        verify(transitions, never()).startTransition(eq(TRANSIT_CHANGE), wct.capture(), anyOrNull())
    }

    private fun findBoundsChange(wct: WindowContainerTransaction, task: RunningTaskInfo): Rect? =
        wct.changes.entries
            .find { (token, change) ->
                token == task.token.asBinder() &&
                    (change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS) != 0
            }
            ?.value
            ?.configuration
            ?.windowConfiguration
            ?.bounds

    private fun setUpLandscapeDisplay() {
        whenever(displayLayout.width()).thenReturn(DISPLAY_DIMENSION_LONG)
        whenever(displayLayout.height()).thenReturn(DISPLAY_DIMENSION_SHORT)
        val stableBounds =
            Rect(
                0,
                STATUS_BAR_HEIGHT,
                DISPLAY_DIMENSION_LONG,
                DISPLAY_DIMENSION_SHORT - TASKBAR_FRAME_HEIGHT,
            )
        whenever(displayLayout.getStableBounds(any())).thenAnswer { i ->
            (i.arguments.first() as Rect).set(stableBounds)
        }
    }

    private fun createToBackTransition(task: RunningTaskInfo?) =
        TransitionInfo(TRANSIT_TO_BACK, /* flags= */ 0).apply {
            addChange(
                Change(mock(), mock()).apply {
                    mode = TRANSIT_TO_BACK
                    parent = null
                    taskInfo = task
                    flags = flags
                }
            )
        }

    private companion object {
        private const val DISPLAY_DIMENSION_SHORT = 1600
        private const val DISPLAY_DIMENSION_LONG = 2560
        private const val TASKBAR_FRAME_HEIGHT = 200
        private const val STATUS_BAR_HEIGHT = 76
        private const val IME_HEIGHT = 840
    }
}
