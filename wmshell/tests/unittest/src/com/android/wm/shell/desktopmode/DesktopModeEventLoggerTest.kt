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
import android.graphics.Rect
import android.platform.test.annotations.EnableFlags
import com.android.dx.mockito.inline.extended.ExtendedMockito.clearInvocations
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.staticMockMarker
import com.android.dx.mockito.inline.extended.ExtendedMockito.verify
import com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions
import com.android.internal.util.FrameworkStatsLog
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.window.flags2.Flags
import com.android.wm.shell.EventLogTags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.DisplayLayout
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.FocusReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.InputMethod
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.MinimizeReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.NO_SESSION_ID
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ResizeTrigger
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.TaskSizeUpdate
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.TaskUpdate
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UNSET_FOCUS_REASON
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UNSET_MINIMIZE_REASON
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UNSET_UNMINIMIZE_REASON
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.UnminimizeReason
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

/** Tests for [DesktopModeEventLogger]. */
class DesktopModeEventLoggerTest : ShellTestCase() {

    private val desktopModeEventLogger = DesktopModeEventLogger()
    val displayController = mock<DisplayController>()
    val displayLayout = mock<DisplayLayout>()

    @JvmField
    @Rule()
    val extendedMockitoRule =
        ExtendedMockitoRule.Builder(this)
            .mockStatic(FrameworkStatsLog::class.java)
            .mockStatic(EventLogTags::class.java)
            .build()!!

    @Before
    fun setUp() {
        doReturn(displayLayout).whenever(displayController).getDisplayLayout(anyInt())
        doReturn(DISPLAY_WIDTH).whenever(displayLayout).width()
        doReturn(DISPLAY_HEIGHT).whenever(displayLayout).height()
    }

    @After
    fun tearDown() {
        clearInvocations(staticMockMarker(FrameworkStatsLog::class.java))
        clearInvocations(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logSessionEnter_logsEnterReasonWithNewSessionId() {
        desktopModeEventLogger.logSessionEnter(EnterReason.KEYBOARD_SHORTCUT_ENTER)

        val sessionId = desktopModeEventLogger.currentSessionId.get()
        assertThat(sessionId).isNotEqualTo(NO_SESSION_ID)
        verifyOnlyOneUiChangedLogging(
            FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__ENTER,
            FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__KEYBOARD_SHORTCUT_ENTER,
            0,
            sessionId,
        )
        verify {
            EventLogTags.writeWmShellEnterDesktopMode(
                eq(EnterReason.KEYBOARD_SHORTCUT_ENTER.reason),
                eq(sessionId),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logSessionEnter_ongoingSession_logsEnterReasonWithNewSessionId() {
        val previousSessionId = startDesktopModeSession()

        desktopModeEventLogger.logSessionEnter(EnterReason.KEYBOARD_SHORTCUT_ENTER)

        val sessionId = desktopModeEventLogger.currentSessionId.get()
        assertThat(sessionId).isNotEqualTo(NO_SESSION_ID)
        assertThat(sessionId).isNotEqualTo(previousSessionId)
        verifyOnlyOneUiChangedLogging(
            FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__ENTER,
            FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__KEYBOARD_SHORTCUT_ENTER,
            /* exit_reason */
            0,
            sessionId,
        )
        verify {
            EventLogTags.writeWmShellEnterDesktopMode(
                eq(EnterReason.KEYBOARD_SHORTCUT_ENTER.reason),
                eq(sessionId),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logSessionExit_noOngoingSession_doesNotLog() {
        desktopModeEventLogger.logSessionExit(ExitReason.DRAG_TO_EXIT)

        verifyNoLogging()
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logSessionExit_logsExitReasonAndClearsSessionId() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logSessionExit(ExitReason.DRAG_TO_EXIT)

        verifyOnlyOneUiChangedLogging(
            FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__EXIT,
            /* enter_reason */
            0,
            FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__DRAG_TO_EXIT,
            sessionId,
        )
        verify {
            EventLogTags.writeWmShellExitDesktopMode(
                eq(ExitReason.DRAG_TO_EXIT.reason),
                eq(sessionId),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
        assertThat(desktopModeEventLogger.currentSessionId.get()).isEqualTo(NO_SESSION_ID)
    }

    @Test
    fun logTaskAdded_noOngoingSession_doesNotLog() {
        desktopModeEventLogger.logTaskAdded(TASK_UPDATE)

        verifyNoLogging()
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskAdded_logsTaskUpdate() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskAdded(TASK_UPDATE)

        verifyOnlyOneTaskUpdateLogging(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_ADDED,
            TASK_UPDATE.instanceId,
            TASK_UPDATE.uid,
            TASK_UPDATE.taskHeight,
            TASK_UPDATE.taskWidth,
            TASK_UPDATE.taskX,
            TASK_UPDATE.taskY,
            sessionId,
            UNSET_MINIMIZE_REASON,
            UNSET_UNMINIMIZE_REASON,
            TASK_COUNT,
            UNSET_FOCUS_REASON,
        )
        verify {
            EventLogTags.writeWmShellDesktopModeTaskUpdate(
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_ADDED),
                eq(TASK_UPDATE.instanceId),
                eq(TASK_UPDATE.uid),
                eq(TASK_UPDATE.taskHeight),
                eq(TASK_UPDATE.taskWidth),
                eq(TASK_UPDATE.taskX),
                eq(TASK_UPDATE.taskY),
                eq(sessionId),
                eq(UNSET_MINIMIZE_REASON),
                eq(UNSET_UNMINIMIZE_REASON),
                eq(TASK_COUNT),
                eq(UNSET_FOCUS_REASON),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskRemoved_noOngoingSession_doesNotLog() {
        desktopModeEventLogger.logTaskRemoved(TASK_UPDATE)

        verifyNoLogging()
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskRemoved_taskUpdate() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskRemoved(TASK_UPDATE)

        verifyOnlyOneTaskUpdateLogging(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_REMOVED,
            TASK_UPDATE.instanceId,
            TASK_UPDATE.uid,
            TASK_UPDATE.taskHeight,
            TASK_UPDATE.taskWidth,
            TASK_UPDATE.taskX,
            TASK_UPDATE.taskY,
            sessionId,
            UNSET_MINIMIZE_REASON,
            UNSET_UNMINIMIZE_REASON,
            TASK_COUNT,
            UNSET_FOCUS_REASON,
        )
        verify {
            EventLogTags.writeWmShellDesktopModeTaskUpdate(
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_REMOVED),
                eq(TASK_UPDATE.instanceId),
                eq(TASK_UPDATE.uid),
                eq(TASK_UPDATE.taskHeight),
                eq(TASK_UPDATE.taskWidth),
                eq(TASK_UPDATE.taskX),
                eq(TASK_UPDATE.taskY),
                eq(sessionId),
                eq(UNSET_MINIMIZE_REASON),
                eq(UNSET_UNMINIMIZE_REASON),
                eq(TASK_COUNT),
                eq(UNSET_FOCUS_REASON),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskInfoChanged_noOngoingSession_doesNotLog() {
        desktopModeEventLogger.logTaskInfoChanged(TASK_UPDATE)

        verifyNoLogging()
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskInfoChanged_taskUpdate() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskInfoChanged(TASK_UPDATE)

        verifyOnlyOneTaskUpdateLogging(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED,
            TASK_UPDATE.instanceId,
            TASK_UPDATE.uid,
            TASK_UPDATE.taskHeight,
            TASK_UPDATE.taskWidth,
            TASK_UPDATE.taskX,
            TASK_UPDATE.taskY,
            sessionId,
            UNSET_MINIMIZE_REASON,
            UNSET_UNMINIMIZE_REASON,
            TASK_COUNT,
            UNSET_FOCUS_REASON,
        )
        verify {
            EventLogTags.writeWmShellDesktopModeTaskUpdate(
                eq(
                    FrameworkStatsLog
                        .DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED
                ),
                eq(TASK_UPDATE.instanceId),
                eq(TASK_UPDATE.uid),
                eq(TASK_UPDATE.taskHeight),
                eq(TASK_UPDATE.taskWidth),
                eq(TASK_UPDATE.taskX),
                eq(TASK_UPDATE.taskY),
                eq(sessionId),
                eq(UNSET_MINIMIZE_REASON),
                eq(UNSET_UNMINIMIZE_REASON),
                eq(TASK_COUNT),
                eq(UNSET_FOCUS_REASON),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskInfoChanged_logsTaskUpdateWithMinimizeReason() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskInfoChanged(
            createTaskUpdate(minimizeReason = MinimizeReason.TASK_LIMIT)
        )

        verifyOnlyOneTaskUpdateLogging(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED,
            TASK_UPDATE.instanceId,
            TASK_UPDATE.uid,
            TASK_UPDATE.taskHeight,
            TASK_UPDATE.taskWidth,
            TASK_UPDATE.taskX,
            TASK_UPDATE.taskY,
            sessionId,
            MinimizeReason.TASK_LIMIT.reason,
            UNSET_UNMINIMIZE_REASON,
            TASK_COUNT,
            UNSET_FOCUS_REASON,
        )
        verify {
            EventLogTags.writeWmShellDesktopModeTaskUpdate(
                eq(
                    FrameworkStatsLog
                        .DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED
                ),
                eq(TASK_UPDATE.instanceId),
                eq(TASK_UPDATE.uid),
                eq(TASK_UPDATE.taskHeight),
                eq(TASK_UPDATE.taskWidth),
                eq(TASK_UPDATE.taskX),
                eq(TASK_UPDATE.taskY),
                eq(sessionId),
                eq(MinimizeReason.TASK_LIMIT.reason),
                eq(UNSET_UNMINIMIZE_REASON),
                eq(TASK_COUNT),
                eq(UNSET_FOCUS_REASON),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskInfoChanged_logsTaskUpdateWithUnminimizeReason() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskInfoChanged(
            createTaskUpdate(unminimizeReason = UnminimizeReason.TASKBAR_TAP)
        )

        verifyOnlyOneTaskUpdateLogging(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED,
            TASK_UPDATE.instanceId,
            TASK_UPDATE.uid,
            TASK_UPDATE.taskHeight,
            TASK_UPDATE.taskWidth,
            TASK_UPDATE.taskX,
            TASK_UPDATE.taskY,
            sessionId,
            UNSET_MINIMIZE_REASON,
            UnminimizeReason.TASKBAR_TAP.reason,
            TASK_COUNT,
            UNSET_FOCUS_REASON,
        )
        verify {
            EventLogTags.writeWmShellDesktopModeTaskUpdate(
                eq(
                    FrameworkStatsLog
                        .DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED
                ),
                eq(TASK_UPDATE.instanceId),
                eq(TASK_UPDATE.uid),
                eq(TASK_UPDATE.taskHeight),
                eq(TASK_UPDATE.taskWidth),
                eq(TASK_UPDATE.taskX),
                eq(TASK_UPDATE.taskY),
                eq(sessionId),
                eq(UNSET_MINIMIZE_REASON),
                eq(UnminimizeReason.TASKBAR_TAP.reason),
                eq(TASK_COUNT),
                eq(UNSET_FOCUS_REASON),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskInfoChanged_logsTaskUpdateWithFocusReason() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskInfoChanged(
            createTaskUpdate(focusChangesReason = FocusReason.UNKNOWN)
        )

        verifyOnlyOneTaskUpdateLogging(
            FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED,
            TASK_UPDATE.instanceId,
            TASK_UPDATE.uid,
            TASK_UPDATE.taskHeight,
            TASK_UPDATE.taskWidth,
            TASK_UPDATE.taskX,
            TASK_UPDATE.taskY,
            sessionId,
            UNSET_MINIMIZE_REASON,
            UNSET_UNMINIMIZE_REASON,
            TASK_COUNT,
            FocusReason.UNKNOWN.reason,
        )
        verify {
            EventLogTags.writeWmShellDesktopModeTaskUpdate(
                eq(
                    FrameworkStatsLog
                        .DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED
                ),
                eq(TASK_UPDATE.instanceId),
                eq(TASK_UPDATE.uid),
                eq(TASK_UPDATE.taskHeight),
                eq(TASK_UPDATE.taskWidth),
                eq(TASK_UPDATE.taskX),
                eq(TASK_UPDATE.taskY),
                eq(sessionId),
                eq(UNSET_MINIMIZE_REASON),
                eq(UNSET_UNMINIMIZE_REASON),
                eq(TASK_COUNT),
                eq(FocusReason.UNKNOWN.reason),
            )
        }
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    fun logTaskResizingStarted_noOngoingSession_doesNotLog() {
        desktopModeEventLogger.logTaskResizingStarted(
            ResizeTrigger.CORNER,
            InputMethod.UNKNOWN_INPUT_METHOD,
            createTaskInfo(),
        )

        verifyNoLogging()
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_RESIZING_METRICS)
    fun logTaskResizingStarted_logsTaskSizeUpdatedWithStartResizingStage() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskResizingStarted(
            ResizeTrigger.CORNER,
            InputMethod.UNKNOWN_INPUT_METHOD,
            createTaskInfo(),
            TASK_SIZE_UPDATE.taskWidth,
            TASK_SIZE_UPDATE.taskHeight,
            displayController,
        )

        verifyOnlyOneTaskSizeUpdatedLogging(
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__CORNER_RESIZE_TRIGGER,
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZING_STAGE__START_RESIZING_STAGE,
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__UNKNOWN_INPUT_METHOD,
            sessionId,
            TASK_SIZE_UPDATE.instanceId,
            TASK_SIZE_UPDATE.uid,
            TASK_SIZE_UPDATE.taskWidth,
            TASK_SIZE_UPDATE.taskHeight,
            DISPLAY_AREA,
        )
    }

    @Test
    fun logTaskResizingEnded_noOngoingSession_doesNotLog() {
        desktopModeEventLogger.logTaskResizingEnded(
            ResizeTrigger.CORNER,
            InputMethod.UNKNOWN_INPUT_METHOD,
            createTaskInfo(),
        )

        verifyNoLogging()
        verifyNoMoreInteractions(staticMockMarker(EventLogTags::class.java))
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_RESIZING_METRICS)
    fun logTaskResizingEnded_logsTaskSizeUpdatedWithEndResizingStage() {
        val sessionId = startDesktopModeSession()

        desktopModeEventLogger.logTaskResizingEnded(
            ResizeTrigger.CORNER,
            InputMethod.UNKNOWN_INPUT_METHOD,
            createTaskInfo(),
            displayController = displayController,
        )

        verifyOnlyOneTaskSizeUpdatedLogging(
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZE_TRIGGER__CORNER_RESIZE_TRIGGER,
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__RESIZING_STAGE__END_RESIZING_STAGE,
            FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED__INPUT_METHOD__UNKNOWN_INPUT_METHOD,
            sessionId,
            TASK_SIZE_UPDATE.instanceId,
            TASK_SIZE_UPDATE.uid,
            TASK_SIZE_UPDATE.taskWidth,
            TASK_SIZE_UPDATE.taskHeight,
            DISPLAY_AREA,
        )
    }

    private fun startDesktopModeSession(): Int {
        desktopModeEventLogger.logSessionEnter(EnterReason.KEYBOARD_SHORTCUT_ENTER)
        clearInvocations(staticMockMarker(FrameworkStatsLog::class.java))
        clearInvocations(staticMockMarker(EventLogTags::class.java))
        return desktopModeEventLogger.currentSessionId.get()
    }

    @Test
    fun logTaskInfoStateInit_logsTaskInfoChangedStateInit() {
        desktopModeEventLogger.logTaskInfoStateInit()
        verify {
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE),
                /* task_event */
                eq(
                    FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INIT_STATSD
                ),
                /* instance_id */
                eq(0),
                /* uid */
                eq(0),
                /* task_height */
                eq(0),
                /* task_width */
                eq(0),
                /* task_x */
                eq(0),
                /* task_y */
                eq(0),
                /* session_id */
                eq(0),
                /* minimize_reason */
                eq(UNSET_MINIMIZE_REASON),
                /* unminimize_reason */
                eq(UNSET_UNMINIMIZE_REASON),
                /* visible_task_count */
                eq(0),
                /* focus_reason */
                eq(UNSET_FOCUS_REASON),
            )
        }
    }

    private fun createTaskInfo(): RunningTaskInfo =
        TestRunningTaskInfoBuilder()
            .setTaskId(TASK_ID)
            .setUid(TASK_UID)
            .setBounds(Rect(TASK_X, TASK_Y, TASK_WIDTH, TASK_HEIGHT))
            .build()

    private fun verifyNoLogging() {
        verify(
            {
                FrameworkStatsLog.write(
                    eq(FrameworkStatsLog.DESKTOP_MODE_UI_CHANGED),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                )
            },
            never(),
        )
        verify(
            {
                FrameworkStatsLog.write(
                    eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                )
            },
            never(),
        )
        verify(
            {
                FrameworkStatsLog.write(
                    eq(FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                    anyInt(),
                )
            },
            never(),
        )
    }

    private fun verifyOnlyOneUiChangedLogging(
        event: Int,
        enterReason: Int,
        exitReason: Int,
        sessionId: Int,
    ) {
        verify({
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_UI_CHANGED),
                eq(event),
                eq(enterReason),
                eq(exitReason),
                eq(sessionId),
            )
        })
        verify({
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_UI_CHANGED),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
            )
        })
    }

    private fun verifyOnlyOneTaskUpdateLogging(
        taskEvent: Int,
        instanceId: Int,
        uid: Int,
        taskHeight: Int,
        taskWidth: Int,
        taskX: Int,
        taskY: Int,
        sessionId: Int,
        minimizeReason: Int,
        unminimizeReason: Int,
        visibleTaskCount: Int,
        focusChangedReason: Int,
    ) {
        verify({
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE),
                eq(taskEvent),
                eq(instanceId),
                eq(uid),
                eq(taskHeight),
                eq(taskWidth),
                eq(taskX),
                eq(taskY),
                eq(sessionId),
                eq(minimizeReason),
                eq(unminimizeReason),
                eq(visibleTaskCount),
                eq(focusChangedReason),
            )
        })
        verify({
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
            )
        })
    }

    private fun verifyOnlyOneTaskSizeUpdatedLogging(
        resizeTrigger: Int,
        resizingStage: Int,
        inputMethod: Int,
        sessionId: Int,
        instanceId: Int,
        uid: Int,
        taskWidth: Int,
        taskHeight: Int,
        displayArea: Int,
    ) {
        verify({
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED),
                eq(resizeTrigger),
                eq(resizingStage),
                eq(inputMethod),
                eq(sessionId),
                eq(instanceId),
                eq(uid),
                eq(taskWidth),
                eq(taskHeight),
                eq(displayArea),
            )
        })
        verify({
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_TASK_SIZE_UPDATED),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
                anyInt(),
            )
        })
    }

    private companion object {
        private const val TASK_ID = 1
        private const val TASK_UID = 1
        private const val TASK_X = 0
        private const val TASK_Y = 0
        private const val TASK_HEIGHT = 100
        private const val TASK_WIDTH = 100
        private const val TASK_COUNT = 1
        private const val DISPLAY_WIDTH = 500
        private const val DISPLAY_HEIGHT = 500
        private const val DISPLAY_AREA = DISPLAY_HEIGHT * DISPLAY_WIDTH

        private val TASK_UPDATE =
            TaskUpdate(
                TASK_ID,
                TASK_UID,
                TASK_HEIGHT,
                TASK_WIDTH,
                TASK_X,
                TASK_Y,
                visibleTaskCount = TASK_COUNT,
            )

        private val TASK_SIZE_UPDATE =
            TaskSizeUpdate(
                resizeTrigger = ResizeTrigger.UNKNOWN_RESIZE_TRIGGER,
                inputMethod = InputMethod.UNKNOWN_INPUT_METHOD,
                TASK_ID,
                TASK_UID,
                TASK_HEIGHT,
                TASK_WIDTH,
                DISPLAY_AREA,
            )

        private fun createTaskUpdate(
            minimizeReason: MinimizeReason? = null,
            unminimizeReason: UnminimizeReason? = null,
            focusChangesReason: FocusReason? = null,
        ) =
            TaskUpdate(
                TASK_ID,
                TASK_UID,
                TASK_HEIGHT,
                TASK_WIDTH,
                TASK_X,
                TASK_Y,
                minimizeReason,
                unminimizeReason,
                TASK_COUNT,
                focusChangesReason,
            )
    }
}
