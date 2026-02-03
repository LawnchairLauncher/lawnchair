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

import android.app.ActivityManager
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.graphics.Point
import android.graphics.Rect
import android.os.IBinder
import android.os.SystemProperties
import android.os.Trace
import android.testing.AndroidTestingRunner
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CHANGE
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_FLAG_IS_RECENTS
import android.view.WindowManager.TRANSIT_NONE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_SLEEP
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.view.WindowManager.TRANSIT_WAKE
import android.window.IWindowContainerToken
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionInfo.FLAG_MOVED_TO_TOP
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.FocusReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.TaskUpdate
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_UNKNOWN
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_MINIMIZE
import java.util.Optional
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

/**
 * Test class for {@link DesktopModeLoggerTransitionObserver}
 *
 * Usage: atest WMShellUnitTests:DesktopModeLoggerTransitionObserverTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopModeLoggerTransitionObserverTest : ShellTestCase() {

    @JvmField
    @Rule
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this)
            .mockStatic(SystemProperties::class.java)
            .mockStatic(Trace::class.java)
            .build()!!

    private val testExecutor = mock<ShellExecutor>()
    private val mockShellInit = mock<ShellInit>()
    private val transitions = mock<Transitions>()
    private val desktopTasksLimiter = mock<DesktopTasksLimiter>()
    private val desktopState = FakeDesktopState()

    private lateinit var transitionObserver: DesktopModeLoggerTransitionObserver
    private lateinit var shellInit: ShellInit
    private lateinit var desktopModeEventLogger: DesktopModeEventLogger

    @Before
    fun setup() {
        desktopState.canEnterDesktopMode = true
        shellInit = spy(ShellInit(testExecutor))
        desktopModeEventLogger = mock<DesktopModeEventLogger>()

        transitionObserver =
            DesktopModeLoggerTransitionObserver(
                mockShellInit,
                transitions,
                desktopModeEventLogger,
                Optional.of(desktopTasksLimiter),
                desktopState,
            )
        val initRunnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
        verify(mockShellInit)
            .addInitCallback(initRunnableCaptor.capture(), same(transitionObserver))
        initRunnableCaptor.value.run()
        // verify this initialisation interaction to leave the desktopmodeEventLogger mock in a
        // consistent state with no outstanding interactions when test cases start executing.
        verify(desktopModeEventLogger).logTaskInfoStateInit()
    }

    @Test
    fun testInitialiseVisibleTasksSystemProperty() {
        ExtendedMockito.verify {
            SystemProperties.set(
                eq(DesktopModeLoggerTransitionObserver.VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY),
                eq(
                    DesktopModeLoggerTransitionObserver
                        .VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY_DEFAULT_VALUE
                ),
            )
        }
    }

    @Test
    fun testRegistersObserverAtInit() {
        verify(transitions).registerObserver(same(transitionObserver))
    }

    @Test
    fun transitOpen_notFreeformWindow_doesNotLogTaskAddedOrSessionEnter() {
        val change = createChange(TRANSIT_OPEN, createTaskInfo(WINDOWING_MODE_FULLSCREEN))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, never()).logSessionEnter(any())
        verify(desktopModeEventLogger, never()).logTaskAdded(any())
    }

    @Test
    fun transitOpen_logTaskAddedAndEnterReasonAppFreeformIntent() {
        val change = createChange(TRANSIT_OPEN, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.APP_FREEFORM_INTENT,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1, focusReason = FocusReason.UNKNOWN),
        )
    }

    @Test
    fun transitEndDragToDesktop_logTaskAddedAndEnterReasonAppHandleDrag() {
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        // task change is finalised when drag ends
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP, 0)
                .addChange(change)
                .build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.APP_HANDLE_DRAG,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitEnterDesktopByButtonTap_logTaskAddedAndEnterReasonButtonTap() {
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON, 0)
                .addChange(change)
                .build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.APP_HANDLE_MENU_BUTTON,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitEnterDesktopFromAppFromOverview_logTaskAddedAndEnterReasonAppFromOverview() {
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW, 0)
                .addChange(change)
                .build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.APP_FROM_OVERVIEW,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitEnterDesktopFromKeyboardShortcut_logTaskAddedAndEnterReasonKeyboardShortcut() {
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT, 0)
                .addChange(change)
                .build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.KEYBOARD_SHORTCUT_ENTER,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitToFront_logTaskAddedAndEnterReasonOverview() {
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_TO_FRONT, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.OVERVIEW,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitToFront_previousTransitionExitToOverview_logTaskAddedAndEnterReasonOverview() {
        // previous exit to overview transition
        // add a freeform task
        val previousTaskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM)
        transitionObserver.addTaskInfosToCachedMap(previousTaskInfo)
        transitionObserver.isSessionActive = true
        val previousTransitionInfo =
            TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
                .addChange(createChange(TRANSIT_TO_BACK, previousTaskInfo))
                .build()

        callOnTransitionReady(previousTransitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.RETURN_HOME_OR_OVERVIEW, DEFAULT_TASK_UPDATE)

        // Enter desktop mode from cancelled recents has no transition. Enter is detected on the
        // next transition involving freeform windows

        // TRANSIT_TO_FRONT
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_TO_FRONT, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.OVERVIEW,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitChange_previousTransitionExitToOverview_logTaskAddedAndEnterReasonOverview() {
        // previous exit to overview transition
        // add a freeform task
        val previousTaskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM)
        transitionObserver.addTaskInfosToCachedMap(previousTaskInfo)
        transitionObserver.isSessionActive = true
        val previousTransitionInfo =
            TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
                .addChange(createChange(TRANSIT_TO_BACK, previousTaskInfo))
                .build()

        callOnTransitionReady(previousTransitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.RETURN_HOME_OR_OVERVIEW, DEFAULT_TASK_UPDATE)

        // Enter desktop mode from cancelled recents has no transition. Enter is detected on the
        // next transition involving freeform windows

        // TRANSIT_CHANGE
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_CHANGE, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.OVERVIEW,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitOpen_previousTransitionExitToOverview_logTaskAddedAndEnterReasonOverview() {
        // previous exit to overview transition
        // add a freeform task
        val previousTaskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM)
        transitionObserver.addTaskInfosToCachedMap(previousTaskInfo)
        transitionObserver.isSessionActive = true
        val previousTransitionInfo =
            TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
                .addChange(createChange(TRANSIT_TO_BACK, previousTaskInfo))
                .build()

        callOnTransitionReady(previousTransitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.RETURN_HOME_OR_OVERVIEW, DEFAULT_TASK_UPDATE)

        // Enter desktop mode from cancelled recents has no transition. Enter is detected on the
        // next transition involving freeform windows

        // TRANSIT_OPEN
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.OVERVIEW,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    @Suppress("ktlint:standard:max-line-length")
    fun transitEnterDesktopFromAppFromOverview_previousTransitionExitToOverview_logTaskAddedAndEnterReasonAppFromOverview() {
        // Tests for AppFromOverview precedence in compared to cancelled Overview

        // previous exit to overview transition
        // add a freeform task
        val previousTaskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM)
        transitionObserver.addTaskInfosToCachedMap(previousTaskInfo)
        transitionObserver.isSessionActive = true
        val previousTransitionInfo =
            TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
                .addChange(createChange(TRANSIT_TO_BACK, previousTaskInfo))
                .build()

        callOnTransitionReady(previousTransitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.RETURN_HOME_OR_OVERVIEW, DEFAULT_TASK_UPDATE)

        // TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW, 0)
                .addChange(change)
                .build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.APP_FROM_OVERVIEW,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitEnterDesktopFromUnknown_logTaskAddedAndEnterReasonUnknown() {
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_UNKNOWN, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.UNKNOWN_ENTER,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitWake_logTaskAddedAndEnterReasonScreenOn() {
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_WAKE, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.SCREEN_ON,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitBack_previousExitReasonScreenOff_logTaskAddedAndEnterReasonScreenOn() {
        val freeformTask = createTaskInfo(WINDOWING_MODE_FREEFORM)
        // Previous Exit reason recorded as Screen Off
        transitionObserver.addTaskInfosToCachedMap(freeformTask)
        transitionObserver.isSessionActive = true
        callOnTransitionReady(TransitionInfoBuilder(TRANSIT_SLEEP).build())
        verifyTaskRemovedAndExitLogging(ExitReason.SCREEN_OFF, DEFAULT_TASK_UPDATE)
        // Enter desktop through back transition, this happens when user enters after dismissing
        // keyguard
        val change = createChange(TRANSIT_TO_FRONT, freeformTask)
        val transitionInfo = TransitionInfoBuilder(TRANSIT_TO_BACK, 0).addChange(change).build()

        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.SCREEN_ON,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitEndDragToDesktop_previousExitReasonScreenOff_logTaskAddedAndEnterReasonAppDrag() {
        val freeformTask = createTaskInfo(WINDOWING_MODE_FREEFORM)
        // Previous Exit reason recorded as Screen Off
        transitionObserver.addTaskInfosToCachedMap(freeformTask)
        transitionObserver.isSessionActive = true
        callOnTransitionReady(TransitionInfoBuilder(TRANSIT_SLEEP).build())
        verifyTaskRemovedAndExitLogging(ExitReason.SCREEN_OFF, DEFAULT_TASK_UPDATE)

        // Enter desktop through app handle drag. This represents cases where instead of moving to
        // desktop right after turning the screen on, we move to fullscreen then move another task
        // to desktop
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP, 0)
                .addChange(createChange(TRANSIT_TO_FRONT, freeformTask))
                .build()
        callOnTransitionReady(transitionInfo)

        verifyTaskAddedAndEnterLogging(
            EnterReason.APP_HANDLE_DRAG,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun transitSleep_logTaskRemovedAndExitReasonScreenOff() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        val transitionInfo = TransitionInfoBuilder(TRANSIT_SLEEP).build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.SCREEN_OFF, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitExitDesktopTaskDrag_logTaskRemovedAndExitReasonDragToExit() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // window mode changing from FREEFORM to FULLSCREEN
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FULLSCREEN))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG).addChange(change).build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.DRAG_TO_EXIT, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitExitDesktopAppHandleButton_logTaskRemovedAndExitReasonButton() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // window mode changing from FREEFORM to FULLSCREEN
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FULLSCREEN))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON)
                .addChange(change)
                .build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.APP_HANDLE_MENU_BUTTON_EXIT, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitExitDesktopUsingKeyboard_logTaskRemovedAndExitReasonKeyboard() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // window mode changing from FREEFORM to FULLSCREEN
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FULLSCREEN))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT)
                .addChange(change)
                .build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.KEYBOARD_SHORTCUT_EXIT, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitExitBackGesture_logTaskRemovedAndExitReasonTaskMovedToBack() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // task moved to back
        val change = createChange(TRANSIT_TO_BACK, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_TO_BACK).addChange(change).build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.TASK_MOVED_TO_BACK, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitExitDesktopUnknown_logTaskRemovedAndExitReasonUnknown() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // window mode changing from FREEFORM to FULLSCREEN
        val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(WINDOWING_MODE_FULLSCREEN))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN).addChange(change).build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.UNKNOWN_EXIT, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitToFrontWithFlagRecents_logTaskRemovedAndExitReasonOverview() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // recents transition
        val change = createChange(TRANSIT_TO_BACK, createTaskInfo(WINDOWING_MODE_FREEFORM))
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
                .addChange(change)
                .build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.RETURN_HOME_OR_OVERVIEW, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitClose_logTaskRemovedAndExitReasonTaskFinished() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // task closing
        val change = createChange(TRANSIT_CLOSE, createTaskInfo(WINDOWING_MODE_FULLSCREEN))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_CLOSE).addChange(change).build()
        callOnTransitionReady(transitionInfo)

        verifyTaskRemovedAndExitLogging(ExitReason.TASK_FINISHED, DEFAULT_TASK_UPDATE)
    }

    @Test
    fun transitMinimize_logExitReasongMinimized() {
        // add a freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // minimize the task
        val change = createChange(TRANSIT_MINIMIZE, createTaskInfo(WINDOWING_MODE_FULLSCREEN))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_MINIMIZE).addChange(change).build()
        callOnTransitionReady(transitionInfo)

        assertFalse(transitionObserver.isSessionActive)
        verify(desktopModeEventLogger, times(1)).logSessionExit(eq(ExitReason.TASK_MINIMIZED))
        verify(desktopModeEventLogger, times(1))
            .logTaskRemoved(
                eq(DEFAULT_TASK_UPDATE.copy(minimizeReason = MinimizeReason.MINIMIZE_BUTTON))
            )
        verifyNoMoreInteractions(desktopModeEventLogger)
    }

    @Test
    fun sessionExitByRecents_cancelledAnimation_sessionRestored() {
        // add a freeform task to an existing session
        val taskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM)
        transitionObserver.addTaskInfosToCachedMap(taskInfo)
        transitionObserver.isSessionActive = true

        // recents transition sent freeform window to back
        val change = createChange(TRANSIT_TO_BACK, taskInfo)
        val transitionInfo1 =
            TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
                .addChange(change)
                .build()
        callOnTransitionReady(transitionInfo1)

        verifyTaskRemovedAndExitLogging(ExitReason.RETURN_HOME_OR_OVERVIEW, DEFAULT_TASK_UPDATE)

        val transitionInfo2 = TransitionInfoBuilder(TRANSIT_NONE).build()
        callOnTransitionReady(transitionInfo2)

        verifyTaskAddedAndEnterLogging(
            EnterReason.OVERVIEW,
            DEFAULT_TASK_UPDATE.copy(visibleTaskCount = 1),
        )
    }

    @Test
    fun sessionAlreadyStarted_newFreeformTaskAdded_logsTaskAdded() {
        // add an existing freeform task
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.isSessionActive = true

        // new freeform task added
        val change = createChange(TRANSIT_OPEN, createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()
        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskAdded(
                eq(
                    DEFAULT_TASK_UPDATE.copy(
                        instanceId = 2,
                        visibleTaskCount = 2,
                        focusReason = FocusReason.UNKNOWN,
                    )
                )
            )
        verify(desktopModeEventLogger, never()).logSessionEnter(any())
    }

    @Test
    fun sessionAlreadyStarted_taskPositionChanged_logsTaskUpdate() {
        // add an existing freeform task
        val taskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM)
        transitionObserver.addTaskInfosToCachedMap(taskInfo)
        transitionObserver.isSessionActive = true

        // task position changed
        val newTaskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM, taskX = DEFAULT_TASK_X + 100)
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_CHANGE, 0)
                .addChange(createChange(TRANSIT_CHANGE, newTaskInfo))
                .build()
        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskInfoChanged(
                eq(DEFAULT_TASK_UPDATE.copy(taskX = DEFAULT_TASK_X + 100, visibleTaskCount = 1))
            )
        verifyNoMoreInteractions(desktopModeEventLogger)
    }

    @Test
    fun sessionAlreadyStarted_taskResized_logsTaskUpdate() {
        // add an existing freeform task
        val taskInfo = createTaskInfo(WINDOWING_MODE_FREEFORM)
        transitionObserver.addTaskInfosToCachedMap(taskInfo)
        transitionObserver.isSessionActive = true

        // task resized
        val newTaskInfo =
            createTaskInfo(
                WINDOWING_MODE_FREEFORM,
                taskWidth = DEFAULT_TASK_WIDTH + 100,
                taskHeight = DEFAULT_TASK_HEIGHT - 100,
            )
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_CHANGE, 0)
                .addChange(createChange(TRANSIT_CHANGE, newTaskInfo))
                .build()
        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskInfoChanged(
                eq(
                    DEFAULT_TASK_UPDATE.copy(
                        taskWidth = DEFAULT_TASK_WIDTH + 100,
                        taskHeight = DEFAULT_TASK_HEIGHT - 100,
                        visibleTaskCount = 1,
                    )
                )
            )
        verifyNoMoreInteractions(desktopModeEventLogger)
    }

    @Test
    fun sessionAlreadyStarted_taskFocusChanged_logsTaskUpdate() {
        val taskInfo1 = createTaskInfo(WINDOWING_MODE_FREEFORM, id = 1)
        val taskInfo2 = createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2)
        transitionObserver.addTaskInfosToCachedMap(taskInfo1)
        transitionObserver.addTaskInfosToCachedMap(taskInfo2)
        transitionObserver.isSessionActive = true
        transitionObserver.setFocusedTaskForTesting(taskInfo1)

        val task2FocusedChange = createChange(TRANSIT_CHANGE, taskInfo2)
        task2FocusedChange.flags = FLAG_MOVED_TO_TOP
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_CHANGE, 0).addChange(task2FocusedChange).build()
        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskInfoChanged(
                eq(
                    DEFAULT_TASK_UPDATE.copy(
                        instanceId = 2,
                        visibleTaskCount = 2,
                        focusReason = FocusReason.UNKNOWN,
                    )
                )
            )
        verifyNoMoreInteractions(desktopModeEventLogger)
    }

    @Test
    fun sessionAlreadyStarted_multipleTasksUpdated_logsTaskUpdateForCorrectTask() {
        // add 2 existing freeform task
        val taskInfo1 = createTaskInfo(WINDOWING_MODE_FREEFORM)
        val taskInfo2 = createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2)
        transitionObserver.addTaskInfosToCachedMap(taskInfo1)
        transitionObserver.addTaskInfosToCachedMap(taskInfo2)
        transitionObserver.isSessionActive = true

        // task 1 position update
        val newTaskInfo1 = createTaskInfo(WINDOWING_MODE_FREEFORM, taskX = DEFAULT_TASK_X + 100)
        val transitionInfo1 =
            TransitionInfoBuilder(TRANSIT_CHANGE, 0)
                .addChange(createChange(TRANSIT_CHANGE, newTaskInfo1))
                .build()
        callOnTransitionReady(transitionInfo1)

        verify(desktopModeEventLogger, times(1))
            .logTaskInfoChanged(
                eq(DEFAULT_TASK_UPDATE.copy(taskX = DEFAULT_TASK_X + 100, visibleTaskCount = 2))
            )
        verifyNoMoreInteractions(desktopModeEventLogger)

        // task 2 resize
        val newTaskInfo2 =
            createTaskInfo(
                WINDOWING_MODE_FREEFORM,
                id = 2,
                taskWidth = DEFAULT_TASK_WIDTH + 100,
                taskHeight = DEFAULT_TASK_HEIGHT - 100,
            )
        val transitionInfo2 =
            TransitionInfoBuilder(TRANSIT_CHANGE, 0)
                .addChange(createChange(TRANSIT_CHANGE, newTaskInfo2))
                .build()

        callOnTransitionReady(transitionInfo2)

        verify(desktopModeEventLogger, times(1))
            .logTaskInfoChanged(
                eq(
                    DEFAULT_TASK_UPDATE.copy(
                        instanceId = 2,
                        taskWidth = DEFAULT_TASK_WIDTH + 100,
                        taskHeight = DEFAULT_TASK_HEIGHT - 100,
                        visibleTaskCount = 2,
                    )
                )
            )
        verifyNoMoreInteractions(desktopModeEventLogger)
    }

    @Test
    fun sessionAlreadyStarted_freeformTaskRemoved_logsTaskRemoved() {
        // add two existing freeform tasks
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM))
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2))
        transitionObserver.isSessionActive = true

        // new freeform task closed
        val change = createChange(TRANSIT_CLOSE, createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2))
        val transitionInfo = TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change).build()
        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskRemoved(eq(DEFAULT_TASK_UPDATE.copy(instanceId = 2, visibleTaskCount = 1)))
        verify(desktopModeEventLogger, never()).logSessionExit(any())
    }

    @Test
    fun onTransitionReady_taskIsBeingMinimized_logsTaskMinimized() {
        transitionObserver.isSessionActive = true
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM, id = 1))
        val taskInfo2 = createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2)
        transitionObserver.addTaskInfosToCachedMap(taskInfo2)
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_TO_BACK, 0)
                .addChange(createChange(TRANSIT_TO_BACK, taskInfo2))
                .build()
        `when`(desktopTasksLimiter.getMinimizingTask(any()))
            .thenReturn(
                DesktopTasksLimiter.TaskDetails(
                    taskInfo2.displayId,
                    taskInfo2.taskId,
                    minimizeReason = MinimizeReason.TASK_LIMIT,
                )
            )

        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskRemoved(
                eq(
                    DEFAULT_TASK_UPDATE.copy(
                        instanceId = 2,
                        visibleTaskCount = 1,
                        minimizeReason = MinimizeReason.TASK_LIMIT,
                    )
                )
            )
    }

    @Test
    fun onTransitionReady_taskIsBeingUnminimized_logsTaskUnminimized() {
        transitionObserver.isSessionActive = true
        transitionObserver.addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM, id = 1))
        val taskInfo2 = createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2)
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_TO_FRONT, /* flags= */ 0)
                .addChange(createChange(TRANSIT_TO_FRONT, taskInfo2))
                .build()
        `when`(desktopTasksLimiter.getUnminimizingTask(any()))
            .thenReturn(
                DesktopTasksLimiter.TaskDetails(
                    taskInfo2.displayId,
                    taskInfo2.taskId,
                    unminimizeReason = UnminimizeReason.TASKBAR_MANAGE_WINDOW,
                )
            )

        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskAdded(
                eq(
                    DEFAULT_TASK_UPDATE.copy(
                        instanceId = 2,
                        visibleTaskCount = 2,
                        unminimizeReason = UnminimizeReason.TASKBAR_MANAGE_WINDOW,
                    )
                )
            )
    }

    @Test
    fun onTransitionReady_taskLimitMinimizeTransitionType_logsTaskMinimized() {
        val taskInfo2 = createTaskInfo(WINDOWING_MODE_FREEFORM, id = 2)
        transitionObserver.apply {
            isSessionActive = true
            addTaskInfosToCachedMap(createTaskInfo(WINDOWING_MODE_FREEFORM, id = 1))
            addTaskInfosToCachedMap(taskInfo2)
        }
        val transitionInfo =
            TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_TASK_LIMIT_MINIMIZE, /* flags= */ 0)
                .addChange(createChange(TRANSIT_TO_BACK, taskInfo2))
                .build()

        callOnTransitionReady(transitionInfo)

        verify(desktopModeEventLogger, times(1))
            .logTaskRemoved(
                eq(
                    DEFAULT_TASK_UPDATE.copy(
                        instanceId = 2,
                        visibleTaskCount = 1,
                        minimizeReason = MinimizeReason.TASK_LIMIT,
                    )
                )
            )
    }

    /** Simulate calling the onTransitionReady() method */
    private fun callOnTransitionReady(transitionInfo: TransitionInfo) {
        val transition = mock<IBinder>()
        val startT = mock<SurfaceControl.Transaction>()
        val finishT = mock<SurfaceControl.Transaction>()

        transitionObserver.onTransitionReady(transition, transitionInfo, startT, finishT)
    }

    private fun verifyTaskAddedAndEnterLogging(enterReason: EnterReason, taskUpdate: TaskUpdate) {
        assertTrue(transitionObserver.isSessionActive)
        verify(desktopModeEventLogger, times(1)).logSessionEnter(eq(enterReason))
        verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(taskUpdate))
        ExtendedMockito.verify {
            Trace.setCounter(
                eq(Trace.TRACE_TAG_WINDOW_MANAGER),
                eq(DesktopModeLoggerTransitionObserver.VISIBLE_TASKS_COUNTER_NAME),
                eq(taskUpdate.visibleTaskCount.toLong()),
            )
        }
        ExtendedMockito.verify {
            SystemProperties.set(
                eq(DesktopModeLoggerTransitionObserver.VISIBLE_TASKS_COUNTER_SYSTEM_PROPERTY),
                eq(taskUpdate.visibleTaskCount.toString()),
            )
        }
        verifyNoMoreInteractions(desktopModeEventLogger)
    }

    private fun verifyTaskRemovedAndExitLogging(exitReason: ExitReason, taskUpdate: TaskUpdate) {
        assertFalse(transitionObserver.isSessionActive)
        verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(taskUpdate))
        verify(desktopModeEventLogger, times(1)).logSessionExit(eq(exitReason))
        verifyNoMoreInteractions(desktopModeEventLogger)
    }

    private companion object {
        const val DEFAULT_TASK_ID = 1
        const val DEFAULT_TASK_UID = 2
        const val DEFAULT_TASK_HEIGHT = 100
        const val DEFAULT_TASK_WIDTH = 200
        const val DEFAULT_TASK_X = 30
        const val DEFAULT_TASK_Y = 70
        const val DEFAULT_VISIBLE_TASK_COUNT = 0
        val DEFAULT_TASK_UPDATE =
            TaskUpdate(
                DEFAULT_TASK_ID,
                DEFAULT_TASK_UID,
                DEFAULT_TASK_HEIGHT,
                DEFAULT_TASK_WIDTH,
                DEFAULT_TASK_X,
                DEFAULT_TASK_Y,
                visibleTaskCount = DEFAULT_VISIBLE_TASK_COUNT,
            )

        fun createTaskInfo(
            windowMode: Int,
            id: Int = DEFAULT_TASK_ID,
            uid: Int = DEFAULT_TASK_UID,
            taskHeight: Int = DEFAULT_TASK_HEIGHT,
            taskWidth: Int = DEFAULT_TASK_WIDTH,
            taskX: Int = DEFAULT_TASK_X,
            taskY: Int = DEFAULT_TASK_Y,
        ) =
            ActivityManager.RunningTaskInfo().apply {
                taskId = id
                effectiveUid = uid
                configuration.windowConfiguration.apply {
                    windowingMode = windowMode
                    positionInParent = Point(taskX, taskY)
                    bounds.set(Rect(taskX, taskY, taskX + taskWidth, taskY + taskHeight))
                }
            }

        fun createChange(mode: Int, taskInfo: ActivityManager.RunningTaskInfo): Change {
            val change =
                Change(WindowContainerToken(mock<IWindowContainerToken>()), mock<SurfaceControl>())
            change.mode = mode
            change.taskInfo = taskInfo
            return change
        }
    }
}
