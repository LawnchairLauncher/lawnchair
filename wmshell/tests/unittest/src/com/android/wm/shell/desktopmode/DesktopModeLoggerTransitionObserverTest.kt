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
import android.content.Context
import android.os.IBinder
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
import android.window.WindowContainerToken
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_ENTER_DESKTOP_FROM_UNKNOWN
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN
import com.android.wm.shell.shared.DesktopModeStatus
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.TransitionInfoBuilder
import com.android.wm.shell.transition.Transitions
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyZeroInteractions

/**
 * Test class for {@link DesktopModeLoggerTransitionObserver}
 *
 * Usage: atest WMShellUnitTests:DesktopModeLoggerTransitionObserverTest
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class DesktopModeLoggerTransitionObserverTest {

  @JvmField
  @Rule
  val extendedMockitoRule =
      ExtendedMockitoRule.Builder(this)
          .mockStatic(DesktopModeEventLogger::class.java)
          .mockStatic(DesktopModeStatus::class.java)
          .build()!!

  @Mock lateinit var testExecutor: ShellExecutor
  @Mock private lateinit var mockShellInit: ShellInit
  @Mock private lateinit var transitions: Transitions
  @Mock private lateinit var context: Context

  private lateinit var transitionObserver: DesktopModeLoggerTransitionObserver
  private lateinit var shellInit: ShellInit
  private lateinit var desktopModeEventLogger: DesktopModeEventLogger

  @Before
  fun setup() {
    doReturn(true).`when` { DesktopModeStatus.canEnterDesktopMode(any()) }
    shellInit = Mockito.spy(ShellInit(testExecutor))
    desktopModeEventLogger = mock(DesktopModeEventLogger::class.java)

    transitionObserver =
        DesktopModeLoggerTransitionObserver(
            context, mockShellInit, transitions, desktopModeEventLogger)
    if (Transitions.ENABLE_SHELL_TRANSITIONS) {
      val initRunnableCaptor = ArgumentCaptor.forClass(Runnable::class.java)
      verify(mockShellInit).addInitCallback(initRunnableCaptor.capture(), same(transitionObserver))
      initRunnableCaptor.value.run()
    } else {
      transitionObserver.onInit()
    }
  }

  @Test
  fun testRegistersObserverAtInit() {
    verify(transitions).registerObserver(same(transitionObserver))
  }

  @Test
  fun transitOpen_notFreeformWindow_doesNotLogTaskAddedOrSessionEnter() {
    val change = createChange(TRANSIT_OPEN, createTaskInfo(1, WINDOWING_MODE_FULLSCREEN))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, never()).logSessionEnter(any(), any())
    verify(desktopModeEventLogger, never()).logTaskAdded(any(), any())
  }

  @Test
  fun transitOpen_logTaskAddedAndEnterReasonAppFreeformIntent() {
    val change = createChange(TRANSIT_OPEN, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.APP_FREEFORM_INTENT))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitEndDragToDesktop_logTaskAddedAndEnterReasonAppHandleDrag() {
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    // task change is finalised when drag ends
    val transitionInfo =
        TransitionInfoBuilder(Transitions.TRANSIT_DESKTOP_MODE_END_DRAG_TO_DESKTOP, 0)
            .addChange(change)
            .build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.APP_HANDLE_DRAG))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitEnterDesktopByButtonTap_logTaskAddedAndEnterReasonButtonTap() {
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_HANDLE_MENU_BUTTON, 0)
            .addChange(change)
            .build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.APP_HANDLE_MENU_BUTTON))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitEnterDesktopFromAppFromOverview_logTaskAddedAndEnterReasonAppFromOverview() {
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW, 0)
            .addChange(change)
            .build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.APP_FROM_OVERVIEW))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitEnterDesktopFromKeyboardShortcut_logTaskAddedAndEnterReasonKeyboardShortcut() {
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_KEYBOARD_SHORTCUT, 0)
            .addChange(change)
            .build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.KEYBOARD_SHORTCUT_ENTER))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitToFront_logTaskAddedAndEnterReasonOverview() {
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_TO_FRONT, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.OVERVIEW))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitToFront_previousTransitionExitToOverview_logTaskAddedAndEnterReasonOverview() {
    // previous exit to overview transition
    val previousSessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(previousSessionId)
    val previousChange = createChange(TRANSIT_TO_BACK, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val previousTransitionInfo =
        TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
            .addChange(previousChange)
            .build()

    callOnTransitionReady(previousTransitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(previousSessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(previousSessionId), eq(ExitReason.RETURN_HOME_OR_OVERVIEW))

    // Enter desktop mode from cancelled recents has no transition. Enter is detected on the
    // next transition involving freeform windows

    // TRANSIT_TO_FRONT
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_TO_FRONT, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.OVERVIEW))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitChange_previousTransitionExitToOverview_logTaskAddedAndEnterReasonOverview() {
    // previous exit to overview transition
    val previousSessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(previousSessionId)
    val previousChange = createChange(TRANSIT_TO_BACK, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val previousTransitionInfo =
        TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
            .addChange(previousChange)
            .build()

    callOnTransitionReady(previousTransitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(previousSessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(previousSessionId), eq(ExitReason.RETURN_HOME_OR_OVERVIEW))

    // Enter desktop mode from cancelled recents has no transition. Enter is detected on the
    // next transition involving freeform windows

    // TRANSIT_CHANGE
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_CHANGE, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.OVERVIEW))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitOpen_previousTransitionExitToOverview_logTaskAddedAndEnterReasonOverview() {
    // previous exit to overview transition
    val previousSessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(previousSessionId)
    val previousChange = createChange(TRANSIT_TO_BACK, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val previousTransitionInfo =
        TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
            .addChange(previousChange)
            .build()

    callOnTransitionReady(previousTransitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(previousSessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(previousSessionId), eq(ExitReason.RETURN_HOME_OR_OVERVIEW))

    // Enter desktop mode from cancelled recents has no transition. Enter is detected on the
    // next transition involving freeform windows

    // TRANSIT_OPEN
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.OVERVIEW))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  @Suppress("ktlint:standard:max-line-length")
  fun transitEnterDesktopFromAppFromOverview_previousTransitionExitToOverview_logTaskAddedAndEnterReasonAppFromOverview() {
    // Tests for AppFromOverview precedence in compared to cancelled Overview

    // previous exit to overview transition
    val previousSessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(previousSessionId)
    val previousChange = createChange(TRANSIT_TO_BACK, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val previousTransitionInfo =
      TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS)
              .addChange(previousChange)
              .build()

    callOnTransitionReady(previousTransitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(previousSessionId), any())
    verify(desktopModeEventLogger, times(1))
            .logSessionExit(eq(previousSessionId), eq(ExitReason.RETURN_HOME_OR_OVERVIEW))

    // TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo =
      TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_APP_FROM_OVERVIEW, 0)
              .addChange(change)
              .build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
            .logSessionEnter(eq(sessionId!!), eq(EnterReason.APP_FROM_OVERVIEW))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitEnterDesktopFromUnknown_logTaskAddedAndEnterReasonUnknown() {
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_ENTER_DESKTOP_FROM_UNKNOWN, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.UNKNOWN_ENTER))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitWake_logTaskAddedAndEnterReasonScreenOn() {
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_WAKE, 0).addChange(change).build()

    callOnTransitionReady(transitionInfo)
    val sessionId = transitionObserver.getLoggerSessionId()

    assertThat(sessionId).isNotNull()
    verify(desktopModeEventLogger, times(1))
        .logSessionEnter(eq(sessionId!!), eq(EnterReason.SCREEN_ON))
    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verifyZeroInteractions(desktopModeEventLogger)
  }

  @Test
  fun transitSleep_logTaskAddedAndExitReasonScreenOff_sessionIdNull() {
    val sessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    val transitionInfo = TransitionInfoBuilder(TRANSIT_SLEEP).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.SCREEN_OFF))
    verifyZeroInteractions(desktopModeEventLogger)
    assertThat(transitionObserver.getLoggerSessionId()).isNull()
  }

  @Test
  fun transitExitDesktopTaskDrag_logTaskRemovedAndExitReasonDragToExit_sessionIdNull() {
    val sessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // window mode changing from FREEFORM to FULLSCREEN
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FULLSCREEN))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_TASK_DRAG).addChange(change).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.DRAG_TO_EXIT))
    verifyZeroInteractions(desktopModeEventLogger)
    assertThat(transitionObserver.getLoggerSessionId()).isNull()
  }

  @Test
  fun transitExitDesktopAppHandleButton_logTaskRemovedAndExitReasonButton_sessionIdNull() {
    val sessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // window mode changing from FREEFORM to FULLSCREEN
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FULLSCREEN))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_HANDLE_MENU_BUTTON)
            .addChange(change)
            .build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.APP_HANDLE_MENU_BUTTON_EXIT))
    verifyZeroInteractions(desktopModeEventLogger)
    assertThat(transitionObserver.getLoggerSessionId()).isNull()
  }

  @Test
  fun transitExitDesktopUsingKeyboard_logTaskRemovedAndExitReasonKeyboard_sessionIdNull() {
    val sessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // window mode changing from FREEFORM to FULLSCREEN
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FULLSCREEN))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_KEYBOARD_SHORTCUT).addChange(change).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.KEYBOARD_SHORTCUT_EXIT))
    verifyZeroInteractions(desktopModeEventLogger)
    assertThat(transitionObserver.getLoggerSessionId()).isNull()
  }

  @Test
  fun transitExitDesktopUnknown_logTaskRemovedAndExitReasonUnknown_sessionIdNull() {
    val sessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // window mode changing from FREEFORM to FULLSCREEN
    val change = createChange(TRANSIT_TO_FRONT, createTaskInfo(1, WINDOWING_MODE_FULLSCREEN))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_EXIT_DESKTOP_MODE_UNKNOWN).addChange(change).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.UNKNOWN_EXIT))
    verifyZeroInteractions(desktopModeEventLogger)
    assertThat(transitionObserver.getLoggerSessionId()).isNull()
  }

  @Test
  fun transitToFrontWithFlagRecents_logTaskRemovedAndExitReasonOverview_sessionIdNull() {
    val sessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // recents transition
    val change = createChange(TRANSIT_TO_BACK, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo =
        TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS).addChange(change).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.RETURN_HOME_OR_OVERVIEW))
    verifyZeroInteractions(desktopModeEventLogger)
    assertThat(transitionObserver.getLoggerSessionId()).isNull()
  }

  @Test
  fun transitClose_logTaskRemovedAndExitReasonTaskFinished_sessionIdNull() {
    val sessionId = 1
    // add a freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // task closing
    val change = createChange(TRANSIT_CLOSE, createTaskInfo(1, WINDOWING_MODE_FULLSCREEN))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_CLOSE).addChange(change).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.TASK_FINISHED))
    verifyZeroInteractions(desktopModeEventLogger)
    assertThat(transitionObserver.getLoggerSessionId()).isNull()
  }

  @Test
  fun sessionExitByRecents_cancelledAnimation_sessionRestored() {
    val sessionId = 1
    // add a freeform task to an existing session
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // recents transition sent freeform window to back
    val change = createChange(TRANSIT_TO_BACK, createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    val transitionInfo1 =
        TransitionInfoBuilder(TRANSIT_TO_FRONT, TRANSIT_FLAG_IS_RECENTS).addChange(change).build()
    callOnTransitionReady(transitionInfo1)
    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, times(1))
        .logSessionExit(eq(sessionId), eq(ExitReason.RETURN_HOME_OR_OVERVIEW))
    assertThat(transitionObserver.getLoggerSessionId()).isNull()

    val transitionInfo2 = TransitionInfoBuilder(TRANSIT_NONE).build()
    callOnTransitionReady(transitionInfo2)

    verify(desktopModeEventLogger, times(1)).logSessionEnter(any(), any())
    verify(desktopModeEventLogger, times(1)).logTaskAdded(any(), any())
  }

  @Test
  fun sessionAlreadyStarted_newFreeformTaskAdded_logsTaskAdded() {
    val sessionId = 1
    // add an existing freeform task
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // new freeform task added
    val change = createChange(TRANSIT_OPEN, createTaskInfo(2, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskAdded(eq(sessionId), any())
    verify(desktopModeEventLogger, never()).logSessionEnter(any(), any())
  }

  @Test
  fun sessionAlreadyStarted_freeformTaskRemoved_logsTaskRemoved() {
    val sessionId = 1
    // add two existing freeform tasks
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(1, WINDOWING_MODE_FREEFORM))
    transitionObserver.addTaskInfosToCachedMap(createTaskInfo(2, WINDOWING_MODE_FREEFORM))
    transitionObserver.setLoggerSessionId(sessionId)

    // new freeform task added
    val change = createChange(TRANSIT_CLOSE, createTaskInfo(2, WINDOWING_MODE_FREEFORM))
    val transitionInfo = TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change).build()
    callOnTransitionReady(transitionInfo)

    verify(desktopModeEventLogger, times(1)).logTaskRemoved(eq(sessionId), any())
    verify(desktopModeEventLogger, never()).logSessionExit(any(), any())
  }

  /** Simulate calling the onTransitionReady() method */
  private fun callOnTransitionReady(transitionInfo: TransitionInfo) {
    val transition = mock(IBinder::class.java)
    val startT = mock(SurfaceControl.Transaction::class.java)
    val finishT = mock(SurfaceControl.Transaction::class.java)

    transitionObserver.onTransitionReady(transition, transitionInfo, startT, finishT)
  }

  companion object {
    fun createTaskInfo(taskId: Int, windowMode: Int): ActivityManager.RunningTaskInfo {
      val taskInfo = ActivityManager.RunningTaskInfo()
      taskInfo.taskId = taskId
      taskInfo.configuration.windowConfiguration.windowingMode = windowMode

      return taskInfo
    }

    fun createChange(mode: Int, taskInfo: ActivityManager.RunningTaskInfo): Change {
      val change =
          Change(
              WindowContainerToken(mock(IWindowContainerToken::class.java)),
              mock(SurfaceControl::class.java))
      change.mode = mode
      change.taskInfo = taskInfo
      return change
    }
  }
}
