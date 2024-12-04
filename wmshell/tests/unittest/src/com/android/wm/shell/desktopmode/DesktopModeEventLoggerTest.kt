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

import com.android.dx.mockito.inline.extended.ExtendedMockito
import com.android.internal.util.FrameworkStatsLog
import com.android.modules.utils.testing.ExtendedMockitoRule
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.EnterReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.ExitReason
import com.android.wm.shell.desktopmode.DesktopModeEventLogger.Companion.TaskUpdate
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.eq

/**
 * Tests for [DesktopModeEventLogger].
 */
class DesktopModeEventLoggerTest {

    private val desktopModeEventLogger  = DesktopModeEventLogger()

    @JvmField
    @Rule
    val extendedMockitoRule = ExtendedMockitoRule.Builder(this)
            .mockStatic(FrameworkStatsLog::class.java).build()!!

    @Test
    fun logSessionEnter_enterReason() = runBlocking {
        desktopModeEventLogger.logSessionEnter(sessionId = SESSION_ID, EnterReason.UNKNOWN_ENTER)

        ExtendedMockito.verify {
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_UI_CHANGED),
                /* event */
                eq(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__ENTER),
                /* enter_reason */
                eq(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__ENTER_REASON__UNKNOWN_ENTER),
                /* exit_reason */
                eq(0),
                /* sessionId */
                eq(SESSION_ID)
            )
        }
    }

    @Test
    fun logSessionExit_exitReason() = runBlocking {
        desktopModeEventLogger.logSessionExit(sessionId = SESSION_ID, ExitReason.UNKNOWN_EXIT)

        ExtendedMockito.verify {
            FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DESKTOP_MODE_UI_CHANGED),
                /* event */
                eq(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EVENT__EXIT),
                /* enter_reason */
                eq(0),
                /* exit_reason */
                eq(FrameworkStatsLog.DESKTOP_MODE_UICHANGED__EXIT_REASON__UNKNOWN_EXIT),
                /* sessionId */
                eq(SESSION_ID)
            )
        }
    }

    @Test
    fun logTaskAdded_taskUpdate() = runBlocking {
        desktopModeEventLogger.logTaskAdded(sessionId = SESSION_ID, TASK_UPDATE)

        ExtendedMockito.verify {
            FrameworkStatsLog.write(eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE),
                /* task_event */
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_ADDED),
                /* instance_id */
                eq(TASK_UPDATE.instanceId),
                /* uid */
                eq(TASK_UPDATE.uid),
                /* task_height */
                eq(TASK_UPDATE.taskHeight),
                /* task_width */
                eq(TASK_UPDATE.taskWidth),
                /* task_x */
                eq(TASK_UPDATE.taskX),
                /* task_y */
                eq(TASK_UPDATE.taskY),
                /* session_id */
                eq(SESSION_ID))
        }
    }

    @Test
    fun logTaskRemoved_taskUpdate() = runBlocking {
        desktopModeEventLogger.logTaskRemoved(sessionId = SESSION_ID, TASK_UPDATE)

        ExtendedMockito.verify {
            FrameworkStatsLog.write(eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE),
                /* task_event */
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_REMOVED),
                /* instance_id */
                eq(TASK_UPDATE.instanceId),
                /* uid */
                eq(TASK_UPDATE.uid),
                /* task_height */
                eq(TASK_UPDATE.taskHeight),
                /* task_width */
                eq(TASK_UPDATE.taskWidth),
                /* task_x */
                eq(TASK_UPDATE.taskX),
                /* task_y */
                eq(TASK_UPDATE.taskY),
                /* session_id */
                eq(SESSION_ID))
        }
    }

    @Test
    fun logTaskInfoChanged_taskUpdate() = runBlocking {
        desktopModeEventLogger.logTaskInfoChanged(sessionId = SESSION_ID, TASK_UPDATE)

        ExtendedMockito.verify {
            FrameworkStatsLog.write(eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE),
                /* task_event */
                eq(FrameworkStatsLog.DESKTOP_MODE_SESSION_TASK_UPDATE__TASK_EVENT__TASK_INFO_CHANGED),
                /* instance_id */
                eq(TASK_UPDATE.instanceId),
                /* uid */
                eq(TASK_UPDATE.uid),
                /* task_height */
                eq(TASK_UPDATE.taskHeight),
                /* task_width */
                eq(TASK_UPDATE.taskWidth),
                /* task_x */
                eq(TASK_UPDATE.taskX),
                /* task_y */
                eq(TASK_UPDATE.taskY),
                /* session_id */
                eq(SESSION_ID))
        }
    }

    companion object {
        private const val SESSION_ID = 1
        private const val TASK_ID = 1
        private const val TASK_UID = 1
        private const val TASK_X = 0
        private const val TASK_Y = 0
        private const val TASK_HEIGHT = 100
        private const val TASK_WIDTH = 100

        private val TASK_UPDATE = TaskUpdate(
            TASK_ID, TASK_UID, TASK_HEIGHT, TASK_WIDTH, TASK_X, TASK_Y
        )
    }
}